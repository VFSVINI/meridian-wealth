# MeridianWealth — memória do projeto

> Leia este arquivo e o `git log` antes de qualquer ação em uma conversa nova.
> Atualizado em todo PR. Última atualização: PR #1 (Issue #1), 2026-11-20.

## 1. Visão

**Plataforma institucional multi-ativo de gestão de patrimônio, inteligência quantitativa e geolocalização de ativos reais**, construída com rigor de engenharia sênior: arquitetura hexagonal estrita, `BigDecimal` para precisão financeira, idempotência, optimistic locking, eventos de domínio, observabilidade com Correlation-ID e decisões registradas em ADR.

- Repositório: `VFSVINI/meridian-wealth` · pacote base `dev.meridianwealth` · classe principal `MeridianWealthApplication`.
- Stack: Java 21, Spring Boot 3.3.5, PostgreSQL + Flyway (opcional), Redis (opcional), frontend HTML/CSS/ES Modules sem build.
- Origem: base ChronosQuant 0.2.0 (cockpit de timing e risco), importada no commit inicial.

> **Aviso:** ferramenta analítica e educacional. Não executa ordens reais e não constitui recomendação financeira.

## 2. Regras inegociáveis

### 2.1 Dados 100% reais (prioridade máxima)
- Proibido preencher lacunas com números sintéticos, mover preços artificialmente ou "animar" cotações.
- Sem provedor disponível: a tela mostra "sem dado em tempo real" e, se existir, o último dado real do banco com data e hora.
- Toda cotação exibe **fonte, horário e atraso** (tempo real, ~15 min, fechamento).
- Simulador só em `src/test`. Testes de integração usam fixtures HTTP reais gravadas ou WireMock.
- Monte Carlo, stress tests e backtests são **projeções** e levam sempre esse rótulo.
- Limites honestos das fontes gratuitas ficam documentados no README.

### 2.2 Honestidade
- Nunca inventar dados de ativos reais (coordenadas, ABL, inquilinos, balanços): fonte pública identificável com data, ou "não informado".
- Nunca versionar chaves, tokens ou `.env`.
- Se um ambiente impedir uma validação, dizer exatamente o que não foi validado.

### 2.3 Engenharia
- Idioma: Issues, commits, PRs, reviews, README, CHANGELOG e ADRs em português do Brasil; identificadores Java em inglês.
- Um PR por vez, GitHub Flow, 3 a 5 commits atômicos + 1 `fix:` pós-review, merge com `--rebase` (nunca squash).
- Build sempre verde (`./mvnw verify`: JUnit 5, JaCoCo, Checkstyle, Spotless), sem `TODO` em componentes centrais.
- Fechamento de milestone: CHANGELOG, tag `v0.<N>.0` e GitHub Release em português.
- Git: usuário `VFSVINI`, e-mail `vfs2@cin.ufpe.br`; `.gitattributes` com `eol=lf`.

## 3. Roadmap

Legenda: `[x]` concluído · `[ ]` pendente. Issues dos milestones 2 a 6 são criadas no início de cada milestone.

### Milestone 1 — Fundação Arquitetural & Mercados Globais
- [x] **#1** Build verde, rebranding `dev.chronosquant` → `dev.meridianwealth`, CI (Checkstyle/Spotless, JaCoCo, Dependabot, CodeQL), governança e ADRs 001–003.
- [ ] **#2** Remoção total de dados simulados do produto + hexagonal estrita protegida por ArchUnit (dívidas 1 e 2).
- [ ] **#3** Bolsas globais, `MarketHours` multi-fuso com feriados, Finnhub WebSocket, brapi.dev, adapter de feed pago, fixtures reais (dívida 7).
- [ ] **#4** Motor cripto 24/7 (Binance WS com failover Coinbase/Kraken, vol. √365, beta BTC).
- [ ] **#5** Taxonomia multi-ativo + API do BCB (Selic, CDI, IPCA, PTAX).

### Milestone 2 — Inteligência Institucional, Composição & Relatórios
- [ ] **#6** Raio-X de composição e geolocalização de ativos reais (dados públicos curados, fonte e data).
- [ ] **#7** Radar de relatórios periódicos e balanços com veredito e justificativa.
- [ ] **#8** Motor de suitability e carteira modelo por perfil.
- [ ] **#9** Simulador de impacto pré-ordem e glossário didático.
- [ ] **#10** Tradutor geopolítico de carteira e motor de teses institucionais (genéricas, sem posições reais).

### Milestone 3 — Segurança Zero-Trust, Concorrência & Resiliência Sênior
- [ ] **#11** JWT com refresh, hash forte, rate limit por usuário/IP com `X-Forwarded-For` confiável (dívida 5), isolation guard, CORS/CSP, modo demo.
- [ ] **#12** Persistência multi-ativo (`V2__users_and_portfolio.sql`), preço médio ponderado, BRL/USD, `@Version`.
- [ ] **#13** Livro de movimentações (`V3__portfolio_transactions.sql`), `Idempotency-Key`, domain events, IR.
- [ ] **#14** Observabilidade e FinOps (Correlation-ID, Micrometer por provedor, health checks); dívidas 3 e 4.

### Milestone 4 — Interface Dark Luxury, Mapas & Home Broker
- [ ] **#15** Protótipo visual + painel "Minha Carteira" (ECharts, montador de perfil).
- [ ] **#16** Mapa interativo de ativos reais (Leaflet, dark tiles, satélite/Street View).
- [ ] **#17** Shopping financeiro multi-classe e cockpit home broker (Lightweight Charts, paleta de comandos).

### Milestone 5 — Risco Quantitativo, Testes de Concorrência & Cloud
- [ ] **#18** Monte Carlo multi-classe e cone patrimonial (rotulado como projeção).
- [ ] **#19** Matriz de correlação e heatmap de concentração.
- [ ] **#20** Testcontainers, testes de concorrência/idempotência, PIT.
- [ ] **#21** Deploy cloud gratuito (Neon + Hugging Face Spaces, porta 7860), runbook e README executivo.

### Milestone 6 — Extensões Premium (só sob pedido)
- [ ] **#22** Central de alertas · [ ] **#23** Rebalanceamento assistido · [ ] **#24** Calendário de renda passiva · [ ] **#25** Apuração de impostos · [ ] **#26** Backtest avançado · [ ] **#27** Stress testing · [ ] **#28** Research Studio · [ ] **#29** Importação e reconciliação.

## 4. Decisões (ADRs)

| ADR | Título | Status |
|---|---|---|
| [001](adr/001-arquitetura-hexagonal.md) | Arquitetura hexagonal estrita | Aceito (implementação plena no PR #2) |
| [002](adr/002-precisao-financeira.md) | Fronteira `double` × `BigDecimal` | Aceito |
| [003](adr/003-frontend-sem-build.md) | Frontend sem etapa de build | Aceito |

Decisões menores do PR #1 (no corpo do PR): Maven Wrapper com checksum; piso JaCoCo de 70% (medido 76%); CodeQL `security-extended`; Dependabot agrupado.

## 5. Dívidas técnicas abertas

| # | Dívida | Onde | Resolver em |
|---|---|---|---|
| 1 | `application` importa Spring (`@Service`, `@Value`, `@Component`) e Jackson (`ObjectMapper` em `MarketService`) | `application/*` | PR #2 |
| 2 | `web` depende de `infrastructure` (`StreamHub`, `ProviderInfo`, `ProviderStats`) | `web/*` | PR #2 |
| 3 | `MarketService` com `synchronized` e I/O dentro do lock | `application/MarketService` | PR #14 |
| 4 | Cache de backtest desserializa record via Jackson sem teste; chave com `currentTimeMillis/30000` gera misses em rajada | `application/MarketService` | PR #14 |
| 5 | `ApiRateLimitFilter` usa `getRemoteAddr()` (todos iguais atrás de proxy) | `infrastructure/stream` | PR #11 |
| 6 | Não há cálculos monetários; motor inteiro em `double` (fronteira definida no ADR 002) | — | PRs #12/#13 |
| 7 | Parsers dos provedores sem contrato verificado contra respostas reais | `infrastructure/provider` | PR #3 |
| 8 | Simulador (`SimulatedFlow`, `/api/settings/flow`, modo `simulated`) ainda no produto; `meridianwealth.stream.flow-default` vem `true` no `application.yml` | produto | PR #2 |
| 9 | Persistência JDBC com 17% de cobertura (sem banco nos testes) | `infrastructure/persistence` | PR #20 |

**Resolvidas**
- PR #1 (pós-review): `InMemoryCacheStore` vazava memória (entradas expiradas só eram removidas quando relidas; a chave do backtest inclui parâmetros da query e muda a cada 30 s) e fazia varredura O(n) de todos os contadores a cada requisição da API. Agora há contador atômico por chave, limpeza amortizada, teto de 10 mil entradas e relógio injetável, com testes de concorrência.

## 6. Como rodar

```powershell
# Build completo (testes, cobertura, Checkstyle, Spotless) — não precisa de Maven instalado
.\mvnw.cmd verify

# App local sem Docker: cache em memória, sem banco
.\mvnw.cmd spring-boot:run
# http://localhost:8080 · Swagger em /swagger-ui.html · saúde em /actuator/health

# Stack completa (app + PostgreSQL + Redis)
docker compose up --build
```

Formatação: `.\mvnw.cmd spotless:apply`. Relatório de cobertura: `target/site/jacoco/index.html`.

## 7. Estado da validação

| Item | Estado |
|---|---|
| `mvn verify` (compilação, 51 testes, Checkstyle, Spotless, JaCoCo) | Validado localmente (Windows 11, JDK 21.0.9, Maven 3.9.9) |
| GitHub Actions (CI, CodeQL, job Docker) | Validado no PR #1 (todos os checks verdes) |
| Docker / Compose | **Não validado localmente** (Docker ausente na máquina); `docker compose config` e build da imagem validados no job `docker` da CI |
| Provedores reais (Yahoo, Stooq, Finnhub, FRED) | **Não validado** (PR #3) |
| Painel no navegador | **Não validado** (PR #2 e Milestone 4) |
