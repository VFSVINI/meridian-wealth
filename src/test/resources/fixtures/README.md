# Fixtures de provedores

Respostas HTTP **reais**, gravadas sem edição de conteúdo (apenas JSON compactado), usadas nos testes
para não depender de rede e para validar os parsers contra o contrato verdadeiro de cada provedor.

| Arquivo | Origem | Gravado em |
|---|---|---|
| `yahoo/SPY-2y.json` | `https://query1.finance.yahoo.com/v8/finance/chart/SPY?range=2y&interval=1d` | 2026-11-20 |
| `yahoo/SPY-5d.json` | `https://query1.finance.yahoo.com/v8/finance/chart/SPY?range=5d&interval=1d` | 2026-11-20 |
| `yahoo/AAPL-2y.json` | `https://query1.finance.yahoo.com/v8/finance/chart/AAPL?range=2y&interval=1d` | 2026-11-20 |
| `yahoo/AAPL-5d.json` | `https://query1.finance.yahoo.com/v8/finance/chart/AAPL?range=5d&interval=1d` | 2026-11-20 |
| `yahoo/SPY-2y-dia-aberto.json` | mesmo endpoint do SPY 2y, com a barra de 20/11 ainda aberta (close nulo) | 2026-11-20 |
| `yahoo/PETR4.SA-2y.json`, `yahoo/PETR4.SA-5d.json`, `yahoo/7203.T-5d.json`, `yahoo/NPN.JO-5d.json` | `query1.finance.yahoo.com/v8/finance/chart/<símbolo>` | 2026-11-20 |
| `brapi/PETR4-2y.json`, `brapi/PETR4-quote.json` | `https://brapi.dev/api/quote/PETR4?range=2y&interval=1d` e sem parâmetros | 2026-11-20 |
| `frankfurter/USD-BRL-2y.json`, `frankfurter/USD-BRL-latest.json` | `https://api.frankfurter.dev/v1/...?from=USD&to=BRL` | 2026-11-20 |
| `stooq/spy.us-bloqueio.html` | `https://stooq.com/q/d/l/?i=d&s=spy.us` (página de verificação JavaScript) | 2026-11-20 |

Finnhub, FRED e RSS não têm fixture gravada (sem chave ou sem necessidade); os testes usam o formato da documentação pública e dizem isso.
URLs sem fixture respondem HTTP 404 no `FixtureHttpGateway`, o que exercita o estado "sem dado em tempo real".
