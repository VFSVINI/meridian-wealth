# Prompt v2 para o Claude Design: redesenho cinematográfico do MeridianWealth

Como usar: copie tudo entre `INÍCIO DO PROMPT` e `FIM DO PROMPT` e cole no Claude Design. Anexe as capturas de tela do site atual (para mostrar o que NÃO funcionou) e, se a ferramenta aceitar, o `docs/prompt-claude-design.md` (a primeira versão, com o contrato de dados). Peça por etapas (seção 8) se a resposta for limitada.

=== INÍCIO DO PROMPT ===

# MISSÃO

Redesenhe o **MeridianWealth**, uma plataforma de análise de investimentos (educacional, não executa ordens, não é recomendação). O site já está no ar e funciona, mas a experiência está ruim: **excesso de informação e de texto, telas que parecem listas estáticas, falta de hierarquia e alinhamento, e uma primeira página que brinca em vez de explicar**. Quero um site que faça quem entra **se escandalizar de tão bonito** e, ao mesmo tempo, **entender o que está vendo mesmo sem saber nada de investimento ou de geopolítica**.

Entregue um protótipo clicável, em alta fidelidade, com navegação real, estados e animações, e o sistema de design que o sustenta. Tudo em **português do Brasil**.

# 1. DIREÇÃO VISUAL: "COSMOS"

- **Vibe:** cinema de ficção científica espacial. Espaço profundo, estrelas, nebulosas discretas, luz âmbar e dourada, azul-noite, ciano frio como acento. Grandioso, mas **sóbrio e profissional**: pense em uma mesa de operações de uma nave, não em um jogo. Bolsa de valores precisa parecer **épica e elegante**.
- **Cores (proponha a paleta final):** fundo quase preto azulado, camadas em azul-noite, âmbar/ouro para destaque e marca, ciano para dados e interação, verde e vermelho SÓ para alta e baixa. Contraste mínimo **WCAG AA** em todo texto, nos temas escuro (principal) e claro (secundário, também lindo).
- **Tipografia de cinema:** um display largo ou condensado para títulos (estilo letreiro de filme), uma sans limpa para texto e uma monoespaçada tabular para números. **Use apenas fontes do Google Fonts com licença aberta (OFL)** e **liste os nomes exatos** das famílias e pesos escolhidos, porque o desenvolvedor vai baixá-las e servi-las localmente.
- **Animações:** campo de estrelas em paralaxe muito sutil no fundo, transição entre telas tipo "salto de hiperespaço" curta (menos de 500 ms), revelação suave ao rolar, microinterações nítidas. **Movimento com função**, sempre com versão para `prefers-reduced-motion`.
- **PROIBIDO:** usar nome, logotipo, fonte, texto ou imagem de qualquer franquia (inclusive Star Wars): é só a *vibe*. Contadores de preço que sobem de zero (nenhum preço pode ser animado: só aparece quando chega uma cotação nova). Instruções de brinquedo na tela ("arraste a moeda", "mexa aqui"): **se algo for interativo, deixe a pessoa se surpreender**. Objetos decorativos gigantes que roubam a atenção do conteúdo.
- **Referências:** https://www.awwwards.com/sites/potadot-web-system-solutions e https://designmodo.com/css-website-designs/ (composição, ritmo, tipografia, uso de espaço). Estrutura e princípios do wealthsimple.com/en-ca (hero escuro e calmo, título editorial, pílulas, muito espaço vazio), **sem copiar marca nem material**. Artigo de fintechs: https://goodface.agency/insight/top-10-fintech-product-interface-designs/ (densidade com clareza, explicação progressiva, modo escuro para análise longa).

# 2. PRINCÍPIOS DE EXPERIÊNCIA (o que mais importa)

1. **Uma mensagem por tela.** Cada página responde uma pergunta clara no topo; o resto é revelado sob demanda (abas, "ver detalhes", painéis laterais). **Menos texto, menos números, mais hierarquia.**
2. **Para leigos primeiro, para especialistas depois.** Cada número tem um "o que é isso e por que importa para o meu dinheiro" em linguagem simples, em camada (resumo de uma frase, detalhe ao abrir).
3. **Primeira página = apresentação e tutorial.** Explica em poucos segundos o que a plataforma faz, para quem é e **o que fazer agora**, em 3 passos (por exemplo: Explore, Entenda, Simule). Nada de brinquedo.
4. **Grade e alinhamento rígidos.** Mesma escala de espaços, mesma tipografia, mesmos cartões em todas as páginas. Nada solto nem desalinhado.
5. **Nada de listas verticais estáticas.** Use cartões, grades, mapas, gráficos interativos, linhas do tempo, comparadores. Mapas e gráficos podem ser 2D, 3D ou ter o tempo como dimensão (linha do tempo que se percorre), **sempre com uma alternativa simples** (tabela ou texto) e **só com dado real**.
6. **Funciona em qualquer tela:** notebook 1366×768 com escala de 125%, tablet e celular, **sem a pessoa precisar dar zoom**.

# 3. REGRAS DE DADOS (inegociáveis; o design precisa torná-las visíveis)

- **Dados 100% reais com fonte.** Toda cotação mostra fonte, horário e atraso. Dado curado mostra fonte e data. Onde a fonte não publica, a tela diz **"não informado"** (nunca zero ou traço).
- **Projeção é projeção:** Monte Carlo, backtests e cenários levam o selo "projeção".
- **Não executa ordens e não é recomendação**: aviso permanente, discreto e elegante.
- **Números do protótipo:** use dados plausíveis marcados **"exemplo"** ou os campos reais descritos abaixo; nunca apresente número inventado como se fosse real.

# 4. PÁGINAS E O QUE CADA UMA PRECISA (e os dados reais disponíveis)

1. **Início (apresentação):** hero cinematográfico e calmo com a proposta em uma frase, os 3 passos, 3 a 4 provas de confiança (dados reais com fonte, não executa ordens, explica cada número) e um único botão principal. Rodapé com o aviso legal. **Sem moeda gigante, sem texto de instrução de brinquedo.**
2. **Mercados:** **busca de qualquer ativo** (autocompletar por símbolo e nome; o catálogo cobrirá ações e FIIs da B3, ações e ETFs dos EUA, cripto, câmbio, commodities e índices), filtros em pílulas, **cartões** com preço, variação, mini-gráfico do histórico real, selo de frescor e fonte; agrupamento por região e classe; visão em grade (padrão), tabela e mapa. Campos reais por ativo: símbolo, nome, classe, bolsa, moeda, preço, variação, frescor (tempo real, atrasado em minutos, fechamento, gravado), fonte, horário, sinal do motor.
3. **Ativo (cockpit):** no topo, **"a leitura em uma frase"** (por exemplo, "o preço está esticado para cima; o motor sugere paciência") com o sinal, a confiança e **por que**, em linguagem simples. Gráfico de candles grande e limpo; níveis (entrada, alvos, invalidação) como camadas opcionais; indicadores técnicos escondidos atrás de "ver análise completa". Variação 3D opcional e discreta (terreno de candles com câmera orbital), nunca no caminho principal.
4. **Academia (substitui o glossário):** **curso em módulos**, cada módulo com capítulos curtos, ilustrações/diagramas, exemplos, "por que importa para o seu dinheiro", mini-quiz e progresso. Módulos sugeridos: Fundamentos (risco e retorno, inflação, juros), Renda variável, Renda fixa, Fundos imobiliários, Cripto, Câmbio e commodities, Indicadores técnicos (Z-Score, RSI, MACD, Bollinger, ATR, volatilidade, drawdown, Sharpe, VaR), Risco e carteira (correlação, diversificação, Monte Carlo), Macroeconomia e geopolítica. Tom de curso de banco de investimento, mas acessível.
5. **Brasil: juros e inflação:** não só números: **gráficos históricos** (Selic, CDI, IPCA mensal e em 12 meses, PTAX), uma linha do tempo, "o que cada um significa e como mexe no seu bolso", comparação (por exemplo, juros reais) e o caminho para a Academia. Dados reais do Banco Central, cada um com a data de referência.
6. **Geopolítica:** **mapa-múndi interativo** com camadas (risco macro, setores pressionados, manchetes por região), uma explicação passo a passo "por que geopolítica mexe com investimento" para quem nunca estudou, indicadores (VIX, índice do dólar, juros de 10 anos, índice de risco macro) com tooltip educativo, e **notícias em tempo real como chamada curta (título, fonte, horário) com um link "ler a matéria completa" para a fonte** (nunca reproduzir o texto). A tradução da carteira em exposição vem depois, sob demanda. Explore mapa, linha do tempo e, se fizer sentido, o tempo como dimensão.
7. **Minha Carteira:** alocação elegante (anel ou treemap), cartões de posição, ganho e perda, livro de movimentações em aba. Estado vazio acolhedor que ensina a começar.
8. **Perfil de investidor:** questionário **profissional, em etapas**, no estilo dos questionários de suitability de bancos e gestoras (objetivo, horizonte, necessidade de liquidez, tolerância a perdas com cenários concretos, experiência e conhecimento, proporção do patrimônio, situação financeira), uma pergunta por tela, barra de progresso, resultado com explicação e carteira modelo. Nada de perguntas vagas.
9. **Shopping financeiro:** **grade de cartões por classe de ativo** com o que cada um é, para quem faz sentido, risco, liquidez e proteção; comparador lado a lado. O Banco Central entra como referência oficial. **Nenhuma oferta de emissor inventada.**
10. **Mapa de ativos reais:** **todos os fundos imobiliários** (não só três): busca por fundo, filtros por UF, tipo e fundo, mapa do Brasil com agrupamento, lista de imóveis, e a precisão de cada coordenada à vista. Estado "sem coordenada" explicado.
11. **Risco:** **Monte Carlo** (cone patrimonial bonito e legível, probabilidade da meta, distribuição final, sempre "projeção") e **Correlação** (mapa de calor claro, concentração por faixa de risco, "não informado" nunca como zero). Visões 3D opcionais.
12. **Simulador pré-ordem** e **Saúde dos dados** (status das fontes): simples, claros e consistentes com o resto.

# 5. COMPONENTES E PADRÕES (entregue como sistema)

Cartão (com variações), cartão de ativo com mini-gráfico, pílulas e abas, botões (primário, secundário, fantasma), campo de busca com autocompletar, tooltips educativos "o que é isso", selo de frescor, selo "projeção", selo "não informado", estados vazio, carregando, erro e desatualizado, cabeçalho de página (pergunta + resposta curta), painel lateral de detalhe, linha do tempo, módulo de curso (capítulo, quiz, progresso), mapa, gráfico (candles, linha, área, barras, anel, mapa de calor), navegação (barra superior, menu lateral recolhível), rodapé legal.

# 6. RESTRIÇÕES TÉCNICAS (para o desenvolvedor implementar fielmente)

- HTML, CSS e JavaScript simples, **sem etapa de build**. Entregue **tokens como variáveis CSS** (cores, tipografia, espaços, raios, sombras, movimento) nos dois temas.
- **Sem CDN em execução e sem scripts ou estilos inline** (a política de segurança do site só aceita arquivos do próprio servidor). Bibliotecas já disponíveis no projeto: ECharts, Leaflet (sem mapa-base de terceiros), TradingView Lightweight Charts e three.js. Fontes só do Google Fonts (OFL), baixadas e servidas localmente.
- Imagens em SVG ou PNG exportáveis; fundos espaciais feitos em CSS ou canvas (estrelas procedurais), não fotografias de terceiros.
- Acessível: teclado, leitor de tela, contraste AA, alternativa em texto para todo gráfico ou mapa, `prefers-reduced-motion`.

# 7. O QUE EU NÃO QUERO (aprenda com os erros da versão atual)

Moeda ou objeto 3D enorme na primeira página; textos de instrução de brinquedo; páginas com tudo em lista vertical; blocos de texto longos antes do conteúdo; números soltos sem hierarquia ou alinhamento; legendas e avisos em excesso competindo com o dado; qualquer ativo "faltando" (a busca precisa cobrir tudo o que é negociado); glossário em forma de lista (tem que ser um curso); notícias sem link para a matéria; perguntas de perfil vagas.

# 8. ENTREGA POR ETAPAS

1. Sistema de design (paleta, tipografia, grade, componentes, movimento) e **Início**.
2. **Mercados** (busca e cartões) e **Ativo**.
3. **Academia** e **Brasil: juros e inflação**.
4. **Geopolítica**.
5. **Minha Carteira**, **Perfil de investidor** e **Shopping financeiro**.
6. **Mapa de ativos reais**, **Risco** (Monte Carlo e Correlação), **Simulador** e **Saúde dos dados**.
7. Versões **celular** e **notebook 1366×768** das páginas principais, e o tema claro.

Ao final de cada etapa, liste: as fontes escolhidas (nome exato e pesos), as cores (hex) e quais componentes foram criados, para o desenvolvedor reproduzir.

=== FIM DO PROMPT ===
