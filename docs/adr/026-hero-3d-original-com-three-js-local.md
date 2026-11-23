# ADR 026 — Hero 3D original da página inicial com three.js servido localmente

- **Status:** aceito
- **Data:** 2026-11-23
- **Issue:** #62 (item #31 do roadmap, etapa 1)

## Contexto

O dono pediu o Wealthsimple como referência principal do front e a interação 3D da página inicial. Olhando o site: o 3D deles é uma moeda dourada com a marca deles, entregue como **vídeos em loop** (nenhum `canvas` ou WebGL na página), sobre um hero escuro com título editorial e botões em pílula. Esse material é da marca deles. A CSP do projeto só aceita script do próprio servidor e não há etapa de build (ADR 003).

## Decisão

1. **Original, não cópia.** Adota-se a estrutura e os princípios (hero escuro e calmo, título grande, pílulas, objeto 3D protagonista). O objeto é um medalhão com o **"M" do MeridianWealth**, modelado em geometria (cilindro, aro, anel e o "M" extrudado nas duas faces) e iluminado por um ambiente de estúdio gerado em código; nenhuma moeda, "W", vídeo, ilustração ou texto do Wealthsimple foi usado.
2. **three.js 0.170.0**, arquivo único `three.module.min.js` (MIT, 691.648 bytes), baixado com permissão do dono, conferido contra duas origens (jsdelivr e unpkg, mesmo SHA-256) e **fixado por teste**, com a licença ao lado. Escolhida a 0.170.0 por ser a última versão de arquivo único (as seguintes dividem o núcleo em vários arquivos) e porque os builds sem módulo foram descontinuados. Carregada como **módulo ES** (`<script type="module">`), sem global.
3. **Comportamento:** inclina com o ponteiro e gira devagar; renderiza só quando o hero está visível e a aba aberta; com `prefers-reduced-motion` desenha um quadro estático; sem WebGL (ou erro ao criar o contexto) mostra o medalhão em CSS. O canvas é decorativo (`aria-hidden`); o texto do hero é DOM comum.
4. **Identidade, não dado:** nenhum número, preço ou percentual aparece dentro do 3D (teste), para não ser confundido com dado de mercado (ADR 004).

## Consequências

- O carregamento da página inicial passa a incluir cerca de 675 KB de JavaScript (a biblioteca), em módulo e sem bloquear a renderização do texto; o medalhão em CSS aparece enquanto o 3D carrega.
- Atualizar o three.js exige trocar o arquivo e o hash no teste, de propósito.
- Não foi testado em dispositivo real nem fora do Chromium do painel de pré-visualização; o consumo de bateria em celular não foi medido.

## Alternativas descartadas

- **CSS 3D puro:** funciona sem dependência, mas sem o brilho metálico (reflexos) que dá o efeito do exemplo; o dono preferiu a biblioteca.
- **Vídeo pré-renderizado (como o Wealthsimple):** arquivo pesado, sem interação e exigiria uma produção 3D fora do repositório.
- **Cópia da moeda do Wealthsimple:** é marca e material deles.
