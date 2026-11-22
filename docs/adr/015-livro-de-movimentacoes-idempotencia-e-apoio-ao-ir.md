# ADR 015 — Livro de movimentações, idempotência e apoio ao imposto de renda

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #38 (item #13 do roadmap)

## Contexto

A carteira do item #12 guarda só o estado atual de cada posição. Sem o histórico não há câmbio de cada compra (dívida 35), resultado
realizado de cada venda, nem como reproduzir uma posição. Além disso, uma requisição repetida por falha de rede (o cliente não viu a
resposta e tentou de novo) aplicaria a compra duas vezes. E o imposto de renda é onde um número "quase certo" faz mais dano: citar
uma regra que não foi lida na fonte, ou calcular um imposto sem a regra de compensação, dá uma precisão que não existe.

## Decisão

1. **Livro append-only, posição como dobra.** `ledger_entries` guarda abertura, compra, venda, ajuste manual e baixa. Cada entrada traz o
   que o usuário informou (quantidade, preço, taxas, data, câmbio) e o estado da posição logo depois. Um gatilho do PostgreSQL
   bloqueia `UPDATE`; a exclusão só ocorre em cascata ao apagar o usuário. A posição muda **na mesma transação** que grava a entrada
   (`PortfolioLedger.commit`), então uma falha em qualquer das duas desfaz as duas (testado no banco). O livro é o **único caminho de
   escrita** da carteira.
2. **Cronologia por ativo.** A data da operação não pode estar no futuro nem ser anterior à última operação do mesmo ativo. Isso mantém a
   dobra determinística (o preço médio depende da ordem) sem recalcular o histórico a cada lançamento retroativo; importe do mais
   antigo para o mais novo.
3. **Taxas e câmbio.** As taxas entram no custo da compra e abatem o valor da venda. Em operação em dólar, o câmbio é o informado pelo
   usuário (nota de corretagem) ou o **PTAX (venda) do último dia útil até a data**, do Banco Central; sem nenhum dos dois a operação é
   recusada (503 `FX_UNAVAILABLE`), nunca estimada. A posição passa a guardar o **custo médio em reais ao câmbio de cada compra**
   (`average_cost_brl`), que fecha a dívida 35: o resultado não realizado em reais agora existe e inclui a variação cambial. Posições
   em dólar herdadas do item #12 não têm câmbio de compra e ficam com o custo em reais "não informado" até um ajuste com câmbio (a migração
   marca `fx_source = UNKNOWN`).
4. **`Idempotency-Key` sem tabela extra.** A chave e o hash do conteúdo ficam na própria entrada do livro, com índice único por
   `(user_id, idempotency_key)`. Repetir a mesma requisição devolve a mesma entrada (200 com `Idempotent-Replay: true`) sem aplicar de
   novo e sem publicar evento; a mesma chave com outro conteúdo é 422 `IDEMPOTENCY_KEY_REUSED`; duas requisições simultâneas gravam
   uma só vez (a perdedora acha a entrada da vencedora). O hash normaliza os números (1,0 = 1,000) e confere a ordem de grandeza **antes**
   de `toPlainString`: `1E999999999` gerava uma string de um gigabyte e derrubava o servidor (achado pelo teste de integração).
5. **Eventos de domínio.** `PositionOpened`, `PurchaseRecorded`, `SaleRecorded`, `PositionAdjusted` e `PositionClosed` são publicados
   **depois do commit** e nunca para uma repetição idempotente, por um barramento em processo em que cada assinante é isolado (a falha de
   um não derruba a operação nem os outros). O assinante real é um contador Micrometer por tipo, sem identificador de usuário nem valor
   em rótulo. O barramento é a costura onde um outbox ou um broker entram depois.
6. **Apoio ao IR limitado ao que a fonte oficial confirma.** O relatório (`GET /api/me/tax/report`) entrega o que o investidor precisa para
   preencher a declaração (posição e custo em reais em 31/12) e o resultado realizado por mês e classe, com as vendas de ações no mês contra
   o limite de R$ 20.000,00. A base legal foi **lida no texto do Planalto em 2026-11-21**: isenção do ganho líquido em vendas de ações até
   R$ 20.000,00 por mês (Lei 11.033/2004, art. 3º, I), alíquotas de 20% (day trade) e 15% (demais) (art. 2º, I e II) e isenção dos
   rendimentos de FII com condições (art. 3º, III e § 1º). As páginas ainda anotam a Medida Provisória nº 1.303/2025 com vigência encerrada,
   então o relatório manda conferir o texto compilado. **Não se calcula imposto devido, DARF nem compensação de prejuízos**, porque a base
   legal da compensação, do ganho de capital em FII, ETF, criptoativos e ativos no exterior **não foi confirmada**; cada ponto fica listado
   como "não apurado". Venda de um ativo comprado no mesmo dia é só sinalizada como possível day trade.
7. **Classes de apuração.** Ações da B3 (BRL) são "ACOES" e as únicas ao limite de R$ 20.000,00; qualquer ativo em dólar é "EXTERIOR"
   (outro regime, não apurado); cripto, FII, ETF e demais ficam separados.

## Consequências

- **Positivas:** a posição é reproduzível do livro; retry de rede não duplica operação; o resultado realizado e o custo em reais têm o câmbio
  certo; o relatório de IR não afirma nada que a fonte oficial não sustente, e diz o que falta.
- **Negativas:**
  - Sem imposto devido nem compensação de prejuízos, o relatório não fecha a declaração sozinho (o item #25, sob pedido, depende de
    confirmar a base legal restante).
  - O livro é cronológico por ativo: corrigir uma operação antiga exige ajuste manual, não edição.
  - Os eventos são em processo e voláteis: um reinício não reenvia eventos pendentes.
  - O PTAX só cobre dólar; outras moedas continuam fora da carteira.

## Alternativas rejeitadas

- **Tabela de idempotência separada:** exigiria um estado "em andamento" e uma limpeza; a entrada do livro já é o registro durável do que foi
  aplicado e a restrição única decide a corrida.
- **Recalcular o histórico a cada lançamento retroativo:** mudaria o preço médio e o resultado de operações já informadas ao usuário.
- **Calcular imposto devido com a regra "como se lembra":** daria um número aparentemente preciso sobre uma regra não confirmada.
- **Editar entradas do livro:** destruiria a auditoria; correções são novas entradas (ajuste ou baixa).
