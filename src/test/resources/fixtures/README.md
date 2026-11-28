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

| `yahoo/BTC-USD-2y.json`, `yahoo/ETH-USD-2y.json` | `query1.finance.yahoo.com/v8/finance/chart/<símbolo>?range=2y&interval=1d` | 2026-11-21 |
| `crypto/binance-miniticker.jsonl` | mensagens reais do `wss://stream.binance.com:9443/stream?streams=btcusdt@miniTicker/ethusdt@miniTicker` | 2026-11-21 |
| `crypto/coinbase-ticker.jsonl` | mensagens reais do `wss://ws-feed.exchange.coinbase.com` (canal `ticker`, BTC-USD e ETH-USD) | 2026-11-21 |
| `crypto/kraken-ticker.jsonl` | mensagens reais do `wss://ws.kraken.com/v2` (canal `ticker`, BTC/USD e ETH/USD) | 2026-11-21 |

| `yahoo/HGLG11.SA-2y.json` | `query1.finance.yahoo.com/v8/finance/chart/HGLG11.SA?range=2y&interval=1d` | 2026-11-21 |
| `bcb/sgs-432.json`, `sgs-12.json`, `sgs-4389.json` | `https://api.bcb.gov.br/dados/serie/bcdata.sgs.<código>/dados?formato=json&dataInicial=07/10/2026&dataFinal=21/11/2026` | 2026-11-21 |
| `bcb/sgs-433.json`, `sgs-13522.json` | mesmo endpoint, janela de 17/10/2025 a 21/11/2026 | 2026-11-21 |
| `bcb/sgs-1.json` | `https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados?formato=json&dataInicial=26/11/2025&dataFinal=27/11/2026` (dólar comercial de venda = PTAX de venda, 250 pregões) | 2026-11-27 |
| `bcb/ptax-periodo.json` | `https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoDolarPeriodo(...)` (11-11-2026 a 11-21-2026, top 1), **resposta crua**, com os zeros à direita do original | 2026-11-21 |

Finnhub, FRED e RSS não têm fixture gravada (sem chave ou sem necessidade); os testes usam o formato da documentação pública e dizem isso.
URLs sem fixture respondem HTTP 404 no `FixtureHttpGateway`, o que exercita o estado "sem dado em tempo real".

| `crypto/coinbase-candles-1h.json` | `https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=3600` (350 barras) | 2026-11-28 |
| `crypto/kraken-ohlc-1h.json` | `https://api.kraken.com/0/public/OHLC?pair=XBTUSD&interval=60` (721 barras) | 2026-11-28 |
| `crypto/binance-klines-1h.json` | `https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1h&limit=168` | 2026-11-28 |
| `crypto/fear-greed.json` | `https://api.alternative.me/fng/?limit=2` | 2026-11-28 |
