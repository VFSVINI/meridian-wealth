# MeridianWealth — memória do projeto

> Leia este arquivo e o `git log` antes de qualquer ação em uma conversa nova.
> Atualizado em todo PR. Última atualização: PR do item #5 (Issue #5), 2026-11-21.
> Numeração: Issues e PRs compartilham a sequência do GitHub. Os itens #1 a #3 do roadmap foram entregues antes da recriação do repositório (2026-11-20) e **não têm objeto de PR**, só os commits e as Issues fechadas. A partir do item #4, o número do PR é o que o GitHub atribuir e vai no corpo do PR.

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

### 2.4 Login com Google (escopo incorporado em 2026-11-21)

Decisão de escopo: a plataforma passa a oferecer **Sign in with Google** além do login por senha. Implementação nos itens #11 (backend) e #15 (front-end); nada é implementado antes deles.

**Backend (item #11, Milestone 3):**
- Autenticação por **Google OAuth2 / ID Token**: o backend valida o token do Google (assinatura pelas chaves públicas do Google, `iss`, `aud` igual ao `MW_GOOGLE_CLIENT_ID`, `exp` e `email_verified`) e então emite o **mesmo JWT interno + refresh token** do MeridianWealth. A autorização da API continua única, sem tratamento especial por provedor.
- **Provisionamento automático** no primeiro login com Google: `email`, `nome`, `avatar_url` e `auth_provider = GOOGLE`. Sem perfil de investidor cadastrado, o usuário é levado ao fluxo de Suitability (item #8).
- Variáveis `MW_GOOGLE_CLIENT_ID` e `MW_GOOGLE_CLIENT_SECRET`: nunca no código, documentadas no `.env.example` e no `README.md`, com o passo a passo do Google Cloud Console para `localhost` e para Hugging Face Spaces (origens e URIs de redirecionamento autorizadas).
- **Degradação elegante (obrigatória):** `GET /api/auth/config` público devolve `{ "googleLoginEnabled": true|false }`. Sem `MW_GOOGLE_CLIENT_ID` (ambiente local ou CI) a aplicação sobe normalmente e apenas sinaliza que o provedor não está configurado.
- Conta com o mesmo e-mail de um cadastro por senha: nunca vinculada em silêncio (regra a definir no ADR do item #11, com `email_verified` obrigatório).

**Front-end (item #15, Milestone 4):**
- Botão **"Continuar com Google"** na tela/modal de login e cadastro, seguindo as diretrizes visuais do Google dentro do tema *Private Banking Dark Luxury*.
- Consome `/api/auth/config`: com o provedor desativado, o botão aparece desabilitado com aviso claro de que a chave não está configurada neste ambiente.
- A stack do front-end segue o ADR 003 (sem etapa de build) até que um novo ADR a substitua; o critério acima vale em qualquer stack.

## 3. Roadmap

Legenda: `[x]` concluído · `[ ]` pendente. Issues dos milestones 2 a 6 são criadas no início de cada milestone.

### Milestone 1 — Fundação Arquitetural & Mercados Globais
- [x] **#1** Build verde, rebranding `dev.chronosquant` → `dev.meridianwealth`, CI (Checkstyle/Spotless, JaCoCo, Dependabot, CodeQL), governança e ADRs 001–003.
- [x] **#2** Remoção total de dados simulados do produto + hexagonal estrita protegida por ArchUnit (dívidas 1, 2, 4 e 8).
- [x] **#3** Bolsas globais (25 ativos), calendário multi-fuso com feriados oficiais, Finnhub WebSocket, brapi.dev, adapter de feed pago, fixtures reais (dívidas 7 e 10).
- [x] **#4** Motor cripto 24/7 (Binance WS com failover Coinbase/Kraken, vol. √365, beta BTC).
- [x] **#5** Taxonomia multi-ativo + API do BCB (Selic, CDI, IPCA, PTAX). Fecha o Milestone 1 (tag v0.1.0).

### Milestone 2 — Inteligência Institucional, Composição & Relatórios
- [x] **#6** Raio-X de composição e geolocalização de ativos reais (dados públicos curados, fonte e data). Ver [composicao.md](composicao.md).
- [ ] **#7** Radar de relatórios periódicos e balanços com veredito e justificativa.
- [ ] **#8** Motor de suitability e carteira modelo por perfil.
- [ ] **#9** Simulador de impacto pré-ordem e glossário didático.
- [ ] **#10** Tradutor geopolítico de carteira e motor de teses institucionais (genéricas, sem posições reais).

### Milestone 3 — Segurança Zero-Trust, Concorrência & Resiliência Sênior
- [ ] **#11** JWT com refresh, hash forte, rate limit por usuário/IP com `X-Forwarded-For` confiável (dívida 5), isolation guard, CORS/CSP, modo demo e **Login com Google** (seção 2.4).
- [ ] **#12** Persistência multi-ativo (`V2__users_and_portfolio.sql`), preço médio ponderado, BRL/USD, `@Version`.
- [ ] **#13** Livro de movimentações (`V3__portfolio_transactions.sql`), `Idempotency-Key`, domain events, IR.
- [ ] **#14** Observabilidade e FinOps (Correlation-ID, Micrometer por provedor, health checks); dívidas 3 e 4.

### Milestone 4 — Interface Dark Luxury, Mapas & Home Broker
- [ ] **#15** Protótipo visual + painel "Minha Carteira" (ECharts, montador de perfil) e botão **"Continuar com Google"** na tela de login e cadastro (seção 2.4).
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
| [001](adr/001-arquitetura-hexagonal.md) | Arquitetura hexagonal estrita | Aceito; implementado e verificado por ArchUnit no PR #2 |
| [002](adr/002-precisao-financeira.md) | Fronteira `double` × `BigDecimal` | Aceito |
| [003](adr/003-frontend-sem-build.md) | Frontend sem etapa de build | Aceito |
| [004](adr/004-dados-reais-e-procedencia.md) | Dados 100% reais e procedência explícita | Aceito |
| [005](adr/005-stream-cripto-com-failover.md) | Stream de cripto com failover e anualização por √365 | Aceito |
| [006](adr/006-taxonomia-e-dados-oficiais-do-bcb.md) | Taxonomia multi-ativo e dados oficiais do BCB | Aceito |
| [007](adr/007-composicao-curada-e-geolocalizacao.md) | Composição curada e geolocalização conservadora | Aceito |

Decisões menores (no corpo dos PRs):
- Item #6: tipo do imóvel só quando a fonte declara; percentuais de receita do HGLG11 somam 70,85% e não são apresentados como 100%; XPML11 vem de terceiros e é rotulado assim; conflito de classificação do MXRF11 (Tijolo × Papéis) é exibido; coordenada por via é "aproximada".
- Item #5: só atributos definidos pelo produto ou verificados em fonte pública entram na taxonomia (imposto e liquidez ficam de fora); `Rate` em `BigDecimal` e valores como texto exato na API; SGS por janela de datas (o modo "últimos N" traz datas futuras e ordem imprevisível); cada indicador do BCB é independente e mantém o último valor bom; FIIs sem benchmark (o IFIX tem uma barra na Yahoo); PTAX pelo Olinda, não pelo SGS 1.
- Item #4: três bolsas em ordem (Binance, Coinbase, Kraken) com failover por 451/403, falhas e silêncio; histórico de cripto pela Yahoo (a Binance REST sofre o mesmo bloqueio regional); stream suspende o polling REST por 30 s; regra de barra nova por dia local da bolsa; beta de 90 barras com benchmark alinhado por data; Dependabot ignora JDK não-LTS, Spring Boot major e Maven da distribuição do wrapper.
- PR #1: Maven Wrapper com checksum; piso JaCoCo de 70% (medido 76%); CodeQL `security-extended`; Dependabot agrupado.
- PR #3: calendário por regra conferido contra páginas oficiais (bolsas sem fonte verificada ficam marcadas, sem datas adivinhadas); brapi só para histórico (horário de cotação não confiável em feriado); Stooq desligado por padrão; Frankfurter no endpoint `.dev/v1`; feed pago com selo declarado pelo plano.
- PR #2: `System.Logger` na `application` (sem SLF4J, ponte JUL do Spring Boot); chave do cache de backtest pela versão dos dados; 503 para ativo sem dado real; fixtures reais da Yahoo nos testes; Dependabot ignora JDK não-LTS nas imagens.

## 5. Dívidas técnicas abertas

| # | Dívida | Onde | Resolver em |
|---|---|---|---|
| 3 | `MarketService` usa um único monitor para todos os ativos (o I/O já saiu do lock no PR #2; falta snapshot imutável ou lock por ativo) | `application/MarketService` | PR #14 |
| 5 | `ApiRateLimitFilter` usa `getRemoteAddr()` (todos iguais atrás de proxy) | `infrastructure/stream` | PR #11 |
| 6 | Não há cálculos monetários; motor inteiro em `double` (fronteira definida no ADR 002) | — | PRs #12/#13 |
| 9 | Persistência JDBC com 17% de cobertura (sem banco nos testes) | `infrastructure/persistence` | PR #20 |
| 11 | Finnhub REST e WebSocket sem validação ao vivo (sem chave); FRED idem | `infrastructure/provider` | quando houver chave |
| 12 | Calendários de feriados não verificados: Euronext, Tóquio, Hong Kong, ASX, Tadawul, JSE | `domain/Exchange` | PR futuro |
| 13 | Failover do stream de cripto validado só com recusas simuladas; o HTTP 451 real da Binance não foi observado (as três bolsas responderam do Brasil) | `infrastructure/crypto` | deploy do item #21 |
| 14 | Degrau de preço ao trocar de bolsa no stream (Binance em USDT; Coinbase e Kraken em USD) | `infrastructure/crypto` | documentado no ADR 005 |
| 16 | "Cotação desatualizada" pode aparecer para par ilíquido em canal que só publica a cada negócio (Coinbase e Kraken) mesmo com o stream saudável; a saúde deveria ser medida pela conexão | `domain/Freshness`, `infrastructure/crypto` | quando o universo de cripto crescer |
| 17 | Nomes oficiais das séries SGS 12, 4389, 433 e 13522 sem página no portal de dados abertos do BCB; semântica confirmada por recomposição aritmética (CDI diário composto, 12 IPCA mensais compostos), não por documentação. A página de documentação do Olinda também não renderiza | `docs/mercados.md` | quando o BCB publicar |
| 18 | Correspondência produto → sigla do título público (LFT, LTN, NTN-F, NTN-B) sem releitura em página oficial (o Tesouro Direto respondeu 403); os nomes dos produtos foram confirmados nos dados abertos da STN. Notas comerciais e promissórias não constam na lista do FGC (tratadas como sem garantia) | `domain/AssetType` | item #12 |
| 19 | Dado de composição é um retrato (2026-11-21): envelhece e a atualização é manual (rodar o script e revisar o diff) | `scripts/curadoria`, `data/composicao` | deploy do item #21 ou job agendado |
| 20 | Cobertura de coordenadas: 32 de 72 imóveis listados (24 aproximados por via); HGLG11 6 de 36, pois o informe quase nunca traz cidade ou CEP | `data/composicao` | quando houver fonte pública melhor |
| 21 | HGLG11: percentuais de receita por imóvel somam 70,85% e o informe não explica o resto; KNRI11 lista 17 de 20 imóveis; XPML11 sem fonte oficial (403) e sem data de referência | `data/composicao` | quando as fontes oficiais forem acessíveis |
| 22 | Links de Street View dependem de haver imagem no ponto (o Google decide ao abrir); a existência da imagem não foi verificada | `domain/composition/MapLinks` | item #16 (mapa) |

**Resolvidas**
- Item #5 (pós-review): a Selic meta (SGS 432) é preenchida para a frente com o valor vigente, então sua data é sempre a de hoje; a regra de defasagem por data nunca disparava para ela e, se a coleta falhasse por dias, o painel afirmaria "dado em dia". Agora cada observação guarda quando o sistema a confirmou (`confirmedAt`), qualquer série fica `STALE` após 3 h sem confirmação, a Selic meta não usa a data como idade e a tela diz "vigente em". `refresh()` do BCB passou a ser exclusivo (antes duas rodadas simultâneas disputavam a cota e o retrato) e `Rate.compoundedOver` valida o teto de períodos do `BigDecimal.pow`.
- Item #5: dívida 15 (PRs incompatíveis do Dependabot: #6 fechado à mão, #9 e #12 fechados pelo próprio Dependabot após a nova configuração).
- Item #4 (pós-review): o stream de cripto zerava o backoff ao abrir a conexão (e não ao provar que ela funciona) e deixava o socket aberto quando a bolsa devolvia erro; uma bolsa que aceitava o handshake e derrubava a conexão era martelada a cada segundo, acumulando WebSockets abertos, sem nunca fazer failover. Agora as falhas só zeram no primeiro preço válido, o socket é fechado quando a perda é decidida pelo cliente e três falhas seguidas isolam a bolsa por 5 min.
- PR #3 (pós-review): com o dia em andamento, a Yahoo manda a barra corrente com close nulo; o parser a descartava e a cotação de hoje era gravada sobre a barra de ontem (SPY 19/11 aparecia com 689,81 em vez de 715,87). Agora a barra do dia usa os OHLC reais com o close do meta, cotação de dia posterior abre barra nova e a abertura é o primeiro preço observado.
- PR #3: dívida 7 (parsers de Yahoo, brapi, Frankfurter e Stooq validados contra respostas reais gravadas) e dívida 10 (Stooq desligado por padrão, mensagem clara de verificação JavaScript).
- PR #2 (pós-review): o rate limiter descartava na hora a carga paralela de históricos da partida (rajada de 5 da Yahoo para 10 ativos), deixando 3 ou 4 ativos sem dado com a fonte no ar; agora históricos aguardam o próximo token (até 20 s, dormindo fora do lock) e os 10 ativos carregam em 7,8 s. Rate limit local também deixou de zerar o circuit breaker.
- PR #2: dívida 1 (`application` sem Spring/Jackson/SLF4J, POJOs ligados em `config`), dívida 2 (portas `TickSink` e `ProviderStatusSource`; `web` sem `infrastructure`), dívida 4 (`JsonBacktestCache` testado e chave pela versão dos dados) e dívida 8 (simulador fora do produto).
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
| `mvn verify` (compilação, 199 testes incluindo ArchUnit, Checkstyle, Spotless, JaCoCo) | Validado localmente (Windows 11, JDK 21.0.9, Maven 3.9.9) |
| GitHub Actions (CI, CodeQL, job Docker) | Validado no PR #1 (todos os checks verdes) |
| Docker / Compose | **Não validado localmente** (Docker ausente na máquina); `docker compose config` e build da imagem validados no job `docker` da CI |
| Provedores reais | Item #5: BCB (SGS e Olinda) **validado ao vivo** (7 indicadores, 6 chamadas, 0 falhas) e FIIs na Yahoo; item #4: Binance, Coinbase e Kraken **validadas ao vivo** (stream real rodando no app, 29 ativos carregados, preços com 1 s de idade); item #3: Yahoo (25 ativos), brapi (B3) e Frankfurter **validados ao vivo** com latência medida; Stooq bloqueado por verificação JS; Finnhub (REST/WS) e FRED não validados (sem chave). Detalhes em `docs/mercados.md` |
| Painel no navegador | Validado no PR #2 em navegador Chromium (desktop): dados reais, selos, estado sem dado, sem erros de console da aplicação. Larguras 1024/390 px e acessibilidade completa ficam para o Milestone 4 |
