# Protótipo visual: transcrição obrigatória para a interface

Referência da interface (ver [ADR 012](adr/012-prototipo-visual-como-referencia.md)). O protótipo foi gerado no Claude Design a
partir de [prompt-claude-design.md](prompt-claude-design.md) e aprovado pelo responsável do produto. **A interface deve segui-lo
à risca**, com a ressalva do ADR 012: dado de exemplo do protótipo vira dado real com fonte, horário e atraso, ou "não informado".

O material recebido é um conjunto de 28 imagens (PNG), sem código; as 8 principais (sistema de design, painel inicial, mercados, cockpit, estados, mobile, marca e notas de implementação) estão versionadas, comprimidas, em [design/](design/). Esta página transcreve em texto o que dá para fixar; os
valores abaixo foram lidos da página "Sistema de design" do protótipo e **devem ser conferidos contra a imagem** ao implementar
(o item #15 versiona as imagens comprimidas em `docs/design/`).

## Conteúdo do protótipo

| Imagem | O que define |
|---|---|
| Sistema de design | tokens, tipografia, selos de honestidade, controles, cartões, ícones, espaço, forma, movimento |
| Marca | wordmark, monograma, favicon |
| Shell: navbar superior e sidebar | estrutura fixa de navegação |
| 01 a 18 | Entrada e onboarding; Painel inicial; Mercados; Cockpit do ativo; Radar de relatórios; Brasil: juros e inflação; Geopolítica; Minha Carteira; Perfil de investidor; Simulador antes e depois; Mapa de imóveis; Composição; Monte Carlo e correlação; Stress test; Glossário e trilhas; Alertas e calendário; Configurações; Saúde dos dados |
| Estados de primeira classe | carregando (esqueleto), vazio, "não informado", dado desatualizado, erro |
| Mobile 390 px | versão escura e Ivory em tela estreita |
| Notas de implementação | mapeamento de gráficos para bibliotecas, movimento, marca e contrato de honestidade |

## Tokens (escuro, principal)

| Papel | Valor |
|---|---|
| `--bg` | `#0B0E13` |
| `--panel` / `--panel-2` / `--popover` | `#11151C` / `#171C25` / `#1D2330` |
| `--line` / `--line-2` | `#232A36` / `#2F3848` |
| `--text` / `--text-2` / `--text-3` | `#E9EBF0` / `#AAB2C0` / `#8E97A8` |
| `--gold` (destaque único) | `#C9A96A`, com filete metálico em gradiente fino para bordas ativas, selos e marca |
| `--up` / `--down` | `#52C7AC` (verde-azulado) / `#F2897C` (coral) |
| `--warn` / `--info` | `#E8B84F` (âmbar, atenção) / `#8FBBEA` (gelo, informação) |

## Tokens (claro, "Ivory", mesmos papéis e rigor)

`--bg #F5F1E8` · `--panel #FBF8F1` · `--panel-2` e `--popover #FFFFFF` · `--line #E2DAC8` · `--line-2 #CFC5AD` ·
`--text #1A1D24` · `--text-2 #464D5E` · `--text-3 #5E6576` · `--gold #85662A` · `--up #0C7561` · `--down #B1403A` ·
`--warn #865A06` · `--info #2A5C9C`.

## Forma, espaço e movimento

- Espaço em base de 4 px (4, 8, 12, 16, 24, 32, 48, 64); raios de 6, 8 e 10 px; filetes de 1 px, sem sombras pesadas; vidro fosco só em popovers e modais.
- Curva `cubic-bezier(.2,.7,.2,1)`; microinterações de 150 a 250 ms (padrão 200); traçado de gráfico 1000 ms uma vez por carga (não repete ao filtrar); pulso de preço 1100 ms, verde ou coral suave, sem piscar; `prefers-reduced-motion` desliga tudo e mostra o estado final.

## Tipografia

- Servidas pelo projeto em `src/main/resources/static/fonts/` (WOFF2 latino, SIL OFL 1.1, licenças ao lado), sem CDN.
- Títulos e marca: **Cormorant Garamond** (fallback Georgia), 26, 40 e 56 px.
- Texto e interface: **Figtree** (fallback Segoe UI e system-ui).
- Números: **IBM Plex Mono** tabular em todos os valores; caixa alta pequena em 11 px com espaçamento .16em para rótulos de seção.
- Formato: BRL com 2 casas e separadores pt-BR, fonte mono tabular.

## Marca

Wordmark "Meridian" em texto e "Wealth" em dourado, em Cormorant Garamond 600; monograma "M" com meridiano vertical em filete
dourado (24 e 32 px); 65 ícones de traço de 1,5 px mascarados com `currentColor`; favicon com o monograma dourado sobre `#0B0E13`.

## Shell

- **Navbar superior:** busca de ativo, página ou ação (atalho Ctrl+K), alternância BRL/USD, modo privacidade (oculta valores), indicador de estado ao vivo, central de alertas, tema claro/escuro e menu do usuário com o perfil.
- **Sidebar recolhível, em grupos:** VISÃO GERAL (Painel inicial, Mercados); ANÁLISE (Cockpit do ativo, Radar de relatórios, Brasil: juros e inflação, Geopolítica e teses); PATRIMÔNIO (Minha Carteira, Perfil e carteira modelo, Simulador pré-ordem, Mapa de ativos reais, Composição/Raio-X); RISCO (Monte Carlo, Correlação e concentração, Stress testing); APRENDER (Glossário, Central de aprendizado); CONTA (Alertas, Configurações, Saúde dos dados). Rodapé: "Dados reais, N fontes ao vivo · Ferramenta analítica e educacional" e o botão Recolher.
- **Rodapé de página:** "Ferramenta analítica e educacional. Não executa ordens e não é recomendação de investimento."

## Componentes de honestidade (contrato)

- **Selos de frescor:** Tempo real, Atrasado N min, Fechamento, Gravado, Desatualizado ("Idade 2 h 14 min; limite 15 min").
- **Selos de fonte:** Oficial, De terceiros; **selos de natureza:** projeção, exemplo (só no protótipo), POSITIVO / NEUTRO / NEGATIVO, SEM VEREDITO.
- "não informado" em itálico para qualquer valor nulo da API (nunca 0); linha de fonte: "Fonte: X · cotação em data e hora · consultado em data e hora".
- Faixa **"SIMULAÇÃO: NÃO É COTAÇÃO NEM ORDEM. Nenhuma ordem enviada."** (listrada em âmbar) em qualquer simulação.
- Esqueleto de carregamento, nunca spinner solto; toda cifra com fonte, horário do dado e atraso; backtest, Monte Carlo e cenário sempre rotulados "projeção"; gráficos com `aria-label` e tabela equivalente sob cada um.

## Mapeamento de gráficos para bibliotecas (notas do protótipo)

| Gráfico | Biblioteca | Observação |
|---|---|---|
| Candles, volume, SMA, bandas de Bollinger, zonas e níveis | Lightweight Charts | cor por papel, nunca só por cor; atualização por tick |
| Monte Carlo (leque P5 a P95), heatmap de correlação, cascata do simulador, composição (treemap ou sunburst) | ECharts | célula sem dado fica cinza, nunca zero; selo "projeção" no título |
| Mapas de imóveis e de geopolítica | Leaflet | tiles escuros, marcadores agrupados, GeoJSON por país com legenda e fonte |
| Mini-gráficos de tabela | SVG leve | sem biblioteca |

## Como cada item do roadmap usa o protótipo

| Item | Páginas do protótipo |
|---|---|
| #10 e etapa 1 do #15 (Issue #31) | Sistema de design, shell, Painel inicial, Mercados, Cockpit, Radar, Brasil, Geopolítica e teses, Perfil, Simulador, Composição e Saúde dos dados |
| #12 e #13 | Minha Carteira, livro de movimentações |
| #15 (etapa 2) | Minha Carteira, Entrada e onboarding (com "Continuar com Google"), Configurações |
| #16 | Mapa de imóveis, Composição |
| #17 | Mercados, Cockpit do ativo |
| #18 e #19 | Monte Carlo, correlação e concentração |
| Milestone 6 (sob pedido) | Stress test, Alertas e calendário |

## Referências de design fintech (indicadas pelo dono do projeto em 2026-11-23)

Fonte: o artigo "Top 10 FinTech product interface designs", da agência Goodface (atualizado em 05/03/2026; <https://goodface.agency/insight/top-10-fintech-product-interface-designs/>). **Ressalvas:** é texto de uma agência que promove os próprios serviços (um dos dez casos é trabalho dela) e é qualitativo, sem métricas de usabilidade; o artigo não trata de visualização de carteira, alocação nem acessibilidade de gráficos, então esses pontos pedem outras fontes. O resumo abaixo é uma paráfrase.

Os dez casos: Revolut, Stripe, Neverless, Robinhood, Vivid, Uniswap, Wealthsimple, Fey, Monobank e Magnetiq Bank.

Padrões que o dono quer ver aplicados, em ordem do que mais serve a uma plataforma analítica de investimentos:
- **Densidade com clareza** (Stripe, Revolut, Robinhood): dado denso, mas com hierarquia visual forte, cor por categoria e navegação por camadas; é o ponto central do redesenho das páginas (item #31).
- **Modo escuro rico para análise longa** (Fey): o escuro continua o padrão, e os gráficos usam luz e cor para contar a história do número (item #32).
- **Explicação progressiva** (Wealthsimple): sliders, tooltips e metáforas visuais no lugar de blocos de texto, o que atende à queixa de "informação jogada" (itens #31 e #36).
- **Poucas ações por tela e muito respiro** (Uniswap, Wealthsimple): menos poluição visual.
- **Movimento com função** (Revolut, Robinhood): microinterações que dão retorno, nunca enfeite; respeitam `prefers-reduced-motion`.
- **Identidade própria e confiança** (Stripe, Fey, Monobank): sobriedade premium com personalidade, sem perder a clareza.
- **Gamificação com moderação** (Monobank, Vivid): só onde ajuda a criar hábito (por exemplo, progresso até a meta no Monte Carlo), sem perder a seriedade.

Limites que continuam valendo por cima da referência: dado real com fonte, horário e atraso (ADR 004), nada de recomendação financeira e o protótipo aprovado como base visual (ADR 012). Imitar a **estrutura** e os princípios dos exemplos, nunca copiar marca, ilustração ou texto de terceiros.

### Wealthsimple como referência principal do front (pedido do dono em 2026-11-23)

O dono pediu o site wealthsimple.com/en-ca como **fonte principal** do front-end e a interação 3D da página inicial como inspiração. O que se observou na página (2026-11-23): fundo azul-marinho escuro em degradê, título editorial enorme com tipografia sem serifa geométrica, rótulo em serifa acima do título, botões em pílula (um claro e um escuro), barra de navegação flutuante e arredondada, muito espaço vazio, e um **objeto 3D dourado** (uma moeda com a marca) em destaque. O 3D deles é **mídia pré-renderizada** (vídeos em loop sobre a tela inteira, nenhum `canvas` nem WebGL na página).

Regras para aplicar sem copiar:
- Adota-se a **estrutura e os princípios**: hero escuro e calmo, título editorial grande, pouco texto, botões em pílula, navegação limpa, o objeto 3D como protagonista.
- **Não se copia** marca, moeda com o "W", vídeo, ilustração, texto nem identidade deles. O 3D do MeridianWealth é **original**, com a nossa marca (o "M" do protótipo), em **CSS 3D sem biblioteca** (a CSP só aceita script local e o projeto não tem etapa de build; baixar uma biblioteca 3D exige permissão), com inclinação pelo ponteiro e giro lento, e desligado com `prefers-reduced-motion`.
- O objeto 3D é **identidade visual, não dado**: nenhum número ou preço aparece nele. Todo dado do painel continua real, com fonte, horário e atraso (ADR 004).
- O protótipo aprovado (ADR 012) segue sendo a base das cores e dos componentes; o Wealthsimple orienta a composição, o ritmo e o tom.

