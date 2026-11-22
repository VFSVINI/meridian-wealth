# Changelog

Todas as mudanças relevantes do projeto ficam registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o versionamento segue o [SemVer](https://semver.org/lang/pt-BR/).
Cada milestone fecha com uma tag `v0.<N>.0` e uma GitHub Release.

## [Não lançado]

### Adicionado (item #12 do roadmap)
- **Carteira persistida** sob `/api/me/portfolio` (login): abrir posição, compra, venda, substituição manual, exclusão e listagem valorizada. Multi-ativo: ativo do painel por símbolo, ativo sem cotação (Tesouro, LCI, CRI, fundos, cripto guardada) por tipo e rótulo; até 200 posições por carteira.
- **Preço médio ponderado** exato em `BigDecimal` (compra: `(q·pm + q'·p) / (q + q')` com 6 casas HALF_EVEN; venda mantém o preço médio e zerar fecha a posição), com limites de magnitude contra notação científica.
- **`@Version` (bloqueio otimista)**: toda escrita é condicionada à versão lida; escritas concorrentes sobre a mesma versão resultam em exatamente uma vencedora e um 409 `VERSION_CONFLICT` (testado com 8 threads no banco e 10 requisições paralelas na API: nenhuma compra aceita se perde).
- **BRL e USD**: valorização com cotação real (fonte, horário e selo de frescor) e **PTAX do Banco Central** (data e fonte); sem cotação a posição é avaliada pelo custo e o resultado fica "não informado"; sem PTAX o valor em reais de posição em dólar fica "não informado". Ativo cotado em outra moeda é recusado com explicação.
- **Migração `V2__users_and_portfolio.sql`** (`users`, `refresh_tokens`, `positions`) com `NUMERIC`, `CHECK`, únicos, chaves estrangeiras em cascata e índices; adaptadores JDBC de `UserStore`, `RefreshTokenStore` e `PositionStore` (a rotação do refresh é atômica no banco: um `UPDATE ... RETURNING` consome o token), escolhidos por `MW_PERSISTENCE`; o perfil de investidor passa a persistir. Os adaptadores em memória seguem para a execução sem Docker.
- **Testes contra PostgreSQL de verdade** (binário embutido, sem Docker): um contrato por porta roda contra a versão em memória e a JDBC; a aplicação inteira sobe com a persistência ligada e os cenários da carteira valem nos dois modos.
- O valor em reais arredonda **uma única vez**, a partir do valor exato (quantidade x preço x câmbio); arredondar em dólar e converter errava centavos (achado do code review: US$ 1.234,565 a R$ 5,00 dava R$ 6.172,80 em vez de R$ 6.172,82).
- Entrada de glossário "Preço médio ponderado"; ADR 014 e `docs/carteira.md`. Fecha as dívidas 6 e 33.

### Adicionado (item #11 do roadmap)
- Autenticação própria: cadastro e login por senha (BCrypt, política de senha no estilo NIST, trava de tentativas por e-mail e por IP), access token JWT HS256 de 15 minutos e refresh token opaco de 7 dias com **rotação e detecção de reuso** (token reusado derruba a família inteira; troca atômica, testada sob concorrência), logout e `GET /api/auth/config`.
- **Login com Google** (`POST /api/auth/google`): o servidor valida o ID token (assinatura RS256 pelas chaves públicas do Google, `iss`, `aud` igual a `MW_GOOGLE_CLIENT_ID`, `exp`, `sub`, e-mail e `email_verified`) e emite o mesmo JWT interno e refresh token; provisionamento automático da conta; conflito explícito (409) quando o e-mail já existe em outro provedor, **sem vincular em silêncio**; desligado e com aviso claro quando não há `MW_GOOGLE_CLIENT_ID` (a aplicação sobe e os testes passam sem nenhuma variável do Google).
- **Modo demo** (`POST /api/auth/demo`): sessão efêmera de 60 minutos, sem cadastro nem refresh, com limite de sessões.
- Rotas do usuário sob `/api/me` (conta e perfil de investidor), protegidas pelo filtro de autenticação e com **isolamento entre usuários por construção** (o dono vem do token, nunca de parâmetro) e `IsolationGuard`.
- **Rate limit por usuário e por IP real** (dívida 5 resolvida): `X-Forwarded-For` só vale vindo de proxy listado em `MW_TRUSTED_PROXIES`, lido da direita para a esquerda; limite menor nas rotas `/api/auth/*`; resposta 429 com `Retry-After`.
- **CSP restritiva** (sem script nem estilo embutidos: a interface passou a ser servida em `index.html`, `app.css`, `app.js` e `theme.js`), `nosniff`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, HSTS atrás de HTTPS, **CORS** só para origens exatas configuradas (sem curinga) e WebSocket do stream limitado à própria origem.
- **Corpo de requisição limitado a 64 KB** em `POST`/`PUT` de `/api/**` (413 antes de ler; fluxo limitado quando não há `Content-Length`) e login que recusa de imediato senha acima de 72 bytes: antes, as rotas públicas de autenticação alocavam dezenas de megabytes por requisição de quem não fez login (achado do code review; 6 requisições de 15 MB em paralelo passaram de +140 MB de memória para nenhuma alocação relevante).
- ADR 013, `docs/seguranca.md` (com o passo a passo do Google Cloud Console para `localhost` e Hugging Face Spaces), `.env.example`, `README.md` e `SECURITY.md` atualizados; nenhum segredo no repositório.

### Adicionado (dívida 31, Issue #33)
- Fontes do protótipo servidas pelo próprio projeto: Cormorant Garamond, Figtree e IBM Plex Mono (SIL OFL 1.1) em WOFF2 do subconjunto latino, com `font-display: swap`, as licenças ao lado dos arquivos e nenhuma requisição a domínio externo; `FontsContractTest` verifica arquivos, assinatura WOFF2, licenças e ausência de CDN.

### Adicionado (etapa 1 do item #15, Issue #31)
- Interface reescrita sobre o protótipo aprovado: tokens do sistema de design nos temas escuro e Ivory (com alternância e preferência lembrada), shell com navbar (marca, busca rápida Ctrl+K, estado ao vivo, tema) e sidebar recolhível por grupos, navegação por rota (`#/pagina`), gaveta do menu no celular, foco visível e `prefers-reduced-motion`.
- Páginas: Painel inicial (sinais do dia, maiores movimentos e notícias de risco, só com dado real, frescor e fonte), Mercados, Cockpit do ativo, Radar de relatórios, Brasil, Geopolítica e teses, Perfil, Simulador, Composição e Saúde dos dados. Páginas sem backend ficam desabilitadas e indicam o item do roadmap que as entrega.
- Faixa de simulação listrada, selos de frescor e veredito em formato de pílula e gráfico de preço com as cores do tema.
- `ShellContractTest`: toda entrada ativa tem página, páginas sem backend não existem, os dois temas definem os mesmos papéis de cor e nenhum dado de exemplo do protótipo entrou na interface.
- Oito imagens principais do protótipo versionadas, comprimidas, em `docs/design/`.

## [0.2.0] - 2026-11-21

Milestone 2 — Inteligência Institucional, Composição & Relatórios (itens #6 a #10).

### Adicionado (item #10 do roadmap)
- Tradutor geopolítico de carteira (`POST /api/thesis/lens`): cruza a pressão que as manchetes reais do radar macro exercem sobre cada setor com o peso do setor na carteira informada (peso, pressão, contribuição em p.p., manchetes de origem), com limites declarados e sem dado inventado quando o macro está indisponível.
- Cinco teses institucionais genéricas (`GET /api/thesis`): equilíbrio entre cenários, megatendências, sinais estatísticos e liquidez, macro de juros e câmbio e valor de longo prazo, cada uma com premissas, grupos sensíveis e o que a invalidaria. Nenhuma posição real é atribuída a nenhuma gestora, e o aviso de ilustrativo aparece na API e na tela.
- Card "Tradutor geopolítico e teses" no painel; entradas "Pressão das manchetes" e "Tese institucional" no glossário.
- ADR 011 (tradutor e teses) e ADR 012 (protótipo visual como referência obrigatória), `docs/teses.md` e `docs/design-prototipo.md` com a transcrição dos tokens e do shell do protótipo aprovado, e o prompt do Claude Design em `docs/prompt-claude-design.md`.

### Adicionado (item #9 do roadmap)
- Simulador de impacto pré-ordem (`POST /api/impact/pre-order`): efeito de uma compra ou venda hipotética na concentração (maior posição, HHI, posições efetivas), no risco (volatilidade ponderada com cobertura declarada, dado real) e na aderência ao perfil, com achados descritivos. Rotulado como simulação, sem cotação, sem rota de ordem; a plataforma não executa ordens.
- Glossário didático (`GET /api/glossary`): 60 entradas com "o que é", "como ler" e "quando se preocupar", escritas a partir do que o código calcula (o Sharpe do painel não desconta a taxa livre de risco; a expectativa usa uma probabilidade derivada do score).
- Cada métrica do painel liga a uma entrada do glossário (botão "?" e diálogo com busca); teste garante que todo rótulo do painel, inclusive os das séries do BCB, resolve para uma entrada.
- `MoneyBounds`: limite de magnitude de valores monetários compartilhado entre aderência e simulador.
- ADR 010 e `docs/simulador-e-glossario.md`.

### Adicionado (item #8 do roadmap)
- Motor de suitability: questionário de 9 perguntas cobrindo as dimensões do art. 3 da Resolução CVM 30 (objetivos, situação financeira e conhecimento), nota de 0 a 100 e perfil CONSERVADOR, MODERADO, ARROJADO ou AGRESSIVO, com tetos de perfil explicados (horizonte curto, necessidade de caixa, sem reserva, venda em pânico).
- Carteira modelo por perfil com metas por classe de ativo (soma 100, testada), rotuladas como ilustrativas.
- Aderência da carteira ao perfil: peso e desvio por classe, nota, faixa de ±5 pontos percentuais e radar; descreve o desvio sem dizer o que comprar ou vender.
- `GET /api/suitability/questionnaire`, `POST /api/suitability/evaluate`, `GET /api/suitability/model-portfolios` e `POST /api/suitability/adherence`; card "Perfil e carteira modelo" no painel. Respostas e carteira não são gravadas nem logadas.
- ADR 009 e `docs/suitability.md`, com o aviso de que o questionário não substitui a análise de um profissional habilitado.

### Corrigido (item #8)
- O índice de ADRs (`docs/adr/README.md`) não listava o ADR 008 do item #7.

### Adicionado (item #7 do roadmap)
- Radar de relatórios com dados abertos da CVM: ITR e DFP de PETR4, VALE3 e ITUB4 (receita e resultado líquido contra o mesmo período do ano anterior) e Informe Mensal de HGLG11, KNRI11, XPML11 e MXRF11 (dividend yield e valor patrimonial da cota).
- Veredito POSITIVO, NEUTRO ou NEGATIVO calculado no domínio, com os números, a base, o limiar e a regra em cada sinal; prejuízo nunca é POSITIVO e, sem base de comparação, não há veredito.
- Calendário de prazos pela regra da CVM (ITR 45 dias, DFP 3 meses, informe mensal de FII 15 dias), com a data de entrega e a pontualidade pela primeira entrega. Bimestral e semestral não foram criados por não terem base regulatória.
- `GET /api/reports` e `GET /api/reports/{symbol}`; card "Radar de relatórios" no painel; script `scripts/curadoria/gerar_relatorios.py` com testes e recortes reais da CVM como fixtures.
- Sinal de dado desatualizado (`DataAge`) na composição e nos relatórios (dívida 19 do item #6).
- Refinamento visual por princípios de design (paleta enxuta, serifa nos títulos, espaço, layout móvel) e `docs/design.md`.
- ADR 008, `docs/relatorios.md`; Login com Google incorporado ao escopo dos itens #11 e #15 (`docs/PROJETO.md`, seção 2.4).

### Adicionado (item #6 do roadmap)
- Raio-X de composição de fundos imobiliários reais: imóveis com área, vacância, setores dos inquilinos e localização (HGLG11, KNRI11 e XPML11) e alocação por classe de ativo (MXRF11), cada dado com fonte, tipo (oficial ou terceiros), data dos dados e data da consulta. O que a fonte não publica aparece como "não informado".
- Geolocalização conservadora: coordenada só quando o OpenStreetMap concorda com cidade, UF, CEP, via e número (ou km em rodovia); posição por via é rotulada aproximada; sem coordenada, o motivo é mostrado. 32 dos 72 imóveis listados têm coordenada. Links diretos para o Google Maps em Satélite e Street View.
- `GET /api/composition`, `/api/composition/{symbol}` e `/api/composition/geo`; card "Raio-X de composição" no painel.
- Script reproduzível de curadoria (`scripts/curadoria`) com testes, `docs/composicao.md` e ADR 007.
- Carregador estrito: dado de composição inválido derruba a partida em vez de ser servido.

## [0.1.0] - 2026-11-21

Milestone 1: Fundação Arquitetural & Mercados Globais.

### Adicionado (item #5 do roadmap)
- Taxonomia multi-ativo com 22 tipos (`AssetType`): ação, ETF, FII, três tipos de fundo, os cinco títulos do Tesouro Direto, LCI, LCA, CRI, CRA, nota comercial, nota promissória, cripto, câmbio, commodity e índice de referência. Só entram atributos definidos pelo produto ou verificados em fonte pública (proteção do FGC e nomes dos produtos do Tesouro); `docs/taxonomia.md` lista também o que não foi verificado.
- `Rate` em `BigDecimal` (juros compostos, percentual de uma taxa, arredondamento `HALF_EVEN`) e o catálogo de indicadores do BCB.
- Selic (meta), CDI (diário e anualizado), IPCA (mensal e em 12 meses) e PTAX (compra e venda) do Banco Central, com séries e contratos confirmados contra a API real: janela de datas no SGS, PTAX pelo Olinda, valores exatos como texto, último valor bom mantido em falha, defasagem por periodicidade e `MW_BCB_ENABLED`.
- `GET /api/taxonomy` e `GET /api/brazil/rates`; card "Juros e inflação (Brasil)" no painel.
- Quatro FIIs reais (HGLG11, KNRI11, XPML11 e MXRF11) no universo, que passa a ter 33 instrumentos.
- ADR 006 e fixtures reais do BCB e do HGLG11.

### Corrigido (item #5)
- A Selic meta, preenchida para a frente pelo SGS, nunca ficava desatualizada e exibia a data de hoje como se fosse de publicação: cada observação agora guarda a última confirmação, qualquer série fica desatualizada após 3 horas sem confirmação e a tela diz "vigente em".
- A atualização dos indicadores do BCB passou a ser exclusiva (rodadas simultâneas disputavam a cota e o retrato).
- O Jackson apagava zeros à direita dos decimais do BCB (o PTAX saía `5.8889` em vez de `5.88890`).
- A nota promissória deixou de ter forma de renda presumida.
- Os PRs do Dependabot incompatíveis com o projeto (Temurin 26, Spring Boot 4 e Maven 3.10) foram fechados.

### Adicionado (item #4 do roadmap)
- Motor cripto 24/7: BTC, ETH, SOL e XRP com preço por WebSocket (Binance, com failover automático para Coinbase Exchange e Kraken), isolando a bolsa por 30 min em bloqueio regional (HTTP 451 ou 403) e por 5 min após 3 falhas seguidas, derrubando conexão calada por 20 s e voltando à bolsa preferida sem lacuna.
- Volatilidade, Sharpe e backtest anualizados por √365 para cripto (√252 para os demais) e beta de 90 barras contra o benchmark do ativo (BTC nas altcoins), com benchmark alinhado por data.
- `Exchange.CRYPTO` (24/7), barras em UTC e regra de barra nova pelo dia local da bolsa.
- `ProviderStatus.detail` e status do stream (bolsa em uso e isoladas); painel com região Cripto, beta e "stream ao vivo".
- ADR 005 e fixtures reais de Binance, Coinbase, Kraken, BTC e ETH.

### Corrigido (item #4)
- Stream de cripto: o backoff só zera no primeiro preço válido e o socket é fechado quando a bolsa devolve erro (antes, uma bolsa que abria e caía era reconectada a cada segundo, deixando conexões abertas e sem failover).
- Barra nova abria às 23:30 UTC do mesmo dia para barras que começam à meia-noite UTC (corte fixo de 23 h).
- Dependabot ignora JDK não-LTS, majors do Spring Boot e o Maven da distribuição do wrapper.

### Adicionado (PR #3)
- Bolsas globais: 25 ativos de EUA, B3, Europa, Ásia, Oceania, Oriente Médio/África e mercado global, com bolsa, moeda de cotação e benchmark por ativo.
- Calendário de pregão por bolsa (`Exchange`, `MarketHours.status`) com fuso, sessões, feriados oficiais de NYSE, B3 e Xetra conferidos, fechamento antecipado e abertura atrasada; endpoint `GET /api/markets`.
- B3 via brapi.dev (`MW_BRAPI_TOKEN`), WebSocket de negócios da Finnhub e adapter plugável de feed pago (Twelve Data, desligado).
- Campo `stale` por cotação; `docs/mercados.md` com latência medida por fonte.

### Alterado (PR #3)
- Frankfurter no endpoint canônico `api.frankfurter.dev/v1`; Stooq desligado por padrão (`MW_STOOQ_ENABLED`).

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
- Carga de históricos aguarda o próximo token do rate limiter (até 20 s) em vez de descartar a requisição; rate limit local passa a ser neutro para o circuit breaker.
- Camada `application` sem Spring, Jackson ou SLF4J; `web` sem dependência de `infrastructure`.
- Chave do cache de backtest pela versão dos dados (antes expirava a cada 30 s).
- `/api/status` expõe `channels` (WebSocket e SSE) no lugar de `wsClients`/`sseClients` e não traz mais `dataMode`/`flow`.
- Rebranding de ChronosQuant (`dev.chronosquant`) para MeridianWealth (`dev.meridianwealth`): classe principal `MeridianWealthApplication`, artefato `meridian-wealth`, imagem Docker, textos da UI e variáveis `CQ_*` → `MW_*`.
- Prefixo de propriedades `chronosquant.*` → `meridianwealth.*`.

### Base herdada
- Importada a base inicial ChronosQuant 0.2.0 (motor quantitativo, provedores com failover, resiliência própria, stream WebSocket/SSE e painel). Primeiro `mvn verify` executado neste PR: 45 testes, todos verdes.
