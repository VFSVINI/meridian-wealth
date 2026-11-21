# Changelog

Todas as mudanças relevantes do projeto ficam registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o versionamento segue o [SemVer](https://semver.org/lang/pt-BR/).
Cada milestone fecha com uma tag `v0.<N>.0` e uma GitHub Release.

## [Não lançado]

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
