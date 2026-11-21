# Arquitetura

Hexagonal estrita ([ADR 001](adr/001-arquitetura-hexagonal.md)), verificada pelo `ArchitectureTest` (ArchUnit) a cada `mvn verify`.

## Camadas
- **domain**: `Indicators`, `TimingEngine`, `GeoRiskScorer`, `MarketHours`, `Freshness`, `MacroSnapshot` e registros. Só JDK.
- **application** (POJOs, sem Spring/Jackson): `MarketService` (estado por ativo só com dados reais, benchmark, sinais, backtest), `BacktestService`, `StreamService`, `StatusService` e as portas `MarketDataPort`, `CacheStore`, `CandleStore`, `SignalStore`, `FundamentalsPort`, `BacktestCache`, `TickSink` e `ProviderStatusSource`.
- **infrastructure** (adaptadores de saída e agendamento)
  - `resilience`: `TokenBucketRateLimiter`, `CircuitBreaker`, `RetryPolicy`, `ResilientExecutor`.
  - `provider`: clientes Yahoo, Stooq, Frankfurter, Finnhub, FRED e RSS (todos `ProviderStatusSource`); `LiveMarketData` (failover, polling e selo de frescor); `LiveMacroData`; `FundamentalsService`.
  - `cache`: `InMemoryCacheStore`, `RedisCacheStore` e `JsonBacktestCache`.
  - `persistence`: `JdbcCandleStore`, `JdbcSignalStore` (Flyway `V1__init.sql`) e versões sem efeito quando o banco está desligado.
  - `stream`: `StreamWebSocketHandler` (canal `TickSink`), `MarketLoop` (agendador) e `ApiRateLimitFilter`.
- **web** (adaptadores de entrada): `MarketController`, `StatusController`, `SseTickSink` (canal SSE) e OpenAPI.
- **config**: `ApplicationConfig` cria os casos de uso e os liga às portas.

## Failover de dados (sem simulação)
Histórico: Yahoo, depois Stooq, depois Frankfurter (câmbio), depois o último dado real gravado no banco; sem nada real, o ativo fica `UNAVAILABLE` e o `MarketLoop` tenta de novo a cada 2 minutos. Cotações: Finnhub (com chave), Yahoo, Stooq, Frankfurter. Cada chamada passa por `ResilientExecutor`: rate limit local e circuito aberto falham rápido para o chamador tentar o próximo provedor; erros 5xx e timeouts são repetidos com backoff exponencial (250 ms, dobrando) e jitter; erros 4xx não são repetidos nem abrem o circuito. O circuito abre após 4 falhas e sonda de novo após 60 s.

## Procedência e frescor ([ADR 004](adr/004-dados-reais-e-procedencia.md))
Cada tick traz `status` (`LIVE`, `STORED`, `INSUFFICIENT`, `UNAVAILABLE`), `source`, `quoteTime` (horário na origem), `freshness` (`REAL_TIME`, `DELAYED`, `END_OF_DAY`, `STORED`) e `nextUpdateAt`. O selo vem da fonte e do pregão: fora do pregão é fechamento; Frankfurter é referência diária; Yahoo/Stooq são atrasados; Finnhub é tempo real (a confirmar no PR #3).

## Tempo real
O `MarketLoop` chama `StreamService.publishNext()` a 1 Hz: a barra do dia é atualizada no lugar com a última cotação real recebida (sem nenhum movimento entre cotações) e o lote vai para cada `TickSink` (WebSocket e SSE); a falha de um canal não afeta os demais. O painel tenta WebSocket e cai para SSE.

## Decisões herdadas da base

As decisões formais do MeridianWealth ficam em [`docs/adr/`](adr/README.md). A lista abaixo resume as escolhas da base herdada; a de número 2 foi revogada pelo [ADR 004](adr/004-dados-reais-e-procedencia.md) e removida no PR #2.

1. **Resiliência própria em vez de biblioteca.** As quatro peças são pequenas, testáveis com relógio injetado e sem dependência extra. Trade-off: menos recursos que Resilience4j.
2. ~~**Porta de dados com simulador de fallback.**~~ Revogada: nenhum preço sintético no produto.
3. **Cache compartilhado de cotações.** Várias instâncias reaproveitam a mesma consulta e respeitam os limites dos provedores.
4. **Postgres e Redis opcionais.** Sem Docker o app roda com cache em memória e sem banco; `MW_EXCLUDE_AUTOCONFIG` liga ambos no Compose.
5. **Motor em domínio puro.** Testes sem Spring; o backtest reaproveita o mesmo motor do painel.
6. **Sinais gravados só quando mudam.** Evita 1 linha por segundo por ativo.
