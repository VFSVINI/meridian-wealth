# MeridianWealth — memória do projeto

> Leia este arquivo e o `git log` antes de qualquer ação em uma conversa nova.
> Atualizado em todo PR. Última atualização: PR do item #43 (Issue #78), 2026-11-24. Milestone 5 concluído (v0.5.0).
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
- [x] **#7** Radar de relatórios periódicos e balanços com veredito e justificativa.
- [x] **#8** Motor de suitability e carteira modelo por perfil.
- [x] **#9** Simulador de impacto pré-ordem e glossário didático.
- [x] **#10** Tradutor geopolítico de carteira e motor de teses institucionais (genéricas, sem posições reais). Ver [teses.md](teses.md).

### Milestone 3 — Segurança Zero-Trust, Concorrência & Resiliência Sênior
- [x] **#11** JWT com refresh, hash forte, rate limit por usuário/IP com `X-Forwarded-For` confiável (dívida 5), isolation guard, CORS/CSP, modo demo e **Login com Google** (seção 2.4). Ver [seguranca.md](seguranca.md).
- [x] **#12** Persistência multi-ativo (`V2__users_and_portfolio.sql`), preço médio ponderado, BRL/USD, `@Version`. Ver [carteira.md](carteira.md).
- [x] **#13** Livro de movimentações (`V3__portfolio_transactions.sql`), `Idempotency-Key`, domain events, IR. Ver [carteira.md](carteira.md).
- [x] **#14** Observabilidade e FinOps (Correlation-ID, métricas Prometheus por provedor e por cache, health checks em grupos, métricas atrás de token), leituras do mercado sem monitor compartilhado (dívida 3) e fronteiras entre módulos verificadas por ArchUnit. Ver [observabilidade.md](observabilidade.md) e o ADR 016.

### Fora do roadmap numerado
- [x] **Camada de segurança** (Issue #43): auditoria pelas listas de requisitos com sonda no app real, cookie `HttpOnly`, HTTPS forçado, LGPD (política, termos, exportação e exclusão), documentação da API desligada por padrão e varreduras de segredos, SQL e SSRF. Ver [seguranca-checklist.md](seguranca-checklist.md).

### Milestone 4 — Interface Dark Luxury, Mapas & Home Broker
- [x] **#15** Protótipo visual + painel "Minha Carteira" (ECharts, montador de perfil) e botão **"Continuar com Google"** na tela de login e cadastro (seção 2.4).
  - [x] Etapa 1 (Issue #31, feita antes do #11): sistema de design, shell (navbar, sidebar, busca Ctrl+K, tema escuro e Ivory) e as páginas que já têm backend. Ver [design-prototipo.md](design-prototipo.md).
  - [x] Etapa 2 (Issue #26): entrada (senha, Google e demonstração), menu da conta, Minha Carteira (patrimônio, alocação em treemap, aderência com radar, posições, operações, livro, ganho ou perda por posição) com ECharts servido localmente, e "salvar como meu perfil" (montador de perfil). Ver [carteira.md](carteira.md) e o ADR 017.
- [x] **#16** Mapa de ativos reais (`#/mapa`): Leaflet servido pelo projeto, **sem mapa-base de terceiros** (contorno dos estados do IBGE), marcadores com a precisão à vista, filtros, lista com os imóveis sem coordenada e o motivo, comparação de fundos. Ver [composicao.md](composicao.md) e o ADR 018.
- [x] **#17** Cockpit com Lightweight Charts (candles, volume, SMA50, Bollinger, níveis do motor, inspetor, tabela, exportar imagem), boleta de simulação (sem ordens), "usar minha carteira salva" no simulador e no tradutor, e **Shopping financeiro** (`#/shopping`, catálogo dos tipos com as referências oficiais do Banco Central, sem ofertas inventadas). Paleta Ctrl+K já existia. Ver o ADR 019.

### Milestone 5 — Risco Quantitativo, Testes de Concorrência & Cloud
- [x] **#18** Monte Carlo e metas (`#/montecarlo`): cone patrimonial (percentis 5, 25, 50, 75 e 95), probabilidade de bater a meta e distribuição do patrimônio final, por premissas manuais ou pela carteira salva (reamostragem de retornos **reais** alinhados por data, que preserva a correlação). Sempre rotulado projeção; determinístico por semente. Ver o ADR 020.
- [x] **#19** Correlação e concentração (`#/correlacao`): matriz de correlação de Pearson entre ativos e entre classes (retornos reais, par a par, 252 pregões, célula sem dado fica "n/d"), risco da carteira com a correlação contra a média ponderada (benefício da diversificação) e mapa de concentração por faixa de risco. Ver o ADR 021.
- [x] **#20** Testcontainers (PostgreSQL 16 em contêiner na CI), concorrência e idempotência pela API HTTP nos dois modos de persistência (cadastro, refresh e venda simultâneos) e **PIT** (teste de mutação) sobre o domínio, com meta de 80% (medido 85%). Ver o ADR 022.
- [x] **#21** Deploy cloud gratuito: serviço web do Render (Docker, plano gratuito) com PostgreSQL no Neon, `render.yaml`, runbook ([deploy.md](deploy.md)) e README executivo. O primeiro plano (Hugging Face Spaces) caiu porque Docker exige plano pago; o deploy real depende das contas do dono e **ainda não foi concluído**. Ver os ADRs 023 e 024.

### Milestone 7 — Redesenho profissional e universo de ativos
Aberto em 2026-11-23, a partir do primeiro uso do site no ar (feedback do dono do projeto). Um PR por item, nesta ordem; cada item cria a sua Issue. Referência de design pedida pelo dono: [os exemplos de fintech em design-prototipo.md](design-prototipo.md#referências-de-design-fintech-indicadas-pelo-dono-do-projeto-em-2026-11-23).
- [x] **#30** Fundação visual: tela que se ajusta a notebook, tablet e celular (barra lateral recolhida em telas até 1279 px, espaçamento e tipografia fluidos, sem rolagem horizontal em 390, 768, 1093 e 1366 px), tema claro com cartões que se destacam do fundo e contraste AA medido por teste, saudação por horário (inclui "Boa madrugada") e **seletor de ativo com busca por digitação** no lugar da lista longa. Ver o ADR 025.
- [ ] **#31** Repaginação das páginas. **Etapa 1 feita (Issue #62): página inicial com hero 3D original** em three.js local (ADR 026); restam as demais páginas. Plano: começando pela **página inicial com hero 3D original** (medalhão com a marca do MeridianWealth em CSS 3D, inclinação pelo ponteiro, sem dado inventado; inspirada em estrutura e princípios do Wealthsimple, sem copiar nada deles) e seguindo com: Mercados em cartões com mini-gráficos no lugar da lista vertical, Painel, Shopping, Glossário, Saúde dos dados, Radar de relatórios, Brasil (juros e inflação) e Simulador, com menos texto e tipografia e alinhamento padronizados.
- [ ] **#32** Cockpit profissional e mais gráficos interativos.
- [x] **#33** (Issue #82, ADR 034) Universo de ativos: catálogo montado de listas oficiais (SEC, CVM e B3, com permissão de download dada em 2026-11-23) e busca de qualquer ativo nacional e global.
- [x] **#34** (Issue #84, ADR 035) Composição e Mapa para todos os ativos, só com o que uma fonte publica ("não informado" no resto).
- [ ] **#35** Stress testing com cenários históricos reais (absorve o #27 do Milestone 6).
- [ ] **#36** Perfil de investidor com questionário profissional.
- [ ] **#37** Geopolítica em dia (verificar a frequência de atualização das manchetes antes de mexer).
- [ ] **#38** Redefinição de senha por e-mail (serviço de e-mail gratuito, chaves só como segredo).
- [ ] **#39** Login com Google ativo e o primeiro deploy conferido (faixa do proxy, cookie `Secure`).

**Bloco 3D** (pedido do dono em 2026-11-23: "gráficos de bolsa e tudo o que você puder imaginar", o site "repleto e completamente lindo"). Regras de todo item 3D: **só dado real**, com fonte, horário e atraso à vista (ADR 004); **alternativa em tabela ou 2D** para quem não usa o 3D (leitor de tela, movimento reduzido, sem WebGL); pausa fora da tela, tratamento da perda de contexto e orçamento de desempenho, reusando um módulo comum (`kit3d`: redimensionar, pausar, perda de contexto, rolagem) em vez de repetir o código do hero; o three.js já está no projeto, então não há novo download.
- [x] **#40** Cockpit em 3D (Issue #70, ADR 028): o histórico real do ativo como terreno de candles (x = tempo, y = preço), volume em barras no piso, SMA50 e faixa de Bollinger, câmera orbital (arrastar, Ctrl+roda ou botões para zoom, duplo clique restaura, setas), passar o mouse mostra o candle real e atualiza o inspetor. Cria o módulo compartilhado `kit3d.js`, que o hero também passa a usar. O 2D e a tabela continuam como alternativa.
- [x] **#41** Globo 3D dos mercados (Issue #72, ADR 029): as dez bolsas com cidade no globo e FX e cripto como anéis em órbita, coloridas por aberta, fechada ou feriado **agora** (o estado do servidor, com os calendários reais e o aviso de calendário não verificado), dia e noite pelo Sol calculado do horário UTC, continentes do Natural Earth (domínio público, baixado com permissão e fixado por hash); clique ou toque numa bolsa mostra o estado, a hora local e os ativos dela, que abrem o cockpit. O relógio de pregões vira botões (alternativa em texto, teclado e toque).
- [x] **#42** Correlação em 3D (Issue #76, ADR 031): a matriz de Pearson da carteira como barras (altura = correlação, verde positiva para cima, vermelha negativa para baixo); a célula "não informado" é só um marcador cinza achatado, **nunca zero**; o tooltip traz o par, o valor, os pregões em comum e o aviso de estimativa frágil; a tabela é a alternativa.
- [x] **#43** Minha Carteira em 3D (Issue #78, ADR 032): rosca 3D da alocação, anel de fora com as classes (percentuais exatos da API) e anel de dentro com as posições com valor em reais (as demais ficam de fora e isso é dito); passar o mouse numa fatia a destaca e mostra o nome, o percentual e, nas posições, o valor. Ao sair da conta o módulo esquece a carteira. A lista, o treemap 2D e a tabela de posições são a alternativa.
- [x] **#44** Monte Carlo em 3D (Issue #74, ADR 030): o cone patrimonial como superfície (x = tempo, y = patrimônio, z = percentil) com as cinco curvas dos percentis 5, 25, 50, 75 e 95 exatos da simulação, a parede do histograma do patrimônio final e o plano da meta, na mesma escala; passar o mouse mostra o mês e os cinco percentis. **Sempre rotulado projeção.** A câmera orbital saiu do cockpit para o `kit3d.js` e é compartilhada.
- [x] **#45** Microinterações e transições (Issue #68, ADR 027): transição "íris" entre telas, cartões que se inclinam em 3D com brilho que segue o ponteiro e revelação ao rolar, tudo desligado com `prefers-reduced-motion`. **Contadores de preço que sobem de zero ficaram de fora de propósito:** o ADR 004 proíbe preço animado. Mini-gráficos que se desenham entram com os cartões de Mercados (#31).
- [ ] **#46** Fundo vivo sutil (linhas e partículas) em páginas escolhidas, só se couber no orçamento de desempenho medido.

### Milestone 8 — Cosmos: o protótipo aprovado do Claude Design
Aberto em 2026-11-24. O dono achou o site no ar com informação demais e telas feias, gerou o protótipo "Cosmos" no Claude Design (PNGs em `docs/design-prototipo/cosmos/`, a referência **obrigatória** e fiel: layout, gráficos, transições, tipografia) e pediu fidelidade total mais ideias novas. Direção: fundo de espaço com estrelas, âmbar e ciano, título de cinema em fonte condensada, uma pergunta por tela, cartões em grade, curso em módulos, nenhum texto de brinquedo. **Choque com o ADR 004:** o protótipo mostra ticks e números de "exemplo"; no produto tudo é dado real com fonte, e o que não existe vira "não informado". Itens, um PR cada, com a sua Issue:
- [x] **#47** (Issue #80, ADR 033) Fundação Cosmos: fontes locais (OFL), tokens do tema escuro e Ivory, campo de estrelas em canvas, barra superior e menu por seções (Explorar, Entender, Simular), rodapé legal fixo e a **página Início** (apresentação, três passos, por que confiar), sem a moeda 3D. O fundo tem **céu vivo** (estrelas e nebulosas que se mexem e reagem ao ponteiro) e a troca de página é um **salto de velocidade da luz**; os gráficos em estilo sistema solar (órbita da carteira no Simulador, constelação de fontes na Saúde dos dados, galáxia de moedas no Cripto 24h) usam o `kit3d` nos itens #53 e #54.
- [x] **#48** (Issue #88, ADR 036) Mercados (lista com mini-gráficos e destaque que troca sozinho, filtros, Lista/Tabela/Mapa) e **Ativo** ("a leitura em uma frase", camadas Entrada/Alvos/Invalidação, "Conheça o ativo").
- [x] **#49** (Issue #90, ADR 037) Academia: curso em módulos com capítulos, quiz e progresso salvo no navegador.
- [x] **#50** (Issue #92, ADR 038) Brasil: juros e inflação com gráficos históricos reais do Banco Central, "no seu bolso", juro real e linha do tempo do Copom.
- [x] **#51** (Issue #94, ADR 039) Geopolítica: mapa de risco por país, passo a passo, quatro termômetros e notícias com link para a matéria.
- [x] **#52** (Issue #96, ADR 040) Minha Carteira (rosca e mapa das posições), Perfil em sete perguntas e Shopping com comparador.
- [ ] **#53** Mapa de ativos (Mundo e Brasil), Risco (Monte Carlo com paisagem de probabilidades), Simulador pré-ordem ("órbita da carteira") e Saúde dos dados ("constelação de fontes"). **Partes feitas (Issue #101): Saúde dos dados, Simulador e Risco.** Falta o Mapa de ativos.
- [ ] **#54** Cripto 24h com dado real (relógio de 24 horas, galáxia de moedas, dominância, medo e ganância), só com fonte conectada.
- [ ] **#55** Oportunidades por perfil: radar de janelas com regra publicada, nunca previsão.
- [ ] **#56** Celular e Ivory de todas as páginas, estados de primeira classe, e as telas da série anterior que o Cosmos não redesenhou (Radar de relatórios, Stress test, Alertas e calendário, Configurações, Onboarding), absorvendo o #35.
O catálogo de ativos (**#33**) segue como prioridade em paralelo: sem ele a busca do protótipo não cobre tudo o que é negociado.

### Milestone 9 — Qualidade de código, validação do motor e IA
Aberto em 2026-11-29, depois do redesenho Cosmos (itens #47 a #56). Combinado com o dono: manter a arquitetura hexagonal (não migrar para MVC) e fazer os itens abaixo nesta ordem, cada um com a sua Issue e o seu PR. IA só com dado público, atrás de uma porta (provedor trocável), com cache e cota por usuário; o assistente nunca inventa número e recusa recomendação (ADR 004).
- [ ] **#57** SOLID e pastas por assunto, **sem mudar comportamento** (os testes e o ArchUnit são a rede de segurança): dividir o `MarketService` (estado e publicação do snapshot, atualização periódica, acompanhamento de ativos e avaliação de sinais) mantendo uma fachada; quebrar o `MarketDataPort` em portas pequenas (histórico, cotação ao vivo, macro e catálogo) e dar a quem só precisa da lista de ativos uma interface mínima; provedores de histórico como estratégias numa lista ordenada e classes de ativo num registro único; injetar o `TimingEngine`; agrupar `domain`, `application` e `web` por assunto (mercado, sinais, macro e ativos); novas regras no ArchUnit (limite de métodos públicos e de dependências, sem ciclos entre assuntos); ADR registrando o que foi aplicado. Só onde dói hoje: interface para tudo piora o código.
- [ ] **#58** Painel de validação do motor, sem IA e sem custo: correlação entre o score e o retorno futuro fora da amostra, taxa de acerto por faixa de score com intervalo de confiança (bootstrap), custo de operação, comparação com comprar e segurar e o aviso explícito de quando não há evidência. O absorve o backtest avançado (#26 do Milestone 6) no que couber.
- [ ] **#59** "Pergunte à Academia": RAG sobre o corpus próprio (capítulos da Academia, glossário e regras do motor), começando por busca por palavras (BM25) e comparando com embeddings, com perguntas de teste rotuladas (acerto da busca, fidelidade da resposta e recusa a pedir recomendação). IA gratuita atrás de uma porta, só texto público, com cache, cota por usuário e degradação honesta quando a cota acaba.
- [ ] **#60** Agente com ferramentas ("Pergunte aos dados"): chama as APIs existentes (cotação, sinal, composição, juros, correlação), cita fonte e horário de cada número, mostra as consultas feitas e recusa recomendação; só se #58 e #59 ficarem sólidos. Dados da carteira só com consentimento claro.

### Milestone 10 — DevOps e plataforma, sem custo
- [ ] **#61** Observabilidade: Spring Actuator com métricas Prometheus, logs estruturados em JSON, painel Grafana (plano gratuito ou `docker compose` local) e alertas simples de saúde.
- [ ] **#62** Deploy automático: workflow que publica a imagem no GHCR e aciona o *deploy hook* do Render, com verificação de saúde depois; migração do banco já coberta pelo Flyway.
- [ ] **#63** Infraestrutura como código (Terraform) descrevendo o ambiente, **sem aplicar** em nuvem paga; ADR comparando Render e Neon com GCP Cloud Run (custo, cartão, camada gratuita). Nada migra para o GCP sem decisão sua.

### Transversal — Guia de estudo para entrevista
- [ ] **#64** Guia de estudo para entrevista, **privado: nunca vai para este repositório público**. Fica na pasta local `estudo-privado/` (ignorada pelo Git) e, se você quiser cópia, num repositório privado separado. Um guia por assunto (arquitetura hexagonal, motor de sinais, dados reais, segurança, testes e CI, frontend sem build, Cosmos, IA, DevOps), cada um com o problema, a decisão, a alternativa descartada, "explique em 1 minuto" e as perguntas prováveis. Cresce a cada item entregue.

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
| [008](adr/008-radar-de-relatorios-e-veredito-mecanico.md) | Radar de relatórios e veredito mecânico | Aceito |
| [009](adr/009-suitability-por-questionario-e-tetos.md) | Suitability por questionário, tetos e carteira modelo | Aceito |
| [010](adr/010-impacto-pre-ordem-e-glossario.md) | Impacto pré-ordem e glossário ligado às métricas | Aceito |
| [011](adr/011-tradutor-geopolitico-e-teses-genericas.md) | Tradutor geopolítico e teses institucionais genéricas | Aceito |
| [012](adr/012-prototipo-visual-como-referencia.md) | Protótipo visual como referência obrigatória da interface | Aceito |
| [013](adr/013-autenticacao-jwt-google-e-defesas-de-borda.md) | Autenticação por JWT, Login com Google e defesas de borda | Aceito |
| [014](adr/014-carteira-persistida-preco-medio-e-bloqueio-otimista.md) | Carteira persistida, preço médio ponderado e bloqueio otimista | Aceito |
| [015](adr/015-livro-de-movimentacoes-idempotencia-e-apoio-ao-ir.md) | Livro de movimentações, idempotência e apoio ao imposto de renda | Aceito |
| [016](adr/016-monolito-modular-e-observabilidade.md) | Monólito modular com fronteiras verificadas e observabilidade por provedor | Aceito |
| [017](adr/017-interface-da-carteira-sessao-no-cliente-e-echarts-local.md) | Interface da carteira: sessão no cliente, ECharts local e Google na CSP | Aceito |
| [018](adr/018-mapa-sem-mapa-base-de-terceiros.md) | Mapa de ativos reais com Leaflet e sem mapa-base de terceiros | Aceito |
| [019](adr/019-cockpit-com-lightweight-charts-e-shopping-sem-ofertas-inventadas.md) | Cockpit com Lightweight Charts e shopping financeiro sem ofertas inventadas | Aceito |
| [020](adr/020-monte-carlo-reprodutivel-e-excecao-a-aleatoriedade.md) | Monte Carlo reprodutível e a exceção estreita à regra de aleatoriedade | Aceito |
| [021](adr/021-correlacao-par-a-par-e-celula-sem-dado.md) | Correlação par a par, célula sem dado e risco da carteira com diversificação | Aceito |
| [022](adr/022-testes-de-persistencia-em-conteiner-e-mutacao.md) | Persistência testada em contêiner, concorrência pela API e teste de mutação | Aceito |
| [023](adr/023-deploy-gratuito-hugging-face-e-neon.md) | Deploy gratuito no Hugging Face Spaces com PostgreSQL no Neon | Substituído pelo 024 |
| [024](adr/024-deploy-gratuito-render-e-neon.md) | Deploy gratuito no Render com PostgreSQL no Neon | Aceito |
| [025](adr/025-fundacao-visual-escala-contraste-e-seletor-com-busca.md) | Fundação visual: escala, contraste e seletor de ativo com busca | Aceito |
| [026](adr/026-hero-3d-original-com-three-js-local.md) | Hero 3D original da página inicial com three.js servido localmente | Aceito |
| [027](adr/027-transicoes-e-microinteracoes-sem-animar-numeros.md) | Transições e microinterações sem animar números | Aceito |
| [028](adr/028-kit3d-e-cockpit-em-3d.md) | Módulo 3D compartilhado e cockpit em 3D com o histórico real | Aceito |
| [029](adr/029-globo-3d-dos-mercados.md) | Globo 3D dos mercados com o estado real do servidor | Aceito |
| [030](adr/030-monte-carlo-3d-e-orbita-compartilhada.md) | Monte Carlo em 3D e câmera orbital compartilhada | Aceito |
| [031](adr/031-correlacao-em-3d.md) | Correlação em 3D com célula sem dado nunca como zero | Aceito |
| [032](adr/032-carteira-em-3d.md) | Minha Carteira em 3D: rosca de alocação sem guardar dado depois do logout | Aceito |

Decisões menores (no corpo dos PRs):
- Item #31, etapa 1: o hero 3D é identidade visual (nenhum número dentro dele), original (nada do Wealthsimple foi copiado), em three.js 0.170.0 baixado com permissão e fixado por SHA-256; pausa fora da tela, com movimento reduzido tem um quadro estático e sem WebGL fica o medalhão em CSS.
- Item #30: a barra lateral começa recolhida até 1279 px (a escolha do usuário vence); o tema claro ganhou fundo creme mais escuro com cartões brancos; o seletor de ativo é um combobox e não um select; a regra que pintava todo `canvas` de branco foi removida porque escondia as camadas do gráfico.
- Item #21 (corrigido na Issue #58): o Hugging Face exige plano pago para Docker, então o deploy vai para o Render; o Redis fica fora do deploy gratuito (cache em memória); o Render publica sem workflow nosso (`autoDeploy: false`); o `HEALTHCHECK` usa o `liveness` porque o agregado fica `DEGRADED` (normal) enquanto falta dado de algum provedor.
- Item #20: o PostgreSQL embutido segue como padrão local (sem Docker) e o contêiner roda na CI; o teste dourado do motor de timing é de caracterização (congela o comportamento) e vem do PIT, que achou o motor com 85% dos mutantes vivos; a meta de mutação é 80% e só sobe; o beta com benchmark parado passou a ser "não informado" e o motor deixou de explicar um sinal de realização com "sem gatilhos relevantes" (agora diz que o sinal vem da soma de leituras moderadas).
- Item #19: correlação par a par (um ativo curto só apaga as próprias células), piso de 60 pregões, célula sem dado é `null` e vira "n/d", nunca zero; classe sem histórico em comum fica fora da matriz de classes e a tela diz; ativos sem faixa de risco do motor vão para a coluna "sem classificação" do mapa de concentração.
- Item #18: `SplittableRandom` com semente explícita só em `domain.montecarlo` (as outras fontes de aleatoriedade seguem proibidas); posição sem histórico real fica parada no valor atual, nunca preenchida com a Selic ou o CDI; ativos em dólar entram pelo retorno na moeda do ativo (o câmbio não entra); a semente padrão sai da própria entrada, então reabrir a tela mostra o mesmo cone.
- Item #17: o logotipo da TradingView fica desligado porque injeta `<style>` e a CSP o bloqueia (crédito por texto e link); o shopping só mostra a referência oficial de cada indexador e diz que a taxa do produto não existe; nomes globais dos scripts passam a ser únicos por teste (a colisão `renderList` do mapa quebrou a lista de Mercados e foi corrigida aqui).
- Item #16: o mapa não tem tiles (privacidade e CSP fechada), o estado é pintado pela contagem de imóveis do dado curado, painéis de exemplo do protótipo sem fonte ficaram de fora e a vacância do fundo inteiro só aparece quando a fonte a publica ("do fundo"); o `L` global do `app.js` obrigou a ler o Leaflet de `window.L`.
- Camada de segurança: o refresh em cookie só vale com o cabeçalho `X-Session-Mode: cookie` (clientes de API seguem recebendo no corpo); renovação entre abas por Web Lock; o aceite dos termos é exigido na tela, não na API; o texto de privacidade e termos é informativo e pede revisão jurídica; a documentação da API sai do ar por padrão (`MW_API_DOCS=true` liga).
- Item #15 (etapa 2): decimais saem do formulário como texto exato (nunca por `double`); a mesma `Idempotency-Key` vale até a operação dar certo; "por corretora" e "do aporte ao saldo" do protótipo não existem (o modelo não guarda corretora nem aportes: sem dado, sem gráfico); sem "Importar extrato" (Milestone 6) e sem alternância BRL/USD; o avatar do Google não é carregado (inicial do nome), então a CSP não libera imagens externas.
- Item #14: microsserviços só por gatilho medido (carga, cadência, isolamento, infraestrutura), com a ingestão de mercado como candidata e carteira + livro sempre juntos; `liveness` e `readiness` sem provedor externo e `DEGRADED` em grupo à parte; `X-Correlation-Id` só no formato `[A-Za-z0-9._-]{8,64}`; métricas por provedor lidas do `ProviderStatus` existente; `/actuator/metrics` e `/actuator/prometheus` somem (404) sem `MW_METRICS_TOKEN`.
- Item #9: nenhuma cotação na simulação (valores em reais do usuário); risco de carteira como limite superior com cobertura declarada; `MoneyBounds` compartilhado; classes nomeadas `Impact` para manter a regra do ArchUnit contra `Simulat`; glossário escrito a partir da leitura do código, com a ligação métrica → entrada testada.
- Item #8: perguntas ancoradas nas dimensões do art. 3 da Resolução CVM 30 (sem alegar conformidade); tetos de perfil por horizonte curto, necessidade de caixa, falta de reserva e venda em pânico; metas por `AssetCategory` rotuladas como ilustrativas; respostas e carteira não são gravadas nem logadas; aderência só descreve desvio, sem dizer o que comprar ou vender.
- Item #7: sem bimestral e semestral (sem base regulatória nos emissores cobertos); pontualidade pela primeira entrega e números pela última versão; banco usa a conta 3.09; ações dos EUA sem fonte integrada; refinamento visual inicial por princípios de design ([design.md](design.md)).
- Item #6: tipo do imóvel só quando a fonte declara; percentuais de receita do HGLG11 somam 70,85% e não são apresentados como 100%; XPML11 vem de terceiros e é rotulado assim; conflito de classificação do MXRF11 (Tijolo × Papéis) é exibido; coordenada por via é "aproximada".
- Item #5: só atributos definidos pelo produto ou verificados em fonte pública entram na taxonomia (imposto e liquidez ficam de fora); `Rate` em `BigDecimal` e valores como texto exato na API; SGS por janela de datas (o modo "últimos N" traz datas futuras e ordem imprevisível); cada indicador do BCB é independente e mantém o último valor bom; FIIs sem benchmark (o IFIX tem uma barra na Yahoo); PTAX pelo Olinda, não pelo SGS 1.
- Item #4: três bolsas em ordem (Binance, Coinbase, Kraken) com failover por 451/403, falhas e silêncio; histórico de cripto pela Yahoo (a Binance REST sofre o mesmo bloqueio regional); stream suspende o polling REST por 30 s; regra de barra nova por dia local da bolsa; beta de 90 barras com benchmark alinhado por data; Dependabot ignora JDK não-LTS, Spring Boot major e Maven da distribuição do wrapper.
- PR #1: Maven Wrapper com checksum; piso JaCoCo de 70% (medido 76%); CodeQL `security-extended`; Dependabot agrupado.
- PR #3: calendário por regra conferido contra páginas oficiais (bolsas sem fonte verificada ficam marcadas, sem datas adivinhadas); brapi só para histórico (horário de cotação não confiável em feriado); Stooq desligado por padrão; Frankfurter no endpoint `.dev/v1`; feed pago com selo declarado pelo plano.
- PR #2: `System.Logger` na `application` (sem SLF4J, ponte JUL do Spring Boot); chave do cache de backtest pela versão dos dados; 503 para ativo sem dado real; fixtures reais da Yahoo nos testes; Dependabot ignora JDK não-LTS nas imagens.

## 5. Dívidas técnicas abertas

| # | Dívida | Onde | Resolver em |
|---|---|---|---|
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
| 22 | Links de Street View dependem de haver imagem no ponto (o Google decide ao abrir); a existência da imagem não foi verificada | `domain/composition/MapLinks` | sem prazo (o Google decide ao abrir) |
| 23 | Radar sem fonte para ações dos EUA e outros mercados (SEC/EDGAR não integrado); só 3 companhias e 4 FIIs; o informe trimestral e anual de FII não é acompanhado (só o prazo) | `data/relatorios` | itens #20 e #21 ou PR futuro |
| 24 | Dividend yield do Informe Mensal sem unidade documentada pela CVM (o veredito compara com o próprio histórico; a tela mostra o valor sem converter) | `domain/reports` | quando a CVM documentar |

| 25 | Pontos, faixas, tetos e metas do suitability são definições do produto, não validadas por profissional habilitado nem por dados históricos | `domain/suitability` | revisão por profissional antes de qualquer uso além do educacional |

| 27 | O risco do **simulador pré-ordem** (carteira digitada) ainda usa a média ponderada das volatilidades, limite superior, e ignora a correlação; para a carteira salva, a tela de Correlação e concentração (item #19) e o Monte Carlo (item #18) já usam a correlação real | `domain/impact` | PR futuro (levar a covariância ao simulador) |
| 28 | O conteúdo do glossário não foi revisado por profissional habilitado, e a lista de rótulos do teste de cobertura é espelhada à mão | `domain/glossary`, `GlossaryTest` | revisão antes de uso além do educacional |
| 29 | A pressão geopolítica é contagem de palavras-chave em manchetes de terceiros e só mede cinco setores; o setor da posição vem do universo do painel | `GeoRiskScorer`, `GeoTranslator` | fonte de eventos estruturada e mapa de exposição por país (PR futuro; o mapa do item #16 só cobre imóveis) |
| 30 | As caracterizações das gestoras e os textos das teses são resumos simplificados, sem revisão pelas instituições nem por profissional habilitado | `ThesisCatalog` | revisão antes de uso além do educacional |
| 32 | Controles do protótipo sem backend ficam de fora: alternância BRL/USD, modo privacidade, central de alertas, "Fixar" e "Comparar" do cockpit e as seções Técnico, Macro e Backtest em abas (o menu do usuário passou a existir no item #15); páginas sem dado real ficam desabilitadas na sidebar | `index.html` (NAV) | itens #18, #19 e Milestone 6 |
| 37 | O apoio ao IR não calcula imposto devido, DARF nem compensação de prejuízos: a base legal da compensação e do ganho de capital em FII, ETF, cripto e ativos no exterior não foi confirmada em fonte oficial (só os arts. 2º e 3º da Lei 11.033/2004 foram lidos, e o Planalto ainda anota a MP 1.303/2025 com vigência encerrada) | `domain/portfolio/TaxReport` | item #25 (sob pedido), com a base legal confirmada |
| 38 | O livro é cronológico por ativo (operação anterior à última do ativo é recusada) e não há importação em lote; os eventos de domínio são em processo e voláteis (sem outbox) | `PortfolioService`, `InProcessEventBus` | item #14 (observabilidade) e importação no Milestone 6 |
| 39 | Posições em dólar abertas antes do livro (item #12) ficam com o custo em reais "não informado" até um ajuste com câmbio | migração V3, `Position` | ajuste manual do usuário |
| 41 | O fluxo do botão do Google (GIS, ID token em popup) e a CSP com o domínio do Google foram testados só com a chave desligada e por teste de integração com chave RSA de teste; **não foi validado ao vivo com um `MW_GOOGLE_CLIENT_ID` real** | `auth.js`, `SecurityHeadersFilter` | quando houver o ID do cliente OAuth |

| 43 | Pendências da auditoria de segurança ([seguranca-checklist.md](seguranca-checklist.md)): criptografia em repouso e backups (deploy), aceite dos termos gravado com data e versão (hoje só a caixa na tela), correção do nome da conta, expurgo de contas inativas, arquivo `LICENSE` (decisão do dono do projeto), alertas de monitoramento, CAPTCHA, teste de invasão externo, `nonce` do login com Google e travas de login compartilhadas entre instâncias | `docs/seguranca-checklist.md` | item #21 (deploy) e PRs futuros |

**Resolvidas**
- Dívida 36 (item #20, Issue #53): o Testcontainers existe e a CI roda os contratos de persistência, a migração V3 e a aplicação com banco num PostgreSQL 16 em contêiner; o binário embutido (zonky) segue só como conveniência local sem Docker.
- Dívida 26 (item #17, Issue #47): o simulador pré-ordem e o tradutor geopolítico têm "Usar minha carteira salva", que preenche as linhas com as posições reais em reais.
- Dívida 9 (item #12): a persistência JDBC tem testes contra um PostgreSQL de verdade, com contratos que rodam também na versão em memória.
- Dívidas 34 e 42 (parte, Issue #43): o refresh token saiu do corpo e do `localStorage` para um cookie `HttpOnly`, `SameSite=Strict`, `Secure` sob HTTPS, e nenhum token fica no armazenamento do navegador; o que resta (`nonce` do Google e travas de login por instância) está na dívida 43.
- Dívida 40 (Issue #43): uma série do Banco Central sem valor é repetida a cada 2 minutos até aparecer (antes esperava 30), sem consumir cota quando tudo está em dia.
- Dívida 26 parcial e item #15 (Issue #26): a Minha Carteira lê a carteira e o perfil salvos; o "montador de perfil" salva o resultado do questionário na conta.
- Dívidas 3 e 4 (item #14, Issue #40): o `MarketService` tem um escritor único e publica um mapa imutável de visões, e as leituras não esperam a atualização (teste: leituras abaixo de 100 ms durante uma atualização de mais de 250 ms, que falha no código anterior); a dívida 4 (`JsonBacktestCache`) já tinha sido resolvida no PR #2.
- Dívida 35 (item #13, Issue #38): o custo em reais de posição em dólar usa o câmbio de cada compra (PTAX do Banco Central da data ou o câmbio da nota informado pelo usuário), guardado no livro de movimentações; o resultado em reais passou a existir e inclui a variação cambial.
- Dívidas 6 e 33 (item #12, Issue #36): o cálculo monetário da carteira é todo em `BigDecimal` (preço médio, custo, valorização, câmbio), com `NUMERIC` no banco; contas, sessões e perfil têm adaptadores JDBC (V2) com a rotação do refresh atômica no banco, testados contra um PostgreSQL de verdade. O motor de mercado segue em `double` por decisão do ADR 002.
- Dívida 5 (item #11, Issue #25): o `ApiRateLimitFilter` usava `getRemoteAddr()`; agora conta pelo IP real (`X-Forwarded-For` só de proxy confiável, lido da direita para a esquerda) e por usuário autenticado, com limite menor nas rotas de autenticação. Testes de integração com e sem proxy confiável.
- Dívida 31 (Issue #33): as fontes do protótipo (Cormorant Garamond, Figtree e IBM Plex Mono, SIL OFL 1.1) são servidas pelo projeto como WOFF2 do subconjunto latino em `static/fonts/`, com as licenças ao lado, sem CDN; `FontsContractTest` trava isso. As 20 imagens do protótipo que não estão em `docs/design/` seguem fora do repositório.
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

Segurança: `MW_JWT_SECRET` (segredo do JWT) e `MW_GOOGLE_CLIENT_ID` (Login com Google) são opcionais em desenvolvimento e obrigatórios na prática em produção; ver [seguranca.md](seguranca.md). Sem elas, a aplicação sobe com segredo aleatório por partida e sem Login com Google.

Formatação: `.\mvnw.cmd spotless:apply`. Relatório de cobertura: `target/site/jacoco/index.html`.

## 7. Estado da validação

| Item | Estado |
|---|---|
| `mvn verify` (compilação, 732 testes incluindo ArchUnit, Checkstyle, Spotless, JaCoCo) | Validado localmente (Windows 11, JDK 21.0.9, Maven 3.9.9) |
| GitHub Actions (CI, CodeQL, job Docker) | Validado no PR #1 (todos os checks verdes) |
| Docker / Compose | **Não validado localmente** (Docker ausente na máquina); `docker compose config` e build da imagem validados no job `docker` da CI |
| Provedores reais | Item #5: BCB (SGS e Olinda) **validado ao vivo** (7 indicadores, 6 chamadas, 0 falhas) e FIIs na Yahoo; item #4: Binance, Coinbase e Kraken **validadas ao vivo** (stream real rodando no app, 29 ativos carregados, preços com 1 s de idade); item #3: Yahoo (25 ativos), brapi (B3) e Frankfurter **validados ao vivo** com latência medida; Stooq bloqueado por verificação JS; Finnhub (REST/WS) e FRED não validados (sem chave). Detalhes em `docs/mercados.md` |
| Painel no navegador | Validado no PR #2 em navegador Chromium (desktop): dados reais, selos, estado sem dado, sem erros de console da aplicação. Larguras 1024/390 px e acessibilidade completa ficam para o Milestone 4 |
