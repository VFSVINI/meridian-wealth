# Registros de decisão de arquitetura (ADRs)

Cada ADR descreve uma decisão estrutural: contexto, decisão, consequências e alternativas.
ADRs aceitos não são reescritos; uma mudança de rumo gera um ADR novo que substitui o anterior.

| ADR | Título | Status | Data |
|---|---|---|---|
| [001](001-arquitetura-hexagonal.md) | Arquitetura hexagonal estrita | Aceito | 2026-11-20 |
| [002](002-precisao-financeira.md) | Precisão financeira: fronteira `double` × `BigDecimal` | Aceito | 2026-11-20 |
| [003](003-frontend-sem-build.md) | Frontend sem etapa de build | Aceito | 2026-11-20 |
| [004](004-dados-reais-e-procedencia.md) | Dados 100% reais e procedência explícita | Aceito | 2026-11-20 |
| [005](005-stream-cripto-com-failover.md) | Stream de cripto com failover e anualização por √365 | Aceito | 2026-11-21 |
| [006](006-taxonomia-e-dados-oficiais-do-bcb.md) | Taxonomia multi-ativo e dados oficiais do BCB | Aceito | 2026-11-21 |
| [007](007-composicao-curada-e-geolocalizacao.md) | Composição curada e geolocalização conservadora | Aceito | 2026-11-21 |
| [008](008-radar-de-relatorios-e-veredito-mecanico.md) | Radar de relatórios e veredito mecânico | Aceito | 2026-11-21 |
| [009](009-suitability-por-questionario-e-tetos.md) | Suitability por questionário, tetos e carteira modelo | Aceito | 2026-11-21 |
| [010](010-impacto-pre-ordem-e-glossario.md) | Impacto pré-ordem e glossário ligado às métricas | Aceito | 2026-11-21 |
| [011](011-tradutor-geopolitico-e-teses-genericas.md) | Tradutor geopolítico e teses institucionais genéricas | Aceito | 2026-11-21 |
| [012](012-prototipo-visual-como-referencia.md) | Protótipo visual como referência obrigatória da interface | Aceito | 2026-11-21 |
| [013](013-autenticacao-jwt-google-e-defesas-de-borda.md) | Autenticação por JWT, Login com Google e defesas de borda | Aceito | 2026-11-21 |
| [014](014-carteira-persistida-preco-medio-e-bloqueio-otimista.md) | Carteira persistida, preço médio ponderado e bloqueio otimista | Aceito | 2026-11-21 |
| [015](015-livro-de-movimentacoes-idempotencia-e-apoio-ao-ir.md) | Livro de movimentações, idempotência e apoio ao imposto de renda | Aceito | 2026-11-21 |
| [016](016-monolito-modular-e-observabilidade.md) | Monólito modular com fronteiras verificadas e observabilidade por provedor | Aceito | 2026-11-21 |
| [017](017-interface-da-carteira-sessao-no-cliente-e-echarts-local.md) | Interface da carteira: sessão no cliente, ECharts local e Google na CSP | Aceito | 2026-11-22 |
| [018](018-mapa-sem-mapa-base-de-terceiros.md) | Mapa de ativos reais com Leaflet e sem mapa-base de terceiros | Aceito | 2026-11-22 |
| [019](019-cockpit-com-lightweight-charts-e-shopping-sem-ofertas-inventadas.md) | Cockpit com Lightweight Charts e shopping financeiro sem ofertas inventadas | Aceito | 2026-11-22 |
| [020](020-monte-carlo-reprodutivel-e-excecao-a-aleatoriedade.md) | Monte Carlo reprodutível e a exceção estreita à regra de aleatoriedade | Aceito | 2026-11-22 |
| [021](021-correlacao-par-a-par-e-celula-sem-dado.md) | Correlação par a par, célula sem dado e risco da carteira com diversificação | Aceito | 2026-11-22 |
| [022](022-testes-de-persistencia-em-conteiner-e-mutacao.md) | Persistência testada em contêiner, concorrência pela API e teste de mutação | Aceito | 2026-11-22 |
| [023](023-deploy-gratuito-hugging-face-e-neon.md) | Deploy gratuito no Hugging Face Spaces com PostgreSQL no Neon | Substituído pelo 024 | 2026-11-23 |
| [024](024-deploy-gratuito-render-e-neon.md) | Deploy gratuito no Render com PostgreSQL no Neon | Aceito | 2026-11-23 |
| [025](025-fundacao-visual-escala-contraste-e-seletor-com-busca.md) | Fundação visual: escala, contraste e seletor de ativo com busca | Aceito | 2026-11-23 |
| [026](026-hero-3d-original-com-three-js-local.md) | Hero 3D original da página inicial com three.js servido localmente | Aceito | 2026-11-23 |
| [027](027-transicoes-e-microinteracoes-sem-animar-numeros.md) | Transições e microinterações sem animar números | Aceito | 2026-11-23 |
| [028](028-kit3d-e-cockpit-em-3d.md) | Módulo 3D compartilhado e cockpit em 3D com o histórico real | Aceito | 2026-11-24 |
| [029](029-globo-3d-dos-mercados.md) | Globo 3D dos mercados com o estado real do servidor | Aceito | 2026-11-24 |
| [030](030-monte-carlo-3d-e-orbita-compartilhada.md) | Monte Carlo em 3D e câmera orbital compartilhada | Aceito | 2026-11-24 |
| [031](031-correlacao-em-3d.md) | Correlação em 3D com célula sem dado nunca como zero | Aceito | 2026-11-24 |
| [032](032-carteira-em-3d.md) | Minha Carteira em 3D: rosca de alocação sem guardar dado depois do logout | Aceito | 2026-11-24 |
| [033](033-cosmos-fundacao-visual.md) | Cosmos: o protótipo aprovado vira a fundação visual (fontes, tokens, céu vivo, shell e Início) | Aceito | 2026-11-24 |
| [034](034-catalogo-de-ativos-negociados.md) | Catálogo de ativos negociados e abertura sob demanda com sinal do motor | Aceito | 2026-11-25 |
| [035](035-composicao-de-todos-os-fiis-pela-cvm.md) | Composição e mapa de todos os FIIs com imóveis publicados na CVM | Aceito | 2026-11-25 |
| [036](036-mercados-e-ativo-no-visual-cosmos.md) | Mercados e Ativo no visual Cosmos | Aceito | 2026-11-27 |
| [037](037-academia-curso-em-modulos.md) | Academia: curso em módulos com quiz e progresso no navegador | Aceito | 2026-11-28 |
