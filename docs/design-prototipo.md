# Protótipo visual: transcrição obrigatória para a interface

Referência da interface (ver [ADR 012](adr/012-prototipo-visual-como-referencia.md)). O protótipo foi gerado no Claude Design a
partir de [prompt-claude-design.md](prompt-claude-design.md) e aprovado pelo responsável do produto. **A interface deve segui-lo
à risca**, com a ressalva do ADR 012: dado de exemplo do protótipo vira dado real com fonte, horário e atraso, ou "não informado".

O material recebido é um conjunto de 28 imagens (PNG), sem código. Esta página transcreve em texto o que dá para fixar; os
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
| #10 (este PR) | Geopolítica e teses, no card atual; o shell completo vem no #15 |
| #12 e #13 | Minha Carteira, livro de movimentações |
| #15 | Sistema de design, shell, Painel inicial, Perfil, Minha Carteira, Entrada e onboarding (com "Continuar com Google"), Configurações, estados, mobile |
| #16 | Mapa de imóveis, Composição |
| #17 | Mercados, Cockpit do ativo |
| #18 e #19 | Monte Carlo, correlação e concentração |
| Milestone 6 (sob pedido) | Stress test, Alertas e calendário |
