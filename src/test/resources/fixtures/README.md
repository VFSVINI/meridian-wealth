# Fixtures de provedores

Respostas HTTP **reais**, gravadas sem edição de conteúdo (apenas JSON compactado), usadas nos testes
para não depender de rede e para validar os parsers contra o contrato verdadeiro de cada provedor.

| Arquivo | Origem | Gravado em |
|---|---|---|
| `yahoo/SPY-2y.json` | `https://query1.finance.yahoo.com/v8/finance/chart/SPY?range=2y&interval=1d` | 2026-11-20 |
| `yahoo/SPY-5d.json` | `https://query1.finance.yahoo.com/v8/finance/chart/SPY?range=5d&interval=1d` | 2026-11-20 |
| `yahoo/AAPL-2y.json` | `https://query1.finance.yahoo.com/v8/finance/chart/AAPL?range=2y&interval=1d` | 2026-11-20 |
| `yahoo/AAPL-5d.json` | `https://query1.finance.yahoo.com/v8/finance/chart/AAPL?range=5d&interval=1d` | 2026-11-20 |

Os demais provedores (Stooq, Frankfurter, Finnhub, FRED, RSS) ganham fixtures reais no PR #3.
URLs sem fixture respondem HTTP 404 no `FixtureHttpGateway`, o que exercita o estado "sem dado em tempo real".
