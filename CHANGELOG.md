# Changelog

Todas as mudanças relevantes do projeto ficam registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o versionamento segue o [SemVer](https://semver.org/lang/pt-BR/).
Cada milestone fecha com uma tag `v0.<N>.0` e uma GitHub Release.

## [Não lançado]

### Removido
- Simulador de fallback (`SimulatedFlow`), fluxo simulado sobre a cotação real, endpoint `POST /api/settings/flow`, propriedade `meridianwealth.stream.flow-default` e modo `MW_DATA_MODE` (ADR 004).

### Adicionado
- Selo de frescor em cada cotação (tempo real, ~15 min, fechamento, último dado real gravado), horário da cotação na origem e próxima atualização.
- Estado "sem dado em tempo real" na API (503) e no painel; último dado real do banco rotulado com data e hora.
- Nova tentativa automática, a cada 2 minutos, dos ativos sem dado.
- Portas `TickSink`, `ProviderStatusSource` e `BacktestCache`; casos de uso `StreamService` e `StatusService`; composição em `dev.meridianwealth.config`.
- Testes ArchUnit das regras de camadas e da política de dados reais; fixtures reais da Yahoo; `MarketServiceTest`, `StreamServiceTest` e `JsonBacktestCacheTest`.
- ADR 004 (dados 100% reais e procedência).
- Maven Wrapper 3.9.9 com checksum SHA-256 verificado.
- Cobertura JaCoCo com piso de 70% de linhas no `verify`.
- Esteira com CodeQL (`security-extended`), Dependabot (Maven, Actions e Docker) e resumo de cobertura no job.
- Governança: `CONTRIBUTING.md`, `SECURITY.md`, templates de Issue e PR em português e `docs/PROJETO.md` como memória do projeto.
- ADRs 001 (arquitetura hexagonal), 002 (precisão financeira) e 003 (frontend sem etapa de build).

### Alterado
- Camada `application` sem Spring, Jackson ou SLF4J; `web` sem dependência de `infrastructure`.
- Chave do cache de backtest pela versão dos dados (antes expirava a cada 30 s).
- `/api/status` expõe `channels` (WebSocket e SSE) no lugar de `wsClients`/`sseClients` e não traz mais `dataMode`/`flow`.
- Rebranding de ChronosQuant (`dev.chronosquant`) para MeridianWealth (`dev.meridianwealth`): classe principal `MeridianWealthApplication`, artefato `meridian-wealth`, imagem Docker, textos da UI e variáveis `CQ_*` → `MW_*`.
- Prefixo de propriedades `chronosquant.*` → `meridianwealth.*`.

### Base herdada
- Importada a base inicial ChronosQuant 0.2.0 (motor quantitativo, provedores com failover, resiliência própria, stream WebSocket/SSE e painel). Primeiro `mvn verify` executado neste PR: 45 testes, todos verdes.
