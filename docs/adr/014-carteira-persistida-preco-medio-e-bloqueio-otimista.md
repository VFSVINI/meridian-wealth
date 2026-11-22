# ADR 014 — Carteira persistida, preço médio ponderado e bloqueio otimista

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #36 (item #12 do roadmap)

## Contexto

Com a autenticação do item #11, o usuário passa a ter dados próprios. Contas e sessões só existiam em memória (dívida 33), o perfil
de investidor se perdia ao recarregar e a carteira era digitada à mão a cada uso. Os riscos deste item: perder uma alteração em
silêncio quando duas operações concorrem (atualização perdida), errar o custo médio por arredondamento ou por ponto flutuante,
misturar moedas e variação cambial num resultado enganoso, e deixar a posição de um usuário alcançável por outro.

## Decisão

1. **Persistência por portas, com dois adaptadores equivalentes.** `UserStore`, `RefreshTokenStore` e `PositionStore` têm uma versão
   em memória (execução sem Docker, como antes) e uma JDBC, escolhidas por `MW_PERSISTENCE`. Um **contrato de testes** por porta roda
   contra as duas versões, a JDBC sobre um PostgreSQL de verdade (binário embutido, sem Docker, `io.zonky.test:embedded-postgres`),
   para o comportamento ser o mesmo. A migração `V2__users_and_portfolio.sql` cria `users`, `refresh_tokens` e `positions`, com
   restrições (`CHECK`, únicos, chaves estrangeiras com `ON DELETE CASCADE`) que o banco impõe mesmo que a aplicação erre.
2. **Dinheiro e quantidade em `NUMERIC`/`BigDecimal`.** Quantidade com até 8 casas, preço com até 6, 15 dígitos inteiros, validado por
   `MoneyBounds` antes de qualquer conta (herda a defesa contra notação científica do item #8). O `double` só aparece na fronteira
   com o motor de mercado (o preço do provedor entra por `BigDecimal.valueOf`).
3. **Preço médio ponderado pelo custo.** Compra: `(q · pm + q' · p) / (q + q')`, arredondado a 6 casas em HALF_EVEN. Venda: reduz a
   quantidade e **mantém** o preço médio; vender mais do que se tem é recusado; zerar fecha a posição. Corretagem e taxas não são
   estimadas: entram no custo só se o usuário as incluir no preço informado. Resultado realizado e imposto são do livro de
   movimentações (item #13).
4. **Bloqueio otimista por `version`.** Toda escrita é condicionada à versão lida (`UPDATE ... WHERE id = ? AND user_id = ? AND version = ?`);
   com duas escritas sobre a mesma versão, exatamente uma vence e a outra recebe 409 `VERSION_CONFLICT`. O cliente pode mandar
   `expectedVersion` para detectar leitura velha; a substituição manual a exige. Testado com 8 threads por 15 rodadas (store) e com
   10 requisições paralelas (API): nenhuma compra aceita se perde.
5. **Rotação do refresh atômica no banco.** Um único `UPDATE ... WHERE used = FALSE RETURNING` consome o token e a escrita do novo vai
   na mesma transação; o segundo pedido concorrente espera, reavalia `used = FALSE`, não acha linha e é tratado como reuso (a família
   inteira é revogada), igual à versão em memória. Testado com 8 threads por 20 rodadas no PostgreSQL.
6. **Isolamento por construção, duas camadas.** Toda consulta e toda escrita recebem o dono e filtram por `user_id`; o serviço ainda
   passa cada posição pelo `IsolationGuard`. O identificador na rota é de posição, nunca de usuário, e posição de outro usuário
   responde 404 (igual a "não existe").
7. **Multi-ativo e moedas.** Posição do universo do painel é identificada pelo símbolo; ativo sem cotação (Tesouro, LCI, CRI, fundos,
   cripto guardada) pelo tipo e por um rótulo normalizado, para duas LCIs serem posições distintas. Só **BRL e USD**: ativo cotado em
   outra moeda (EUR, JPY, GBp...) é recusado com explicação, porque a plataforma só tem conversão oficial USD/BRL.
8. **Valorização com dado real e sem invenção.** Preço de mercado do provedor com fonte, horário e selo de frescor; câmbio pelo PTAX
   (venda) do Banco Central com data e fonte. Sem cotação, a posição é avaliada pelo custo e o valor de mercado e o resultado ficam
   nulos; sem PTAX, os valores em reais de posições em dólar ficam nulos. O resultado não realizado é na moeda da posição: o custo em
   reais usa o PTAX de hoje, não o câmbio da compra (que a plataforma não tem antes do livro), então **não se calcula resultado em
   reais**, para não misturar variação cambial com variação de preço.

## Consequências

- **Positivas:** contas, sessões, perfil e carteira sobrevivem a reinício quando a persistência está ligada (fecha a dívida 33); a
  concorrência é tratada no banco e testada contra o banco; o custo médio é exato e conferido à mão em testes.
- **Negativas:**
  - Sem persistência (execução sem Docker) tudo continua se perdendo no reinício.
  - O custo em reais de posição em dólar usa o câmbio de hoje; o correto depende do livro de movimentações (item #13), que guarda o
    câmbio de cada compra.
  - O preço médio não inclui corretagem nem emolumentos por conta própria.
  - O PostgreSQL embutido baixa um binário (cerca de 10 MB) nos testes; o item #20 pode trocá-lo por Testcontainers sem mudar os contratos.

## Alternativas rejeitadas

- **Bloqueio pessimista (`SELECT ... FOR UPDATE`):** seguraria linhas durante a requisição e esconderia o conflito; o otimista
  devolve o conflito ao cliente, que é quem sabe o que fazer.
- **Sobrescrever sem versão (último vence):** perderia compras concorrentes em silêncio.
- **Preço médio em `double`:** acumularia erro de arredondamento a cada operação (ADR 002).
- **H2 em modo PostgreSQL nos testes:** não reproduz `RETURNING`, `ON CONFLICT`, os `CHECK` nem a semântica de concorrência do
  PostgreSQL, justamente o que este item precisa provar.
- **Calcular resultado em reais com o PTAX de hoje:** daria um número aparentemente preciso e errado.
