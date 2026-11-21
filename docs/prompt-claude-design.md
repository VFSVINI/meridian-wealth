# Prompt para o Claude Design: protótipo do MeridianWealth

Como usar: copie tudo entre as linhas `INÍCIO DO PROMPT` e `FIM DO PROMPT`, cole no Claude Design e, se puder, anexe o
`src/main/resources/static/index.html` (a interface atual) e capturas de tela dela como referência de conteúdo (não de estética).
Peça o protótipo por etapas se a ferramenta limitar o tamanho da resposta: primeiro o sistema de design e o shell (navbar,
sidebar, grade), depois as páginas na ordem da seção 6.

=== INÍCIO DO PROMPT ===

# MISSÃO

Projete e prototipe, em alta fidelidade e navegável, o site do **MeridianWealth**: uma plataforma institucional de gestão de
patrimônio multi-ativo, com inteligência quantitativa e geolocalização de ativos reais. Quero o **site mais bonito, mais
sofisticado e mais profissional já desenhado para este tipo de proposta**: pense no que um banco privado de primeira linha, uma
mesa de operações moderna e um produto de design premiado construiriam juntos. Estética **"Private Banking Dark Luxury"**.

Entregue um protótipo clicável (várias páginas, navegação real, estados, interações e gráficos dinâmicos), mais o sistema de design
que o sustenta. Tudo em **português do Brasil**.

# 1. O QUE É A PLATAFORMA (visão sistêmica)

O MeridianWealth reúne, em um só lugar: mercados globais em tempo real, análise quantitativa de timing e risco, dados oficiais do
Brasil (juros e inflação), relatórios e balanços com veredito explicado, composição e localização de imóveis de fundos
imobiliários, perfil de investidor com carteira modelo, simulação do efeito de uma ordem antes de qualquer decisão e um
glossário que explica cada métrica. O usuário-alvo é um investidor sério (família de alto patrimônio, assessor, analista) que
quer **clareza, rigor e confiança**, não gamificação.

## Princípios inegociáveis do produto (o design precisa tornar isso visível)

1. **Dados 100% reais e procedência explícita.** Nenhum preço é simulado. Toda cotação mostra **fonte, horário e atraso**. Todo
   dado curado mostra **fonte, data dos dados e data da consulta**. Onde a fonte não publica algo, a tela diz **"não informado"**
   (nunca um zero, um traço ou um palpite).
2. **Projeção é projeção.** Backtests, Monte Carlo e cenários levam o selo **"projeção"** e nunca se parecem com cotação.
3. **Ferramenta analítica e educacional.** A plataforma **não executa ordens reais** e **não é recomendação financeira**. O aviso
   é permanente, discreto e elegante (rodapé e dentro de cada módulo analítico).
4. **Mostrar a regra.** Todo veredito, nota ou sinal exibe os números e a regra que o geraram, em camadas (resumo primeiro,
   detalhe ao clicar).
5. **Sem pegadinhas.** Estados vazios, desatualizados, em erro e sem dado são cidadãos de primeira classe do design.
6. **Dinheiro é exato.** Valores em reais com 2 casas, separador brasileiro, fonte monoespaçada tabular, sem arredondamentos
   enganosos.

## Dados e módulos que já existem (o protótipo deve refletir fielmente)

**A. Mercados globais.** 33 instrumentos: ações dos EUA e do Brasil, ETFs, FIIs, cripto (BTC, ETH, SOL, XRP), câmbio, commodities
e índices. Cada cotação tem: preço, variação, **selo de frescor** (Tempo real, Atrasado com os minutos de atraso, Fechamento,
Gravado), fonte (Yahoo, brapi, Binance, Coinbase, Kraken, Frankfurter...), horário da cotação e indicador de "desatualizado".
Faixa de **horário de pregão** por bolsa (NYSE, B3, Xetra, LSE, Euronext, Tóquio, Hong Kong, ASX, Tadawul, JSE, FX, Cripto 24/7),
com aberto/fechado e o motivo (feriado, fim de semana). Atualização por WebSocket (o preço pisca de leve ao mudar).

**B. Cockpit do ativo.** Gráfico de candles com bandas de Bollinger, zonas de entrada e de realização, alvos 1 e 2 e linha de
invalidação. **Score de timing** de -100 a +100 com sinal (Janela de compra, Acumular, Manter tendência, Realizar lucro, Reduzir
ou sair, Aguardar), fase (Acumulação, Tendência ativa, Esticado, Cautela), regime (Alta, Baixa, Lateral, Estresse), confiança,
classe de risco (Baixo, Moderado, Alto, Extremo) e exposição sugerida. Métricas: Z-score, percentil, RSI, MACD, volatilidade
anualizada, max drawdown, Sharpe, VaR 95%, ATR, valor justo (SMA50), upside, downside, risco/retorno, expectativa, força
relativa, volume relativo, beta. Lista "Por que este sinal" (fatores com direção). Histórico de sinais. Microeconomia (setor,
benchmark, fundamentos). Radar macro e geopolítico (VIX, índice do dólar, juros de 10 anos, risco geopolítico, manchetes com
risco, impacto por setor). **Backtest** (rotulado projeção): estratégia contra comprar e segurar, operações, acerto, drawdown.

**C. Juros e inflação do Brasil (dado oficial do Banco Central).** Meta Selic ("vigente em"), CDI diário e anualizado, IPCA mensal
e em 12 meses, PTAX compra e venda. Cada um com data de referência, fonte e selo de desatualizado.

**D. Raio-X de composição e mapa.** Fundos imobiliários (HGLG11, KNRI11, XPML11, MXRF11): lista de imóveis com área (m²),
participação na área, vacância, setores dos inquilinos, cidade/UF, tipo (galpão logístico, edifício comercial), **coordenadas**
com **precisão** (endereço localizado, ou posição aproximada por via) e links para **Google Maps Satélite** e **Street View**;
imóveis sem coordenada mostram o **motivo** ("nenhum resultado bateu com cidade, estado, CEP e rua publicados"). Fundo de papel
(MXRF11) mostra a alocação por classe (CRI, FII, ações etc.). Selo de fonte **oficial** ou **de terceiros**, data dos dados, avisos
do que não consta, atribuição ao OpenStreetMap.

**E. Radar de relatórios (dados abertos da CVM).** PETR4, VALE3, ITUB4 e quatro FIIs. Para cada relatório entregue (1T, 2T, 3T,
exercício anual, informe mensal): **veredito POSITIVO, NEUTRO ou NEGATIVO** com os sinais (receita, resultado líquido, dividend
yield, valor patrimonial da cota), a base de comparação, o limiar e a frase que explica; **calendário de prazos** regulatórios com
status (Entregue dentro do prazo, Prazo em aberto, Sem registro, Reentrega com pontualidade não verificável).
Exemplo real: PETR4 3T2026: receita +32,40% e resultado líquido +21,12% sobre o 3T2025, limiar de 5%, veredito POSITIVO.

**F. Perfil de investidor (suitability) e carteira modelo.** Questionário de 9 perguntas em 3 dimensões (Objetivos, Situação
financeira, Conhecimento), nota de 0 a 100, perfis CONSERVADOR, MODERADO, ARROJADO e AGRESSIVO, **tetos** explicados ("Sem
reserva de emergência... Teto: MODERADO"). Carteira modelo por perfil em 6 classes (Renda fixa pública, Renda fixa privada,
Fundos, Renda variável, Criptoativos, Câmbio e commodities). **Aderência** da carteira ao perfil: peso e desvio por classe, nota
de 0 a 100, faixa de ±5 pontos percentuais e **radar** (meta contra carteira).

**G. Simulador de impacto pré-ordem.** O usuário monta a carteira (símbolos ou tipos, em reais) e uma compra ou venda
hipotética. Vê **antes e depois**: maior posição, HHI, posições efetivas, volatilidade ponderada (com a **cobertura** do dado real)
e aderência ao perfil; achados em níveis Informação e Atenção. Rótulo permanente **"SIMULAÇÃO: não é cotação nem ordem"**.

**H. Glossário.** 60 entradas em 9 grupos (O que é, Como ler, Quando se preocupar). Cada métrica do painel tem um botão "?" que
abre a entrada; há busca e entradas relacionadas.

## O que vem a seguir no roadmap (projete as telas já agora)

- **Tradutor geopolítico de carteira e teses institucionais** (genéricas, sem posições reais): eventos do mundo traduzidos em
  efeito por classe e por setor.
- **Conta e segurança:** login por senha e **"Continuar com Google"** (botão no padrão visual do Google dentro do tema escuro; se o
  provedor não estiver configurado, o botão aparece **desabilitado com aviso claro**), modo demonstração.
- **Minha Carteira:** posições multi-ativo, preço médio ponderado, BRL e USD, livro de movimentações, imposto de renda.
- **Mapa interativo de ativos reais:** tiles escuros, satélite e Street View.
- **Shopping financeiro multi-classe** e **cockpit de home broker analítico** (sem enviar ordens reais; só simulação) com paleta
  de comandos.
- **Risco quantitativo:** cone patrimonial de Monte Carlo (projeção), matriz de correlação e heatmap de concentração.
- **Extensões premium:** central de alertas, rebalanceamento assistido, calendário de renda passiva, apuração de impostos,
  backtest avançado, stress testing, research studio, importação e reconciliação.
- **Saúde dos dados e observabilidade:** estado de cada provedor (Yahoo, brapi, Binance, BCB...).

# 2. DIREÇÃO VISUAL: "PRIVATE BANKING DARK LUXURY"

Referências de sensação (não copie nenhuma): um cartão metálico escuro, o salão de um banco privado suíço, um relógio de alta
relojoaria, um terminal de dados moderno. **Sofisticação silenciosa**: menos ornamento, mais precisão, hierarquia e espaço.

- **Fundo e superfícies:** grafite quase preto com leve temperatura azulada, em camadas de elevação (fundo, painel, painel
  elevado, popover). Pontos de luz sutis (gradiente radial muito discreto, vidro fosco apenas em popovers). Sem sombras pesadas.
- **Cor:** paleta enxuta: neutros grafite, **um único dourado de destaque** (champanhe envelhecido, algo próximo de `#C9A96A`),
  **verde-azulado** para alta e **coral** para queda (só para direção de preço e veredito), **âmbar** para atenção e **gelo azul**
  para informação e links. Gradientes metálicos finos no dourado (filetes, selos, bordas ativas). Garanta contraste AA ou melhor.
- **Tipografia:** serifa elegante e de caráter para marca e títulos (proponha uma fonte premium com fallback Georgia),
  sans-serif humanista limpa para texto e interface, **monoespaçada tabular** para todos os números. Escala tipográfica clara,
  espaçamento de letras generoso nos títulos em caixa alta pequenos.
- **Espaço:** escala de 4 px, muito respiro, cartões que respiram. **Menos é mais**: cada tela tem um herói claro e o resto é
  revelação progressiva (resumo, depois detalhe ao clicar ou expandir).
- **Forma:** raios discretos (6 a 10 px), filetes de 1 px, ícones de traço fino consistentes (estilo linear de 1,5 px).
- **Movimento:** microinterações curtas e suaves (150 a 250 ms), contadores que rolam, gráficos que desenham ao entrar, pulso
  discreto em preço que muda. Respeite `prefers-reduced-motion`. Nada de animação chamativa.
- **Modo claro opcional** ("Ivory"), mantendo o dourado e o rigor, além do escuro principal.

# 3. ESTRUTURA DE NAVEGAÇÃO (shell do site)

## Navbar superior (fixa, 64 px)
Marca (logotipo tipográfico + monograma), **busca global** com atalho `Ctrl+K` que abre a **paleta de comandos** (buscar ativo,
ir para página, abrir glossário, alternar tema, "simular compra de..."), seletor de **moeda de exibição (BRL / USD)**, **modo
privacidade** (oculta valores), indicador de **conexão ao vivo** (conectando, ao vivo, reconectando) e **resumo de bolsas**
(pontinhos aberto/fechado), botão **Glossário**, sino de **alertas**, alternador de tema e **menu do usuário** (avatar de
Google, perfil de investidor atual, sair).

## Sidebar esquerda (colapsável: 248 px expandida, 72 px só com ícones)
Ícones de traço fino, rótulo, tooltip no estado colapsado, indicador dourado de página ativa, grupos com títulos pequenos em
caixa alta, **badge** de novidades. Rodapé da sidebar com o **status dos dados** (ex.: "Dados reais, 3 fontes ao vivo") e o aviso
"Ferramenta analítica e educacional".

Grupos e páginas:
- **VISÃO GERAL:** Painel inicial; Mercados.
- **ANÁLISE:** Cockpit do ativo; Radar de relatórios; Brasil (juros e inflação); Geopolítica e teses.
- **PATRIMÔNIO:** Minha Carteira; Perfil e carteira modelo; Simulador pré-ordem; Mapa de ativos reais; Composição (Raio-X).
- **RISCO:** Monte Carlo; Correlação e concentração; Stress testing.
- **APRENDER:** Glossário; Central de aprendizado.
- **CONTA:** Alertas; Configurações; Saúde dos dados.

## Elementos de navegação adicionais
- **Abas e chips de filtragem** em cada página (por região, classe de ativo, sinal, risco, setor, periodicidade).
- **Breadcrumbs** discretos e **abas internas** (ex.: no cockpit: Visão, Técnico, Macro, Backtest, Relatórios, Composição).
- **Painel lateral direito (inspetor/drawer)** para detalhes sem perder o contexto (clicar num ativo, num imóvel, num sinal).
- **Barra de ações flutuante** contextual e **atalhos de teclado** (mostrar lista com `?`).
- Em celular: **barra inferior** com 5 ícones principais e a sidebar vira gaveta.

# 4. PADRÕES DE INTERFACE QUE EXPRESSAM A HONESTIDADE DO PRODUTO

Projete e reutilize estes componentes em todo o site:

1. **Selo de frescor** (Tempo real, Atrasado Nmin, Fechamento, Gravado) com cor e ícone próprios.
2. **Selo de fonte** (Oficial, De terceiros) + **data dos dados** + **consultado em**.
3. **Gaveta "De onde vem este número?"**: clicar em qualquer valor abre a procedência (fonte, link, horário, hash do arquivo,
   fórmula, janela de cálculo).
4. **Estado "não informado"** com motivo e ícone próprio (nunca vazio, nunca zero).
5. **Selo "projeção"** (tracejado âmbar) para backtest, Monte Carlo e cenários.
6. **Banner "SIMULAÇÃO: não é cotação nem ordem"** fixo nas telas de simulação.
7. **Faixa de dado desatualizado** (âmbar) com a idade do dado e o limite.
8. **Botão "?"** que abre o glossário contextual (popover curto com "O que é" e link "Ver completo").
9. **Cartão de veredito** (POSITIVO, NEUTRO, NEGATIVO ou SEM VEREDITO) com sinais, base, limiar e frase de regra, expansível.
10. **Rodapé legal** elegante: "Ferramenta analítica e educacional. Não executa ordens e não é recomendação de investimento."
11. **Estados:** carregando (esqueleto, nunca spinner solto), vazio, sem dado real, erro de provedor, offline, sem permissão.

# 5. GRÁFICOS DINÂMICOS (alto padrão, interativos, acessíveis)

Bibliotecas-alvo para a implementação: **Apache ECharts** (gráficos analíticos), **TradingView Lightweight Charts** (candles do
cockpit) e **Leaflet** (mapa). Desenhe de modo que seja viável implementar com elas. Todos com tooltip rico, crosshair, legenda
interativa, zoom e seleção de período, exportar imagem e **alternativa em tabela** para acessibilidade.

- **Candles + volume** com Bollinger, SMA, zonas de entrada e realização sombreadas, linhas de alvo 1, alvo 2 e invalidação, marcadores
  de sinais históricos e comparação com benchmark (normalizado em base 100).
- **Sparklines** em toda lista; **mini-barras de score**; **medidores** (gauge) de risco e confiança.
- **Treemap** de carteira por classe e ativo, **rosca/sunburst** de alocação (meta contra atual).
- **Radar** de aderência ao perfil (meta tracejada dourada, carteira em gelo azul).
- **Gráfico de cascata (waterfall)** e **barras antes/depois** do simulador pré-ordem.
- **Cone patrimonial (fan chart) de Monte Carlo** com percentis (rotulado projeção).
- **Matriz de correlação (heatmap)** e **heatmap de concentração**.
- **Linha do tempo** de relatórios e prazos (entregue, em aberto, atrasado) com marcadores.
- **Curva de juros / série Selic, CDI e IPCA** com faixas de ciclo.
- **Mapa** escuro com marcadores por fundo, clusters, popover do imóvel (área, vacância, setores, precisão da coordenada, links
  Satélite e Street View), camada satélite e filtro por fundo, UF e tipo.
- **Mapa-múndi de risco geopolítico** (intensidade por região, manchetes ao selecionar) e **barras de impacto por setor**.
- **Calendário de renda passiva** (mapa de calor mensal) e **calendário econômico e de resultados**.

# 6. PÁGINAS A PROTOTIPAR (conteúdo, componentes e interações)

Para cada página: layout em grade de 12 colunas, cabeçalho com título, subtítulo, filtros e ações; conteúdo com dados
realistas (use os exemplos reais citados; o restante, valores plausíveis claramente marcados como "exemplo").

1. **Login e cadastro** (tela cheia, atmosfera luxuosa): formulário de e-mail e senha, **botão "Continuar com Google"** (estados:
   habilitado, desabilitado com aviso "Login com Google não está configurado neste ambiente", carregando, erro), link para o modo
   demonstração, aviso legal. Mostre também o **onboarding** de 3 passos (boas-vindas, descobrir perfil, primeira carteira).
2. **Painel inicial (Home):** saudação, **resumo de patrimônio** (valor, variação do dia, gráfico de evolução), **termômetro de
   mercados** (faixa de bolsas com horário), top movimentos, **sinais do dia** (cartões com selo de frescor), **atalhos**,
   próximos prazos de relatórios, **perfil e aderência** em resumo, **notícias de risco**, e um cartão "o que mudou desde sua
   última visita".
3. **Mercados:** tabela (watchlist) com filtros por região, classe, sinal e risco; busca; ordenação; sparkline; selo de frescor e
   fonte por linha; **modos de visão** (lista, cartões, mapa de calor por variação, comparar até 4 ativos); fixar favoritos.
4. **Cockpit do ativo:** cabeçalho (símbolo, nome, preço, variação, selos), **abas** (Visão, Técnico, Macro, Backtest, Relatórios,
   Composição); gráfico principal grande; cartão de **score e sinal**; grade de métricas com "?" e sparklines; "Por que este
   sinal"; histórico de sinais; **inspetor lateral**; botão "Simular compra ou venda" (leva ao simulador com o ativo preenchido).
5. **Radar de relatórios:** lista de emissores com último veredito e próximo prazo; ao abrir, **linha do tempo** dos relatórios,
   cartão de veredito expansível (números, base, limiar, regra), **calendário de prazos** (status coloridos) e fontes oficiais.
6. **Brasil (juros e inflação):** cartões de Selic, CDI, IPCA e PTAX com data de referência, sparkline e selo; gráfico comparativo; leitura
   didática ("o que isso significa para renda fixa e FIIs") com link ao glossário.
7. **Geopolítica e teses (próximo item do roadmap):** mapa de risco, eventos recentes com **tradução em efeito por classe e setor**,
   **teses institucionais genéricas** em cartões (título, premissa, ativos e setores afetados, o que invalida a tese), tudo sem
   posições reais do usuário e com aviso de que é conteúdo analítico.
8. **Minha Carteira:** patrimônio total (BRL/USD), alocação (treemap e rosca), tabela de posições (preço médio, valor, resultado,
   peso, vol), **aderência** ao perfil com radar, **livro de movimentações** (filtros por tipo e período, importação), indicadores de
   concentração, botão "Adicionar posição" (modal com tipo, símbolo, quantidade, valor).
9. **Perfil e carteira modelo:** questionário em **passos** com barra de progresso e as 3 dimensões; resultado em destaque (perfil,
   nota, tetos explicados); comparador das 4 carteiras modelo; **montador de perfil**; aviso "não substitui a análise de um
   profissional habilitado".
10. **Simulador pré-ordem:** montagem da carteira à esquerda, ordem hipotética à direita; resultado com **antes e depois** (barras,
    cascata), achados (Informação e Atenção), premissas visíveis e o banner de simulação.
11. **Mapa de ativos reais:** tela de mapa com painel de filtros, lista lateral sincronizada com o mapa, popover do imóvel,
    comparação de fundos, camada satélite e links de Street View; legenda de precisão (endereço, aproximado, sem coordenada).
12. **Composição (Raio-X):** por fundo, selo de fonte, métricas do fundo (ABL, vacância), tabela de imóveis com filtros e
    ordenação, setores de inquilinos, alocação (fundos de papel), avisos do que não consta.
13. **Monte Carlo, correlação e concentração (Milestone 5):** parâmetros à esquerda, cone patrimonial (projeção), probabilidade de
    metas, matriz de correlação e heatmap de concentração.
14. **Stress testing (extensão):** cenários prontos (dólar, juros, queda da bolsa, choque geopolítico), impacto por classe e na carteira.
15. **Alertas:** regras (preço, sinal, prazo de relatório, aderência), canais, histórico; criação em modal.
16. **Glossário e Central de aprendizado:** busca, grupos, entradas em três blocos (O que é, Como ler, Quando se preocupar),
    relacionadas, trilhas de aprendizado ("Entenda risco", "Leia um balanço", "Monte sua carteira").
17. **Saúde dos dados:** painel dos provedores (status, latência, última resposta, falhas), cobertura de dados por módulo e
    idade dos dados curados. É um diferencial de transparência.
18. **Configurações:** perfil, moeda, tema, privacidade, atalhos, dados e exportação, sessão e dispositivos.

# 7. IDEIAS NOVAS PARA SURPREENDER (inclua no protótipo; marque como "nova" no seu plano)

- **Paleta de comandos universal (Ctrl+K)** com ações ("Simular compra de 5 mil em BTC", "Abrir radar de PETR4", "Alternar para
  USD") e histórico recente.
- **"Meridian Score" do patrimônio:** nota única de saúde da carteira (aderência, concentração, diversificação, qualidade dos
  dados), explicável camada por camada, sem prometer retorno.
- **Linha do tempo do que mudou:** feed de eventos relevantes (sinal mudou, relatório entregue, prazo próximo, dado desatualizado).
- **Modo comparar:** até 4 ativos lado a lado (gráfico normalizado, métricas, vereditos).
- **Calendário unificado:** resultados, prazos da CVM, dividendos de FIIs, reuniões do Copom e feriados de bolsa em uma só visão.
- **Planejador de metas:** aposentadoria, reserva e projeto, com cone de Monte Carlo e probabilidade, sempre rotulado projeção.
- **Modo apresentação e relatório em PDF** para reunião com a família ou o assessor, com marca e avisos legais.
- **Modo privacidade** (borrar valores) e **modo demonstração** com dados reais públicos.
- **Narrativa assistida** (texto curto e honesto que resume a tela: "O que estou vendo?") sempre com as fontes ao lado.
- **Seleção de contexto global:** trocar de "perfil de visualização" (Conservador, Moderado, Arrojado, Agressivo) e ver como a
  plataforma reinterpreta carteira e alertas.
- **Painel de procedência global:** lista de tudo que a tela usou (fontes, horários, atrasos) com um clique.
- **Comparação de custos e tributos** de renda fixa isenta contra tributada (rotulada simulação, com base legal citada).
- **Biblioteca de teses e notas de pesquisa** com versionamento e comentários (Research Studio).
- **Acessibilidade premium:** navegação total por teclado, leitor de tela, alto contraste, tamanho de fonte ajustável, tabela
  alternativa para todo gráfico.

# 8. RESPONSIVIDADE E ACESSIBILIDADE

Breakpoints: 1440+, 1280, 1024, 768, 390. Desktop em 3 colunas (sidebar, conteúdo, inspetor); tablet com sidebar de ícones;
celular com barra inferior e gaveta. Alvos de toque de 44 px. Contraste AA mínimo, foco visível dourado, ordem de tabulação
lógica, `aria-label` nos ícones, `prefers-reduced-motion`, `prefers-color-scheme`. Nada depende só de cor (use ícone e texto).

# 9. CONTEÚDO E TOM

Português do Brasil, formal e caloroso, direto, sem jargão desnecessário e sem linguagem de venda. Frases como "Leitura mecânica
dos números publicados" e "Mostra o efeito; não indica o que comprar ou vender". Números em formato brasileiro (1.234,56; 12,34%),
datas em dd/mm/aaaa, horários com fuso. Nunca prometa retorno. Nunca use o termo "recomendação" para um sinal ou veredito.

# 10. ENTREGÁVEIS

1. **Sistema de design:** tokens (cor, tipografia, espaço, raios, sombras, movimento) exportáveis como variáveis CSS, componentes
   (botões, campos, abas, chips, tabelas, cartões, selos, modais, drawers, tooltips, popovers, toasts, esqueletos, gráficos).
2. **Shell:** navbar, sidebar (expandida e colapsada), inspetor, paleta de comandos, rodapé legal.
3. **As 18 páginas da seção 6**, navegáveis, com estados (carregando, vazio, sem dado, erro, desatualizado).
4. **Fluxos principais** prototipados de ponta a ponta: (a) onboarding + descobrir perfil; (b) abrir ativo, ler o score e a
   regra, simular uma compra; (c) abrir o radar de relatórios e entender um veredito; (d) explorar o mapa e abrir um imóvel;
   (e) pesquisar um termo no glossário a partir de um "?".
5. **Versões:** escuro (principal), claro "Ivory", desktop e celular.
6. **Notas de implementação:** como mapear cada gráfico para ECharts, Lightweight Charts e Leaflet; lista de ativos de
   marca (logotipo tipográfico, monograma, ícones) e especificação de movimento.

Prioridade de qualidade: coerência absoluta do sistema, hierarquia impecável, densidade de informação elegante (nunca poluída) e
a sensação de um produto caro, calmo e confiável. Quando houver dúvida entre bonito e honesto, **a honestidade da informação
vence**, e o design deve fazer a honestidade parecer luxo.

=== FIM DO PROMPT ===
