# Arquitetura

## Camadas
- **domain**: `Indicators`, `TimingEngine`, `GeoRiskScorer`, `MarketHours` e registros. Sem Spring.
- **application**: `MarketService` (histórico por ativo, benchmark, sinais, backtest em cache), `BacktestService`, `StreamSettings` e as portas `MarketDataPort`, `CacheStore`, `CandleStore`, `SignalStore`, `FundamentalsPort`.
- **infrastructure**
  - `resilience`: `TokenBucketRateLimiter`, `CircuitBreaker`, `RetryPolicy`, `ResilientExecutor`.
  - `provider`: clientes Yahoo, Stooq, Frankfurter, Finnhub, FRED e RSS; `LiveMarketData` (failover e polling); `LiveMacroData`; `SimulatedFlow`; `FundamentalsService`.
  - `cache`: `InMemoryCacheStore` e `RedisCacheStore`.
  - `persistence`: `JdbcCandleStore`, `JdbcSignalStore` (Flyway `V1__init.sql`) e versões sem efeito quando o banco está desligado.
  - `stream`: `StreamHub` (SSE e WebSocket), `MarketLoop` (1 Hz) e `ApiRateLimitFilter`.
- **web**: `MarketController`, `StatusController`, OpenAPI.

## Failover de dados
Histórico: Yahoo, depois Stooq, depois Frankfurter (câmbio), depois o banco, e por fim o simulador. Cotações: Finnhub (com chave), Yahoo, Stooq, Frankfurter. Cada chamada passa por `ResilientExecutor`: rate limit local e circuito aberto falham rápido para o chamador tentar o próximo provedor; erros 5xx e timeouts são repetidos com backoff exponencial (250 ms, dobrando) e jitter; erros 4xx não são repetidos nem abrem o circuito. O circuito abre após 4 falhas e sonda de novo após 60 s.

## Tempo real
O `MarketLoop` roda a 1 Hz. Para dados reais, a barra do dia é atualizada no lugar (com o fluxo simulado opcional sobre a última cotação); para ativos simulados, cada tick cria uma barra nova. O lote é serializado uma vez e enviado por WebSocket e SSE. O painel tenta WebSocket e cai para SSE.

## Decisões (ADRs resumidos)
1. **Resiliência própria em vez de biblioteca.** As quatro peças são pequenas, testáveis com relógio injetado e sem dependência extra. Trade-off: menos recursos que Resilience4j.
2. **Porta de dados com simulador de fallback.** Preço ao vivo mesmo sem rede ou com limite de API; o modo é sempre exibido.
3. **Cache compartilhado de cotações.** Várias instâncias reaproveitam a mesma consulta e respeitam os limites dos provedores.
4. **Postgres e Redis opcionais.** Sem Docker o app roda com cache em memória e sem banco; `CQ_EXCLUDE_AUTOCONFIG` liga ambos no Compose.
5. **Motor em domínio puro.** Testes sem Spring; o backtest reaproveita o mesmo motor do painel.
6. **Sinais gravados só quando mudam.** Evita 1 linha por segundo por ativo.
