# Carteira persistida

Itens #12 e #13 do roadmap. Decisões em [ADR 014](adr/014-carteira-persistida-preco-medio-e-bloqueio-otimista.md) e [ADR 015](adr/015-livro-de-movimentacoes-idempotencia-e-apoio-ao-ir.md). Exige login (rotas sob `/api/me`,
ver [seguranca.md](seguranca.md)). A carteira é um registro seu: não é extrato de corretora, a plataforma não executa ordens e nada
aqui é recomendação de investimento.

## Tela "Minha Carteira" (item #15)

`#/carteira` exige login (senha, Google ou demonstração). Mostra: patrimônio total em reais com o PTAX usado, custo, resultado não realizado, maior posição, HHI e posições efetivas (só sobre as posições com valor em reais); **alocação por classe** (treemap do ECharts com rótulo e percentual em texto); **aderência ao perfil salvo** (nota, tabela e radar meta contra atual, pelo mesmo `POST /api/suitability/adherence` do questionário); **posições** com preço médio, valor, resultado, peso e a fonte e o horário da cotação; **livro de movimentações** com filtro por tipo; e **ganho ou perda por posição**. Compra, venda, ajuste, baixa e abertura usam os formulários da própria tela: os números saem como texto exato, a `Idempotency-Key` é a mesma até a operação dar certo, e o conflito de versão (409) e o PTAX indisponível (503) têm mensagem própria. O que não tem dado (sem cotação, sem câmbio de compra) aparece como "não informado". Sem perfil salvo, a tela leva ao questionário; o botão "Salvar como meu perfil" grava o resultado na conta (`PUT /api/me/profile`).

## Rotas

| Rota | Corpo | O que faz |
|---|---|---|
| `GET /api/me/portfolio` | | posições valorizadas, câmbio, totais em reais, pesos por classe e notas |
| `POST /api/me/portfolio/positions` | `symbol` **ou** `type` (+ `label`), `quantity`, `averagePrice`, `currency`, `tradeDate?`, `fxRate?` | abre a posição (entrada `OPENING`); 409 se já existe |
| `POST /api/me/portfolio/positions/{id}/purchases` | `quantity`, `unitPrice`, `fees?`, `tradeDate?`, `fxRate?`, `expectedVersion?` | compra (`BUY`): novo preço médio ponderado, com as taxas dentro |
| `POST /api/me/portfolio/positions/{id}/sales` | `quantity`, `unitPrice`, `fees?`, `tradeDate?`, `fxRate?`, `expectedVersion?` | venda (`SELL`): mantém o preço médio e realiza o resultado; zerar remove a posição |
| `PUT /api/me/portfolio/positions/{id}` | `quantity`, `averagePrice`, `tradeDate?`, `fxRate?`, `expectedVersion` | ajuste manual (`ADJUSTMENT`) |
| `DELETE /api/me/portfolio/positions/{id}?expectedVersion=&tradeDate=` | | baixa (`WRITE_OFF`): remove sem venda e sem resultado |
| `GET /api/me/portfolio/transactions?assetKey=&from=&to=&limit=&offset=` | | livro de movimentações, na ordem de gravação (até 200 por página) |
| `GET /api/me/tax/report?year=` | | apoio ao imposto de renda (ver abaixo) |

Toda escrita responde `{ entry, position, closed, replayed }`: a entrada do livro e a posição depois dela (`null` se foi removida). Erros trazem
`error` e `code`: `INVALID_INPUT` 400; `NOT_FOUND` 404 (inclusive posição de outro usuário); `POSITION_EXISTS`, `VERSION_CONFLICT` e
`LIMIT_REACHED` 409 (máximo de 200 posições); `OUT_OF_ORDER` e `IDEMPOTENCY_KEY_REUSED` 422; `FX_UNAVAILABLE` 503.

## Como o preço médio e o resultado são calculados

- **Compra de `q'` a `p'` com taxas `f`** sobre `q` a `pm`: `pm' = (q · pm + q' · p' + f) / (q + q')`, arredondado a 6 casas (HALF_EVEN). Exemplo:
  10 a R$ 1.000 e mais 10 a R$ 1.100 com R$ 200 de corretagem dá 20 a R$ 1.060.
- **Venda:** a quantidade cai e o preço médio **não muda**. O **resultado realizado** é `q · p − taxas − q · pm`, arredondado uma só vez. Vender
  mais do que se tem é recusado; taxas maiores que o valor da venda também.
- **Em dólar**, o mesmo cálculo roda também em reais: o custo médio em reais é a média ponderada dos custos ao **câmbio de cada operação**, e o
  resultado realizado em reais é `líquido x câmbio da venda − quantidade x custo médio em reais`, o que inclui a variação cambial.
- Quantidade: até 8 casas. Preço e taxas: até 6. Até 15 dígitos inteiros. A ordem de grandeza é conferida antes de qualquer conta.

## Datas e câmbio

- `tradeDate` (padrão: hoje, no fuso de Brasília) não pode estar no futuro, nem antes de 2000-01-01, nem ser anterior à última operação do mesmo
  ativo (422 `OUT_OF_ORDER`): registre do mais antigo para o mais novo.
- Em operação em dólar, o câmbio é o `fxRate` informado (reais por dólar, `fxSource = USER`) ou o **PTAX (venda) do último dia útil até a
  data** (`fxSource = PTAX`, com `fxDate`). Sem PTAX e sem `fxRate`, a operação é recusada (503 `FX_UNAVAILABLE`). Operação em BRL não tem câmbio.

## Idempotência

Mande `Idempotency-Key` (1 a 64 caracteres entre letras, números, `_`, `.`, `:` e `-`) em qualquer escrita. Repetir a **mesma** requisição com a mesma
chave devolve a mesma entrada (200, cabeçalho `Idempotent-Replay: true`, `replayed: true`) sem aplicar de novo; a mesma chave com **outro**
conteúdo é 422 `IDEMPOTENCY_KEY_REUSED`; duas requisições simultâneas com a mesma chave aplicam uma só vez. A chave vale por usuário e não expira.

## Eventos

Cada escrita confirmada publica um evento (`position_opened`, `purchase_recorded`, `sale_recorded`, `position_adjusted`, `position_closed`),
depois do commit e nunca numa repetição idempotente. Hoje o assinante é o contador Micrometer `meridianwealth.portfolio.events` (por tipo).

## Moedas e valorização

- A posição é em **BRL** ou **USD**. Ativo do painel herda a moeda da cotação; ativo cotado em outra moeda (EUR, JPY, GBp...) é recusado.
- **Valor de mercado:** preço do provedor na moeda da posição, com `source`, `quoteTime` e selo de frescor. Sem cotação, `basis` é `COST` e valor de
  mercado e resultado vêm `null` ("não informado").
- **Reais:** o valor atual de posição em dólar usa o **PTAX (venda)** de hoje, com `date` e `source`; o **custo** usa o câmbio de cada compra (do
  livro). O `unrealizedPnlBrl` é a diferença e inclui a variação cambial; o `unrealizedPnl` é só variação de preço, na moeda da posição.
- Posição em dólar aberta antes do livro (item #12) não tem câmbio de compra: custo e resultado em reais ficam `null` até um ajuste com `fxRate`.

## Apoio ao imposto de renda

`GET /api/me/tax/report?year=` entrega, para o ano:

- **Posição em 31/12** de cada ativo (quantidade, preço médio, custo médio em reais e custo total em reais): o que se precisa para "Bens e Direitos".
- **Resultado realizado por mês e classe** (ações da B3, ETF, FII, cripto, exterior, outros): quantidade de vendas, valor bruto das vendas em reais,
  resultado realizado em reais, vendas sem valor em reais e vendas de um ativo comprado no mesmo dia (**possível day trade**, só sinalizado).
- **Ações da B3:** se o total das vendas do mês ficou em até R$ 20.000,00 (`withinStockExemptionLimit`), indicador **informativo**.

Base legal, **lida no texto do Planalto em 2026-11-21**:

| Ponto | Dispositivo | Uso aqui |
|---|---|---|
| Ganho líquido em vendas de ações no mercado à vista com total de vendas no mês de até R$ 20.000,00: isento | Lei nº 11.033/2004, art. 3º, inciso I | indicador por mês |
| Alíquotas sobre o ganho líquido em bolsa: 20% em day trade e 15% nas demais operações | Lei nº 11.033/2004, art. 2º, incisos I e II | só citadas, nenhum imposto é calculado |
| Rendimentos distribuídos por FII a pessoa física: isentos na fonte e na declaração, nas condições do § 1º | Lei nº 11.033/2004, art. 3º, inciso III e § 1º | só informativo (a plataforma não registra rendimentos) |

As páginas do Planalto ainda anotam a **Medida Provisória nº 1.303/2025 com "vigência encerrada"** e mostram redações sobrepostas de alguns
dispositivos: confira o texto compilado vigente antes de usar qualquer número.

**Não é apurado** (a base legal não foi confirmada em fonte oficial ou não há dado): imposto devido, DARF e IRRF; compensação de prejuízos; day trade;
ganho de capital em cotas de FII, em ETF, em criptoativos e em ativos no exterior; rendimento de renda fixa e de fundos; dividendos e rendimentos de FII.
O relatório **não substitui um contador**, não gera DARF e não preenche a declaração.

## Persistência

Com `MW_PERSISTENCE=true` (Docker Compose) as contas, as sessões, o perfil de investidor e as posições ficam no PostgreSQL
(`V2__users_and_portfolio.sql`). Sem ela, ficam em memória e somem no reinício. A migração tem restrições que o banco impõe:
quantidade e preço positivos, moeda BRL ou USD, uma posição por usuário e ativo, credencial coerente com o provedor da conta e
exclusão em cascata ao apagar o usuário.

## Limites

- O livro é cronológico por ativo; corrigir uma operação antiga é um ajuste, não uma edição. Eventos em processo, voláteis no reinício.
- Sem tela: "Minha Carteira" é a etapa 2 do item #15.
