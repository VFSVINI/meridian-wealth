# ADR 003 — Frontend sem etapa de build

- **Status:** aceito
- **Data:** 2026-11-20
- **Issue:** #1

## Contexto

O painel herdado é um único `index.html` com HTML, CSS e JavaScript puros, servido pelo Spring Boot a partir de `src/main/resources/static/`. O Milestone 4 vai multiplicar a interface (carteira, mapa, home broker, Monte Carlo, correlação) com bibliotecas de visualização (Apache ECharts, TradingView Lightweight Charts, Leaflet).

A questão é adotar um framework com bundler (React/Vite, Angular etc.) ou continuar sem etapa de build.

## Decisão

**Manter o frontend sem etapa de build**, organizado em **ES Modules nativos**:

1. Código em `src/main/resources/static/` como HTML + CSS + módulos JavaScript (`<script type="module">`, `import` relativo), um módulo por tela ou componente.
2. **Bibliotecas por CDN com versão exata** (publicada há mais de 14 dias), com **`integrity` SRI** e `crossorigin="anonymous"`; nada de `@latest` ou faixas de versão.
3. Bibliotecas pesadas (ECharts, Leaflet, Lightweight Charts) carregadas **sob demanda**, só na tela que as usa.
4. **Fontes hospedadas localmente** (woff2 de Plus Jakarta Sans e JetBrains Mono) com fallback de sistema.
5. O shell estático é cacheado por **service worker** (PWA); dados de mercado nunca são servidos do cache como se fossem atuais.
6. Recursos modernos de CSS (`color-mix()`, `@property`, container queries, `:has()`, View Transitions) sempre com fallback.

## Consequências

- **Positivas:** um único artefato (o JAR) serve API e interface; nenhuma toolchain Node no build, no Docker ou na CI; o que está no repositório é exatamente o que roda no navegador; menos dependências transitivas e menor superfície de supply chain.
- **Negativas:** sem TypeScript, tree-shaking ou minificação; a organização depende de disciplina de módulos; dependência de CDNs de terceiros em tempo de execução.
- **Riscos e mitigações:**
  - CDN fora do ar ou comprometida: o SRI bloqueia código adulterado, a versão fixa evita mudanças silenciosas e a CSP do PR #11 restringe as origens de script.
  - Crescimento desorganizado: um módulo por tela, utilitários compartilhados (`format.js`, `api.js`) e JSDoc para tipos.
  - Ausência de testes de UI: validação por captura com navegador headless quando disponível.

## Alternativas consideradas

- **React/Vue + Vite:** ecossistema e tipagem melhores, mas exige Node na CI e no Docker, um segundo pipeline e muito mais dependências. O ganho não compensa para um painel de leitura de dados com poucas interações de formulário.
- **Thymeleaf (renderização no servidor):** acopla a interface ao ciclo de requisição e combina mal com stream em tempo real e gráficos ricos no cliente.
- **Bibliotecas copiadas para o repositório (vendoring):** elimina a CDN, mas engorda o repositório e dificulta atualizar versões; pode ser reavaliado para o modo offline do PWA.
