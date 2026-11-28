# Changelog

Todas as mudanças relevantes do projeto ficam registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o versionamento segue o [SemVer](https://semver.org/lang/pt-BR/).
Cada milestone fecha com uma tag `v0.<N>.0` e uma GitHub Release.

## [Não lançado]

### Adicionado (item #54, parte 1: Cripto 24h, Milestone 8: Cosmos)
- **Cripto 24h no visual do protótipo**: "O que está acontecendo no cripto agora, a qualquer hora?", gráfico **ao vivo** (BTC, ETH ou SOL, amostrado a cada segundo do preço do stream da bolsa conectada, com fonte), últimas cotações, preço de 7 e 30 dias, **sistema de moedas** (centro = moeda de maior volume; tamanho = volume em 24 h; cor = variação de 24 h ou 7 dias; planetas que giram devagar), detalhe da moeda com texto educativo, participação no volume, 24/7 contra horário comercial (bolsas abertas agora, pelo estado real do pregão) e tabela das 12 moedas.
- **Oito moedas a mais** no universo (BNB, DOGE, ADA, AVAX, LINK, DOT, LTC e TRX), com pares na Binance, na Coinbase e na Kraken quando existem; a página Mercados também as lista.
- Sem fonte no projeto, não aparecem: negócio a negócio e lado de compra e venda (o stream só traz o preço), valor de mercado (por isso volume em 24 h), variação de 1 hora e o índice de medo e ganância (marcado como em breve).

### Adicionado (item #53, parte 4: Mapa de ativos, Milestone 8: Cosmos)
- **Mapa de ativos no visual do protótipo**: "Onde estão os ativos que eu acompanho?", abas **Mundo** e **Brasil** e filtro por classe (Todos, Ações, FIIs, ETFs). Na aba Mundo, as bolsas dos ativos acompanhados sobre o mapa-múndi: cor do país = variação média dos ativos, tamanho da bolha = quantidade de ativos, arcos ligando São Paulo às outras praças, e painel da bolsa escolhida com preço, variação e se o pregão está aberto. Cripto, câmbio e commodities ficam de fora, ditos; país sem ativo conectado fica tracejado e "não informado".
- A aba Brasil é o mapa dos imóveis dos fundos que já existia. ADR 041 registra as decisões do #53.

### Alterado (item #53, parte 4)
- O mapa dos imóveis dos fundos passou para dentro da aba Brasil.

### Adicionado (item #53, parte 3: Risco, Milestone 8: Cosmos)
- **Risco no visual do protótipo**: "Quão longe posso chegar e quanto posso oscilar no caminho?", atalhos de aporte e prazo (a projeção roda sozinha), **cone de patrimônio** com o ruim (1 em 20), o mediano e o bom (1 em 20) do ano escolhido no controle deslizante, anel com a chance da meta e a **paisagem de probabilidades**: uma curva por ano mostra onde terminaram as 5.000 trajetórias, e a montanha se espalha com o tempo.
- `/api/risk/monte-carlo` e `/api/me/risk/monte-carlo` passam a devolver `density`: limites comuns e, para cada ano, quantas trajetórias caíram em cada uma das 48 faixas de patrimônio (as mesmas trajetórias do cone, nada sorteado no navegador).

### Alterado (item #53, parte 3)
- Os gráficos anteriores (cone 2D e 3D, distribuição final, meta ano a ano, tabela e premissas) continuam, recolhidos em "Gráficos detalhados".

### Adicionado (item #53, parte 2: Simulador, Milestone 8: Cosmos)
- **Simulador no visual do protótipo**: "O que muda se eu registrar essa operação?", escolha de ativo, compra ou venda e valor, e a simulação roda sozinha a cada mudança. **Órbita da carteira**: cada planeta é uma posição (tamanho = peso depois da ordem, cor = classe, anel = volatilidade anualizada real do ativo; sem volatilidade, anel pontilhado), girando devagar e pausando ao passar o mouse. Resumo com quantidade estimada pela cotação de referência (só em reais), peso antes e depois, "E se cair?" em três cenários hipotéticos e a concentração no ativo.
- A carteira da simulação é a salva do usuário (quando logado) ou a informada no modo avançado; a conta de impacto continua sendo a do servidor (`/api/impact/pre-order`) e nenhuma ordem é enviada ou guardada.

### Alterado (item #53, parte 2)
- O simulador antigo (carteira completa, perfil e tabela antes e depois) continua, recolhido em "Modo avançado".

### Adicionado (item #53, parte 1: Saúde dos dados, Milestone 8: Cosmos)
- **Saúde dos dados no visual do protótipo**: "Posso confiar nos números que estou vendo?", três números (fontes saudáveis, ativos com preço e dados não informados), **constelação de fontes** (cada estrela é um provedor que gira devagar em torno do MeridianWealth; anel de dentro = ligados, de fora = desligados ou sem chave) e painel da fonte escolhida com chamadas, sucessos, falhas, barradas, latência média e a taxa de sucesso.
- Os números vêm do `/api/status`; o servidor ainda não guarda histórico de disponibilidade nem de tempo de resposta, então esses gráficos do protótipo não aparecem (nada é inventado). Os quatro estados das telas (carregando, vazio, desatualizado, erro) aparecem como exemplos rotulados.

### Alterado (item #53, parte 1)
- O status técnico antigo da ingestão continua, recolhido em "Status técnico completo".

### Adicionado (item #52, parte 3: Perfil, Milestone 8: Cosmos)
- **Perfil no visual do protótipo**: "Que tipo de investidor eu sou?", **sete perguntas, uma por tela**, com barra de progresso, Voltar e Próxima; no fim, o perfil, o motivo dos tetos, a carteira modelo e a aderência da carteira, como antes.

### Alterado (item #52, parte 3)
- O questionário passou de nove para **sete perguntas** (ADR 040): necessidade de caixa e reserva de emergência viraram uma pergunta, e produtos e tempo de experiência outra. Nota máxima de 28 pontos, faixas e tetos iguais; perfis já salvos continuam válidos.

### Adicionado (item #52, parte 2: Shopping, Milestone 8: Cosmos)
- **Shopping no visual do protótipo**: "Que tipo de investimento combina comigo?", filtro por risco, oito cartões de classes (Tesouro Selic, CDB, LCI e LCA, Ações, FIIs, ETFs, Fundos e Criptoativos) com o que é, para quem serve, liquidez e proteção, e **comparação de até três classes lado a lado**, com indexador, rendimento e a referência oficial do Banco Central.
- Texto educativo em `data/shopping.json`; a proteção segue as regras do FGC já verificadas e a liquidez é descrita em termos gerais, sem prazo de liquidação nem taxa de produto inventados. O catálogo completo e as referências ficaram recolhidos.

### Adicionado (item #52, parte 1: Minha Carteira, Milestone 8: Cosmos)
- **Minha Carteira no visual do protótipo**: "Quanto eu tenho, onde e com que risco?", abas Visão geral, Posições e Lançamentos, três números (patrimônio, resultado não realizado e realizado das vendas registradas), rosca da alocação por classe e **mapa das posições** proporcional ao peso em reais.
- Roadmap: Milestone 10 (DevOps sem custo) e o guia de estudo para entrevista (#64, privado, fora do repositório).

### Alterado (item #52)
- Os cartões existentes (aderência, rosca 3D, posições, livro e ganho por posição) continuam, agrupados nas abas; a alocação em treemap foi trocada pela rosca.

### Adicionado (item #51 do roadmap, Milestone 8: Cosmos)
- **Geopolítica no visual do protótipo**: mapa-múndi com o risco por país segundo as manchetes reais (abas Risco macro, Setores pressionados e Manchetes), painel do país com a manchete de maior risco e link, passo a passo Evento → Expectativa → Preço → Carteira, quatro termômetros (VIX, dólar DXY, juro de 10 anos, índice de risco) e notícias com título, fonte, horário e link para a matéria.
- País sem manchete aparece como "não informado", nunca como risco zero; o método é publicado e não é um índice oficial.
- Mapa a partir do Natural Earth 110m (domínio público), 176 países, com projeção calculada no navegador.

### Alterado (item #51)
- Notícias passam a carregar link e horário; o VIX e o dólar trazem a variação do dia. O tradutor e o radar da carteira continuam, recolhidos.

### Adicionado (item #50 do roadmap, Milestone 8: Cosmos)
- **Brasil: juros e inflação no visual do protótipo**: três cartões com **gráfico histórico real de 12 meses** (meta Selic, IPCA em 12 meses e dólar PTAX), "O que isso muda no seu bolso" em abas (Selic, Inflação, Dólar) com contas simples sobre o valor real mais recente, **juro real** em barras (poupança pela regra legal, Tesouro Selic, CDB a 100% do CDI e inflação) e a **linha do tempo das mudanças reais da meta Selic**, com o caminho para a Academia.
- `GET /api/brazil/history`: séries oficiais do Banco Central como texto exato, com data de cada ponto; cache de 30 minutos, último dado bom se o Banco Central falha e espera antes de repetir uma consulta que falhou. O que não chega vem como indisponível, nunca estimado.

### Alterado (item #50)
- A lista de indicadores oficiais da página Brasil ficou recolhida em "Todos os indicadores oficiais".

### Adicionado (item #49 do roadmap, Milestone 8: Cosmos)
- **Academia**: o glossário em lista virou um **curso em 9 módulos e 26 capítulos** (Fundamentos, Renda variável, Renda fixa, FIIs, Cripto, Câmbio e commodities, Indicadores técnicos, Risco e carteira, Macro e geopolítica). Cada capítulo tem ideia central, diagrama, exemplo hipotético, "por que importa para o seu dinheiro" e mini-quiz; o cartão "Seu progresso" mostra os capítulos concluídos, salvos só no navegador.
- Um teste confere a estrutura do curso, as contas dos exemplos e a ausência de promessa de retorno e de recomendação.

### Alterado (item #49)
- O menu "Entender" passa a ter a Academia no lugar do Glossário (o glossário segue na Academia e nos botões "?" dos indicadores); os botões do Início levam à Academia.

### Adicionado (item #48 do roadmap, Milestone 8: Cosmos)
- **Mercados no visual do protótipo**: "Como estão os mercados agora?", busca, pílulas por classe com contagem, visões Lista, Tabela e Mapa (o globo de pregões), lista com mini-gráfico real e **ativo em destaque que troca sozinho a cada 7 segundos** (com "Pausar troca", e sem troca automática com movimento reduzido), com candles, selos de frescor e fonte, leitura em uma frase, RSI, volatilidade e média de 50 dias.
- **Ativo no visual do protótipo**: "SÍMBOLO: o que o gráfico diz?", chips para trocar de ativo, cartão **"A leitura em uma frase"** (sinal, confiança, por quê e o que mudaria), gráfico em Área ou Velas com camadas independentes de Entrada, Alvos e Invalidação, "Conheça o ativo antes de decidir" em cinco abas e o restante do cockpit atrás de "Ver análise completa".
- `GET /api/sparklines`: os fechamentos recentes de todos os ativos acompanhados numa chamada, só dado real.

### Alterado (item #48)
- Os blocos provisórios da página inicial (sinais do dia, maiores movimentos, notícias e atalhos) saíram de Mercados; as manchetes voltam em Geopolítica (item #51).

### Corrigido (rotação de refresh token)
- **Deadlock na rotação concorrente do mesmo refresh token** (visto uma vez no CI, com um ciclo de quatro conexões no PostgreSQL): os perdedores de uma corrida acordavam juntos e todos apagavam a mesma família, travando linhas e a fila de tuplas em ordens diferentes. A repetição da transação (3 tentativas) já existia, mas com muitos perdedores ainda podia estourar. Agora toda rotação começa com um lock consultivo de transação pelo hash do token apresentado, então as rotações do **mesmo** token se enfileiram antes de tocar em qualquer linha (rotações de outros tokens não esperam); a repetição segue como rede de segurança. Teste determinístico: com o lock segurado por outra transação, a rotação espera; sem a correção ele falha.

### Adicionado (item #34 do roadmap)
- **Composição e mapa de todos os FIIs com imóveis na CVM**: 167 fundos e 1.220 imóveis do Informe Trimestral (dados abertos da CVM, fonte oficial), com 466 coordenadas confirmadas pela geocodificação conservadora e o motivo de cada imóvel sem coordenada. O ticker é ligado ao CNPJ pelo ISIN do próprio Informe, conferido com o catálogo e o nome; fundo duvidoso fica de fora. O tipo do imóvel fica em branco porque a CVM não o publica.
- **Mapa para centenas de fundos**: busca do fundo pelo código, filtros por UF e tipo, lista que mostra 60 por vez, fontes resumidas e comparação dos 15 maiores do filtro.
- As respostas JSON do servidor passam a sair comprimidas (a composição inteira foi de 1,17 MB para 161 KB).

### Adicionado (item #33 do roadmap)
- **Catálogo de ativos negociados**: 14 013 ativos (ações, FIIs, ETFs e BDRs da B3 e ações e ETFs dos EUA), entre eles o XPSF11, que não aparecia. O catálogo guarda só cadastro (nenhum preço); fontes: Nasdaq Trader e brapi, geradas por `scripts/build-catalog.py`.
- **Busca de qualquer ativo** no seletor e no Ctrl+K (símbolo ou nome, sem acento), marcada "catálogo". Ao abrir um ativo, o servidor busca o histórico real e **o motor calcula o sinal dele** como o dos demais; sem dado real o ativo não entra e a tela diz isso. Até 40 ativos sob demanda; a rota de abertura tem limite próprio.

### Corrigido (item #33)
- Antes da abertura do pregão a Yahoo manda a barra do dia com abertura e mínima 0 (medido em HGLG11 e XPSF11): o parser aceitava o zero e a variação do dia daria infinito. Agora esses preços viram o fechamento e a barra sem abertura real não é usada.

### Adicionado (item #47 do roadmap, Milestone 8: Cosmos)
- **Fundação visual do protótipo Cosmos** (as telas do Claude Design estão em `docs/design/cosmos/`): tokens do tema escuro (fundo espacial, âmbar e ciano) e do Ivory, títulos de cinema em **Big Shoulders Display**, texto em **Inter** e números em **IBM Plex Mono** (fontes OFL servidas pelo projeto, hash fixado em teste), barra superior e menu por seções (Explorar, Entender, Simular, Mais) e rodapé legal fixo.
- **Céu vivo** no fundo: estrelas que derivam e reagem ao ponteiro, nebulosas que orbitam e o **salto de velocidade da luz** entre uma página e outra. Tudo parado com `prefers-reduced-motion`; nenhum dado é desenhado nem animado.
- **Nova página Início**: apresentação ("Entenda o mercado antes de decidir"), três passos e "por que confiar". O cartão "A leitura em uma frase" usa **dado real** (cotação, fonte, horário, últimos 60 fechamentos e a tradução do sinal do motor).

### Alterado (item #47)
- A moeda 3D e a saudação por horário saíram da página inicial; o globo de pregões, os sinais do dia, os maiores movimentos e as notícias foram para o fim de Mercados até a repaginação (item #48).
- Cormorant Garamond e Figtree deixaram o projeto.

### Adicionado (item #43 do roadmap, bloco 3D)
- **Alocação em 3D** em Minha Carteira: uma **rosca 3D** com o anel de fora nas **classes de ativo** (os percentuais exatos da API, os mesmos da legenda e do treemap 2D) e o anel de dentro nas **posições** (peso no valor em reais de hoje; posições sem valor em reais ficam de fora e isso é dito). Passar o mouse numa fatia a destaca e mostra o nome, o percentual e, nas posições, o valor. Câmera orbital compartilhada; a lista, o treemap e a tabela de posições são a alternativa. ADR 032.
- **Privacidade:** ao sair da conta o módulo esquece a carteira, esvazia a cena e troca o texto de acessibilidade por um genérico; um novo login monta a cena do zero.

### Corrigido (item #43)
- Em tela de toque os detalhes dos gráficos 3D (cockpit, Monte Carlo, correlação e carteira) não abriam: o tooltip só existia com hover e o `pointerleave` do dedo o esconderia. A órbita compartilhada ganhou `onTap` (toque ou clique sem arrastar) e o `onLeave` vale só para mouse; um toque abre o detalhe e ele permanece (achado do code review, medido antes e depois).

### Adicionado (item #42 do roadmap, bloco 3D)
- **Correlação entre ativos em 3D** (card na página de correlação): a matriz de Pearson da carteira salva como barras, **altura = correlação**: positiva sobe em verde, negativa desce em vermelho, a diagonal (1,0) fica evidente. **A célula "não informado" nunca vira zero**: é só um marcador cinza achatado, sem barra. Passar o mouse mostra o par, o valor, os pregões em comum e o aviso de estimativa frágil (menos de 120 pregões). Câmera orbital compartilhada (`kit3d.js`); a tabela de cima traz os mesmos números. ADR 031.

### Corrigido (item #42)
- Depois de sair da conta, o 3D da correlação ainda guardava a matriz da carteira do usuário anterior (e o texto de acessibilidade do canvas citava os ativos); agora a tela de entrada limpa o estado, esvazia a cena e um novo login monta tudo do zero (achado do code review, medido antes e depois).

### Adicionado (item #44 do roadmap, bloco 3D)
- **Cone 3D** no Monte Carlo (botão "Cone 3D"): o cone patrimonial como **superfície**, x = tempo, y = patrimônio, z = percentil, com as cinco curvas dos percentis 5, 25, 50, 75 e 95 **exatos** que a simulação devolveu (a superfície só liga as curvas), a parede do histograma do patrimônio final no fim do cone e o plano da meta, tudo na mesma escala de patrimônio. Passar o mouse mostra o mês e os cinco percentis. **Rótulo "projeção" sempre visível** (na tela, no tooltip e na descrição para leitor de tela). O 2D (ECharts) e a tabela continuam. ADR 030.
- **Câmera orbital compartilhada** no `kit3d.js` (`createOrbit`), mais `makeLabel` e `disposeGroup`: o cockpit passou a usá-la, sem mudar de comportamento (hover, inspetor, teclado, zoom, ajuste ao celular e 0 vazamento conferidos de novo).

### Corrigido (item #44)
- Voltar do cone 3D para o 2D com a janela redimensionada deixava o gráfico ECharts com a largura de quando estava escondido (medido: 100 px num contêiner de 635 px); agora o 2D é redimensionado ao reaparecer (achado do code review).

### Adicionado (item #41 do roadmap, bloco 3D)
- **Globo 3D dos mercados** na página inicial ("Pregões agora"): as dez bolsas com cidade (NYSE, B3, Xetra, LSE, Euronext, Tóquio, Hong Kong, ASX, Tadawul, JSE) como marcadores no globo, coloridas por **aberta, fechada ou feriado agora**, e FX e cripto (24 h, sem cidade) como anéis em órbita. O estado é o do servidor (`/api/markets`, com os feriados oficiais e o aviso de calendário não verificado). **Dia e noite reais:** a posição do Sol é calculada do horário UTC (aproximada, erro de poucos graus). Continentes do **Natural Earth 110m** (domínio público), baixado com permissão e fixado por SHA-256 em `static/data`.
- Arrastar gira (com inércia); **clicar ou tocar numa bolsa** (no globo ou na lista) gira até ela e mostra o estado, a hora local e os ativos do universo nela, que abrem o cockpit. Usa o `kit3d.js`. ADR 029.
- O relógio de pregões virou **botões** (teclado, toque e leitor de tela), a alternativa em texto do globo.

### Corrigido (item #41)
- No globo, o hover e o clique achavam marcadores **do outro lado do planeta** (invisíveis): o raycast não considerava a esfera. Com a B3 de frente o tooltip mostrava a Tadawul, a ~102°; agora a esfera oclui o que está atrás (achado do code review, medido antes e depois).
- O `title` do relógio de pregões mostrava a **hora em UTC** como "hora local" (o texto da API não traz `[zona]`, e o código caía em UTC); a API já devolve a hora no fuso da bolsa (ASX 15:09, Tóquio 13:09), e agora ela é lida direto da string (medido contra `/api/markets`).
- O globo pedia o estado das bolsas só pelo evento periódico, que sai antes de o módulo existir: ficava em branco por até 30 s; agora o módulo pede o estado ao carregar.

### Adicionado (item #40 do roadmap, bloco 3D)
- **Cockpit em 3D** (botão "Gráfico 3D"): o histórico **real** do ativo (os mesmos candles do gráfico 2D) como terreno: x = tempo, y = preço, corpos e pavios dos candles, volume em barras no piso à frente, SMA50, bandas de Bollinger e a faixa entre elas. Câmera orbital: arrastar gira (com inércia), Ctrl + roda ou botões dão zoom, duplo clique restaura, setas do teclado giram. Passar o mouse sobre um candle mostra abertura, máxima, mínima, fechamento e volume reais e **atualiza o cartão "Inspetor do candle"**. O gráfico 2D (TradingView Lightweight Charts) e a tabela continuam como alternativa; em celular o terreno inteiro cabe na largura. ADR 028.
- **`kit3d.js`**, o módulo 3D compartilhado (ADR 026 prometia antes do segundo uso): cria o renderizador, ajusta o tamanho, só desenha o que se vê (0 desenhos fora da tela), trata a perda e a restauração do contexto WebGL e o movimento reduzido. O hero passou a usá-lo, com o mesmo comportamento.

### Corrigido (item #40)
- A cada atualização do cockpit o 3D reconstruía a cena sem liberar os buffers de matriz e de cor dos `InstancedMesh`: **46 buffers da GPU vazados em 12 reconstruções** (medido contando `createBuffer` e `deleteBuffer`); agora o `dispose()` do `InstancedMesh` é chamado e o vazamento líquido é 0 (achado do code review).
- No cockpit 3D, um evento de dados chegando **enquanto o contexto WebGL estava perdido** chamava a montagem da cena, que escondia o aviso "O navegador pausou o 3D"; agora a montagem espera a restauração (medido).

### Adicionado (item #45 do roadmap, bloco 3D)
- **Transição "íris" entre telas:** a página que entra se revela num círculo que abre, com leve rotação 3D e desfoque que some (`fx.js`). O roteador continua trocando as páginas de forma síncrona (ECharts e Leaflet medem o tamanho na hora); a transição é só uma camada visual.
- **Cartões que se inclinam em 3D** seguindo o ponteiro, com um brilho que acompanha (sinais do dia, atalhos), e **revelação ao rolar** (cartões abaixo da dobra entram com subida suave).
- Desligado com `prefers-reduced-motion`; a inclinação também em telas de toque. **Nenhum número é animado** (ADR 004): o módulo não escreve texto em elemento nenhum, e um teste trava isso. ADR 027.

### Corrigido (item #45)
- Imprimir ou salvar em PDF uma página longa antes de rolar deixava os cartões ainda não revelados em branco (medido: 7 de 7 no cockpit); agora a impressão mostra todos (achado do code review).
- A íris podia ser cortada no meio: o pulso de cotação nova (`beat`, `fd`, `fu`) de um filho também dispara `animationend`, que borbulha e removia a classe da página antes da hora; agora só vale o fim da própria animação da página (medido provocando o caso).

### Alterado (item #31, etapa 1b: ajustes do hero)
- **Interação:** arrastar para girar (com inércia, volta ao giro lento), clique ou toque dá um impulso, brilho que segue o ponteiro e a rolagem da página inclina a moeda; em tela de toque a rolagem vertical continua funcionando (`touch-action: pan-y`). Dica "Arraste para girar" que some no primeiro uso.
- **Tamanho e acabamento:** moeda maior, ouro mais rico com luz de borda quente e halo suave atrás.
- **Bloco 3D no roadmap** (itens #40 a #46): cockpit, globo dos mercados, correlação, carteira e Monte Carlo em 3D, microinterações e fundo vivo, todos só com dado real e com alternativa em tabela ou 2D.

### Corrigido (item #31, etapa 1b)
- O clique direito acionava o modo arrasto da moeda (e o menu de contexto pode engolir o `pointerup`, congelando o giro) e `setPointerCapture` não tinha proteção contra erro; agora só o botão principal arrasta, a captura é protegida e perder a captura também solta o arrasto (achado do code review).
- O hero **desenhava a cena 3D a cada evento de rolagem ou de ponteiro mesmo fora da tela ou em outra página** (medido: 360 chamadas de desenho da GPU para 40 rolagens fora da página inicial), gastando bateria à toa; agora só desenha o que está visível (achado do code review).

### Adicionado (item #31, etapa 1: página inicial)
- **Hero 3D original** na página inicial: um medalhão dourado com o "M" da marca, em **three.js 0.170.0** (MIT) servido localmente com SHA-256 fixado, que inclina com o ponteiro e gira devagar. Título editorial com a saudação, subtítulo e botões em pílula, sobre um degradê azul-marinho. Pausa fora da tela e com a aba oculta, tem um quadro estático com movimento reduzido e, sem WebGL, o medalhão em CSS. Nenhum número ou preço dentro do 3D (é identidade, não dado). ADR 026.
- O hero trata a **perda do contexto WebGL** (celular em segundo plano, driver reiniciado): mostra o medalhão em CSS e refaz o mapa de reflexo ao restaurar (antes virava um retângulo branco com ícone de imagem quebrada) (achado do code review).
- Estrutura inspirada no Wealthsimple (hero escuro e calmo, título grande, pílulas, objeto 3D) **sem copiar marca, moeda, vídeo nem texto** deles.

### Adicionado (item #30 do roadmap, Milestone 7)
- **Seletor de ativo com busca** (`picker.js`): combobox acessível (digite símbolo, nome, classe ou bolsa; setas, Enter e Esc; leitor de tela) no cockpit, no radar e na composição, no lugar do `select` longo.
- **Escala em qualquer tela:** a barra lateral começa recolhida até 1279 px (a escolha do usuário vale), espaçamento, títulos e cartões fluidos, duas colunas em notebook; medido sem rolagem horizontal nas 16 rotas em 390, 768, 1093 e 1366 px.
- **Tema claro refeito:** fundo creme mais escuro com cartões brancos e sombra, bordas visíveis e cores de texto com contraste AA conferido por teste nos dois temas.
- Saudação por horário com "Boa madrugada" (antes, à 00h24, dizia "Bom dia").
- Novo bloco **Milestone 7** no roadmap (itens #30 a #39) com o que o primeiro uso do site no ar apontou. ADR 025.

### Corrigido (Issue #58, deploy do item #21)
- O runbook do deploy subestimava o proxy não confiável: além dos 300 pedidos por minuto, as rotas pesadas (Monte Carlo e correlação) têm 10 por minuto, e atrás do proxy o site inteiro divide esse limite (medido: da 11ª chamada em diante, 429); configurar `MW_TRUSTED_PROXIES` passou a ser obrigatório antes de divulgar o endereço (achado do code review).
- O **Hugging Face Spaces exige plano pago para Docker** (só Spaces estáticos são gratuitos); a premissa do ADR 023 não tinha sido conferida no site. O deploy gratuito passa a ser o **Render** (Docker, plano gratuito) com o PostgreSQL no Neon: `render.yaml` (Blueprint), runbook reescrito, ADR 024 (substitui o 023), limites de memória da JVM por variável e o `HEALTHCHECK` do `Dockerfile` seguindo a `PORT`. Saem `deploy/hf/` e o workflow do Hugging Face.

## [0.5.0] - 2026-11-23

Fecha o Milestone 5 (itens #18 a #21): risco quantitativo, testes de concorrência e deploy gratuito.

### Adicionado (item #21 do roadmap)
- **Deploy gratuito** no Hugging Face Spaces (SDK Docker, porta 7860) com PostgreSQL no Neon: `deploy/hf/` (Dockerfile com UID 1000 e README com `sdk: docker` e `app_port: 7860`), workflow **manual** `deploy-hf.yml` (segredos `HF_TOKEN` e `HF_SPACE` do dono do repositório) e o runbook [docs/deploy.md](docs/deploy.md), com variáveis, verificação, reversão e os pontos que só se provam com o Space no ar. ADR 023.
- Resumo executivo no README; variáveis `PORT`, `DB_POOL_SIZE` e `DB_CONNECT_TIMEOUT_MS`.
- Testes: coerência dos arquivos de deploy e a aplicação inteira subindo como no Space (banco ligado, Redis desligado) contra PostgreSQL.

### Corrigido (item #21)
- O `X-Forwarded-For` ignorado (conexão de proxy não listado em `MW_TRUSTED_PROXIES`) agora gera um aviso nos logs, uma vez por endereço de conexão (no máximo 50), com o endereço do proxy e nunca o IP do visitante; sem isso o operador de um provedor como o Hugging Face não tinha como descobrir a faixa a listar (achado do code review).
- O `HEALTHCHECK` dos Dockerfiles procurava `UP` no health agregado, que devolve `DEGRADED` (HTTP 200) quando falta dado de algum provedor; agora usa `/actuator/health/liveness`.

### Adicionado (item #20 do roadmap)
- **Testcontainers 1.21.4:** `PostgresDb` escolhe o PostgreSQL por `-Dmw.test.postgres=`; o padrão segue sendo o binário embutido (sem Docker) e `container` sobe um PostgreSQL 16 do Testcontainers. A CI roda, depois do `verify`, os contratos de persistência, a migração V3 e a aplicação com banco **no contêiner**. Fecha a dívida 36.
- **Concorrência e idempotência pela API HTTP**, nos dois modos de persistência: cadastro simultâneo do mesmo e-mail (uma conta, as outras 409), refresh simultâneo do mesmo cookie (uma troca, as outras 401, família revogada) e venda total simultânea da mesma posição (uma venda, nunca 500, livro com abertura e uma venda).
- **PIT (teste de mutação)** sobre o domínio (`mvn test-compile org.pitest:pitest-maven:mutationCoverage`), job próprio na CI com o relatório publicado e **meta de 80%** no `pom.xml`. Primeira medida: 71% dos mutantes mortos e o motor de timing com 85% de sobreviventes; depois dos testes novos, **85% mortos** e força dos testes de 87%. ADR 022.
- **Teste dourado do motor de timing** (13 cenários determinísticos, todos os campos e os textos dos fatores), testes de frescor, beta, volume relativo, ATR, quantis e limites do Monte Carlo e da correlação.

### Corrigido (item #20)
- O motor podia dizer **"Realizar lucro" e justificar com "Sem gatilhos relevantes"**: o score soma componentes, mas a lista de fatores só mostrava os que passam de um limite sozinhos; em 4 de 11 séries calmas o sinal saiu da soma de leituras moderadas com a explicação de reserva. Agora, sem gatilho individual e com |score| de pelo menos 20, o fator diz que o sinal vem da soma de leituras moderadas, com Z-Score, percentil e RSI (achado do code review, ao cobrir o último ramo que o PIT mostrou sem cobertura).
- O **beta** com benchmark praticamente parado devolvia números gigantes (da ordem de 10¹²), porque só a variância exatamente zero era tratada; agora vira "não informado" (achado ao montar os cenários do teste dourado; o PIT mostrava o `beta` com mutantes vivos).

### Adicionado (item #19 do roadmap)
- **Correlação e concentração** (`#/correlacao`, `GET /api/me/risk/correlation`, com login): matriz de **correlação de Pearson** entre os ativos e entre as classes da carteira salva, sobre os últimos 252 retornos diários reais, **par a par** (cada par usa as datas que os dois têm); par com menos de 60 pregões em comum, ou série que não varia, fica **"n/d"** (nunca zero), com o número de pregões no título da célula; o número aparece em toda célula com dado.
- **Risco da carteira com a correlação:** volatilidade anualizada sobre os retornos alinhados, ao lado da média ponderada das volatilidades individuais (o limite superior que o simulador usa) e o **benefício da diversificação**, com a cobertura do patrimônio dita (resolve a essência da dívida 27 para a carteira salva).
- **Mapa de concentração:** peso de cada classe em cada faixa de risco do motor (baixo, moderado, alto, extremo, sem classificação), HHI por posição e por classe, posições efetivas e as cinco maiores posições; ativos sem histórico ficam fora da correlação com o motivo e dentro da concentração pelo valor. ADR 021.
- Testes de propriedade (correlação de uma série com ela mesma é 1 e com a oposta é -1, simétrica e limitada, diversificação nunca negativa, par curto vira "n/d") e cenário de ponta a ponta com candles reais gravados.

### Corrigido (item #19)
- A matriz de correlação não mostrava de quantos pregões vinha cada célula, ao contrário do que a documentação afirmava: agora toda célula, com ou sem dado, traz o valor e os pregões usados no `title` e no `aria-label`, uma linha visível explica, e a célula com menos de 120 pregões ganha um traço pontilhado (achado do code review, medido no app real: 0 de 169 células tinham o dado).
- Motivo mais claro quando um ativo não tem dado real de mercado agora (antes dizia "histórico curto: 0 pregões").

### Corrigido (item #18)
- A rota pública do Monte Carlo deixava **um único IP saturar a CPU dentro do limite geral**: cada simulação custa cerca de 0,3 s, e 50 requisições em paralelo de um só cliente passaram todas (4,4 s de servidor ocupado). As rotas de cálculo pesado (`/api/risk/**` e `/api/me/risk/**`) ganharam um limite próprio (padrão 10 por minuto, por IP ou por usuário, `meridianwealth.rate-limit.heavy-per-minute`) e o serviço tem um teto de 4 simulações simultâneas, com 503 e `Retry-After` quando está cheio (achado do code review, medido no app real).

- A rotação concorrente do mesmo refresh token podia derrubar uma das requisições com `deadlock detected` do PostgreSQL (duas revogações da família travando linhas em ordens opostas), o que viraria erro 500 em produção; apareceu como uma falha intermitente na CI. A rotação agora refaz a transação perdedora (até 3 tentativas) e a perdedora encontra a família já revogada, como o contrato espera. Testado com um gerenciador de transações que simula o deadlock.

### Adicionado (item #18 do roadmap)
- **Monte Carlo e metas** (`#/montecarlo`, rotulado **projeção** em toda a cadeia): cone patrimonial (percentis 5, 25, 50, 75 e 95 por mês), probabilidade de estar na meta no fim e ano a ano, probabilidade de terminar abaixo do que foi investido, distribuição do patrimônio final e tabela equivalente ao gráfico.
- **Dois modos:** premissas manuais (retorno esperado e volatilidade anuais, log-normal; `POST /api/risk/monte-carlo`, pública) e **minha carteira** (`POST /api/me/risk/monte-carlo`, com login): reamostragem por blocos de 21 pregões de retornos **reais alinhados por data**, o que preserva a correlação entre os ativos; pesos fixos pelo valor atual em reais, rebalanceados todo mês.
- **Cobertura explícita:** posição sem histórico real (renda fixa, fundos, ativo sem cotação, histórico curto ou sem datas em comum) fica parada no valor atual e a saída diz qual, por quê e quanto do patrimônio isso é; sem histórico suficiente, a rota recusa (422) e não estima nada. Ativos em dólar entram pelo retorno na moeda do ativo (o câmbio não entra, e a tela diz isso).
- **Reprodutível:** `SplittableRandom` com semente explícita; a mesma entrada dá a mesma projeção. **ADR 020** abre a exceção estreita à regra de aleatoriedade do ADR 004 (só em `domain.montecarlo`, com três regras ArchUnit).
- Testes de propriedade (determinismo, percentis que não se cruzam, volatilidade zero igual à fórmula fechada, meta certa e impossível, correlação preservada, alinhamento por data) e cenário de ponta a ponta com candles reais gravados.

## [0.4.0] - 2026-11-22

Milestone 4 — Interface Dark Luxury, Mapas & Home Broker. Reúne a interface do protótipo aprovado (entrada, Minha Carteira, mapa de ativos reais, cockpit e shopping financeiro) e a camada de segurança e LGPD que a acompanhou.

### Adicionado (item #17 do roadmap)
- **Cockpit com TradingView Lightweight Charts 4.2.3** (servido pelo projeto, licença Apache 2.0 e SHA-256 travado): candles, volume, SMA50, bandas de Bollinger (calculadas sobre os candles reais), zona de entrada, zona de realização, alvos e invalidação do motor como linhas de preço, atualização por tick só na última barra, períodos 1M, 3M, 6M e 1A, camadas ligáveis, **inspetor do candle**, **"Ver como tabela"** (últimas 30 barras) e **"Exportar imagem"**. Atribuição à TradingView por texto e link (o logotipo embutido injeta `<style>` e a CSP o bloqueia).
- **Boleta de simulação:** "Simular compra ou venda" leva ao simulador pré-ordem com o ativo escolhido. A plataforma não executa ordens.
- **"Usar minha carteira salva"** no simulador pré-ordem e no tradutor geopolítico: preenche as linhas com as posições reais em reais e diz quantas ficaram de fora por falta de valor em reais. Fecha a dívida 26.
- **Shopping financeiro** (`#/shopping`): os tipos de ativo da taxonomia lado a lado (indexador, rendimento, proteção do FGC, fonte do preço), com a referência oficial do Banco Central de cada indexador (Selic, CDI, IPCA) e atalhos para simular e para a carteira. **Sem ofertas de emissores**, e a tela diz isso: nenhuma taxa de produto é inventada. ADR 019.

### Corrigido (item #17)
- O eixo do gráfico (UTC) e o inspetor do candle (fuso do navegador) mostravam **dias diferentes** para os mercados da Ásia (HSI e Nikkei: 20/11 no eixo, 19/11 no inspetor). O tempo de cada barra passou a ser deslocado pelo fuso do navegador e formatado em UTC no gráfico, no inspetor e na tabela (achado do code review, medido no app real).
- Os arquivos estáticos (JS, CSS, HTML) agora são sempre revalidados pelo navegador (`Cache-Control: no-cache`): um deploy novo não deixa script antigo preso no cache (aconteceu na validação deste PR).
- A página **Mercados ficou sem lista desde o item #16**: o `mapa.js` declarava `renderList`, o mesmo nome de uma função do `app.js`, e a segunda definição sobrescrevia a primeira em silêncio (erro `Cannot read properties of null` no console). As funções do mapa ganharam o prefixo `mp` e um teste agora derruba o `verify` se dois scripts declararem o mesmo nome global.

### Adicionado (item #16 do roadmap)
- **Mapa de ativos reais** (`#/mapa`) com **Leaflet 1.9.4** servido pelo projeto e **sem mapa-base de terceiros**: contorno dos 27 estados do IBGE (dado público, local), marcadores só para imóveis com coordenada e a precisão à vista (localizado × aproximado por via), filtros por fundo, UF e tipo, lista com os imóveis sem coordenada e o motivo, estados pintados pela quantidade de imóveis e comparação de fundos. Nenhuma requisição a domínio de terceiros (verificado na rede), CSP inalterada, atribuição ao OpenStreetMap pelas coordenadas.
- **Agrupamento de marcadores** por proximidade em pixels (marcador numerado que amplia ao clicar, sem biblioteca): sem ele, 21 dos 32 imóveis ficavam cobertos por outro marcador no zoom inicial (achado do code review, medido no navegador); com ele, nenhum marcador fica coberto em nenhum zoom testado e a soma dos grupos e dos individuais é sempre 32.
- Testes de contrato do Leaflet, da licença e do contorno (27 estados, SHA-256) e de que toda coordenada publicada cai dentro do Brasil. ADR 018.

### Segurança (Issue #43)
- **Auditoria pelas listas de requisitos** (40 requisitos, 20 medidas e os erros comuns), com sonda no app real, em [docs/seguranca-checklist.md](docs/seguranca-checklist.md); roteiro de incidentes em [docs/resposta-a-incidentes.md](docs/resposta-a-incidentes.md).
- **Refresh token em cookie `HttpOnly`**, `SameSite=Strict`, restrito a `/api/auth` e `Secure` sob HTTPS; nenhum token no armazenamento do navegador (o access token fica só na memória da aba); renovação entre abas por Web Lock e login e logout propagados por BroadcastChannel. Clientes de API seguem recebendo o refresh no corpo. Fecha as dívidas 34 e 42 (parte).
- **HTTPS forçado** (`MW_FORCE_HTTPS`, 308 com host validado) e cookie `Secure` atrás de proxy confiável.
- **LGPD:** página de privacidade e termos de uso, aceite no cadastro, **exportação** dos dados (`GET /api/me/export`) e **eliminação** da conta (`POST /api/me/erase`, com o e-mail digitado como confirmação), testadas em memória e no PostgreSQL.
- **Varreduras que quebram o `verify`:** segredos versionados, SQL só parametrizado, nenhuma rota que receba URL (SSRF); revisão de dependências em todo PR.
- **Banco Central:** uma série que falhou na partida é repetida a cada 2 minutos até aparecer (dívida 40).

### Corrigido (Issue #43)
- Swagger e `/v3/api-docs` estavam abertos a qualquer pessoa e listavam todas as rotas: agora desligados por padrão (`MW_API_DOCS=true` liga).
- O corpo de erro devolvia a URL pedida e uma mensagem repetia a entrada do usuário: removidos.
- Os filtros de limite, autenticação, tamanho e cabeçalhos decidiam pelo texto cru da URL (`/api/%61uth/login` caía no limite geral): passaram a usar o caminho que o Spring despacha.

### Adicionado (item #15, etapa 2)
- **Entrada na interface:** diálogo com abas Entrar e Criar conta, **Continuar com Google** (botão oficial do Google Identity Services quando `MW_GOOGLE_CLIENT_ID` está definido; desabilitado com aviso claro quando não está; carregando e erro tratados) e sessão de demonstração; menu da conta com sair; renovação do token por refresh em uma chamada só, compartilhada.
- **Minha Carteira** (`#/carteira`): patrimônio, alocação (treemap), aderência ao perfil com radar, posições, compra, venda, ajuste, baixa e abertura pelos formulários da tela, livro com filtro e ganho ou perda por posição. Só dado real, "não informado" quando falta dado, tema escuro e Ivory, sem rolagem horizontal em 375 px.
- **Montador de perfil:** o questionário de suitability passa a oferecer "Salvar como meu perfil"; contas sem perfil recebem um aviso e, depois do primeiro cadastro, são levadas ao questionário.
- **Apache ECharts 5.5.1** servido pelo projeto (`/vendor`, com licença e NOTICE; SHA-256 travado em teste), sem CDN. `GET /api/auth/config` passa a informar `googleClientId`; a CSP libera o domínio do Google Identity Services **só** quando o Google está configurado. ADR 017.

## [0.3.0] - 2026-11-21

Milestone 3 — Segurança, Persistência & Carteira. Reúne os itens #11 a #14 abaixo: autenticação por JWT com Login com Google e defesas de borda, carteira persistida com preço médio ponderado e bloqueio otimista, livro de movimentações com `Idempotency-Key` e apoio ao imposto de renda, e observabilidade com fronteiras de módulo verificadas.

### Adicionado (item #14 do roadmap)
- **Correlation-ID:** cada requisição recebe um identificador (aceita `X-Correlation-Id` só no formato `[A-Za-z0-9._-]{8,64}`, contra injeção em log; senão gera um UUID), devolvido no cabeçalho e presente em toda linha de log; uma linha por requisição de `/api/**` com método, caminho, status e duração.
- **Métricas Prometheus por provedor e por cache** (`/actuator/prometheus`): chamadas por resultado (sucesso, falha, circuito aberto, limite local), estado do circuito, provedor habilitado, latência por tentativa e acertos/erros do cache, para acompanhar o consumo das cotas gratuitas. Lidas do mesmo `ProviderStatus` da tela de status.
- **Health em grupos:** `liveness` e `readiness` sem dependência de provedor externo; grupo `dependencies` (ativos com cotação ao vivo, indicadores do Banco Central em dia, circuitos abertos) com o estado `DEGRADED` (HTTP 200).
- **Métricas fechadas por token:** `/actuator/metrics` e `/actuator/prometheus` respondem 404 sem `MW_METRICS_TOKEN` e exigem `Authorization: Bearer` com ele (comparação em tempo constante; token com menos de 24 caracteres impede a partida).
- **Fronteiras entre módulos verificadas** (ArchUnit, `ModuleBoundariesTest`): cada módulo de negócio declara de quais pode depender, sem ciclos, e o núcleo compartilhado e os serviços de mercado não conhecem a carteira. ADR 016: monólito modular, com os gatilhos medidos para extrair um serviço. `docs/observabilidade.md` com as consultas PromQL.

### Alterado (item #14)
- `MarketService` com escritor único e mapa imutável de visões: as leituras (visão geral, candles, avaliação, sinais, backtest) não esperam mais a atualização de mercado. Fecha a dívida 3 (a 4 já estava resolvida no PR #2). Teste: leituras abaixo de 100 ms durante uma atualização de mais de 250 ms (falha no código anterior).

### Adicionado (item #13 do roadmap)
- **Livro de movimentações** (`V3__portfolio_transactions.sql`): `ledger_entries` só acrescenta (gatilho bloqueia UPDATE), com abertura, compra, venda, ajuste manual e baixa. A posição é a dobra do livro e muda **na mesma transação** que grava a entrada; falha em qualquer das duas desfaz as duas. A migração preenche o livro das posições do item #12.
- **Taxas, resultado realizado e cronologia:** as taxas entram no custo da compra e abatem a venda; cada venda registra o resultado realizado na moeda da posição e em reais; a data da operação não pode estar no futuro nem antes da última do ativo.
- **Custo em reais ao câmbio de cada compra** (PTAX do Banco Central do último dia útil até a data, ou o câmbio da nota informado): o resultado não realizado em reais passa a existir e inclui a variação cambial. Fecha a dívida 35. Sem câmbio, a operação em dólar é recusada; posição herdada sem câmbio fica "não informado".
- **`Idempotency-Key`** em todas as escritas: repetir devolve a mesma entrada sem aplicar de novo (`Idempotent-Replay: true`), a mesma chave com outro conteúdo é 422 e duplicatas simultâneas aplicam uma só vez (índice único por usuário e chave, sem tabela extra).
- **Eventos de domínio** (posição aberta, compra, venda, ajuste, baixa) publicados depois do commit e nunca numa repetição, por um barramento em processo com assinantes isolados e um contador Micrometer por tipo.
- **Apoio ao IR** (`GET /api/me/tax/report?year=`): posição e custo em reais em 31/12, resultado realizado por mês e classe, vendas de ações da B3 no mês contra o limite de R$ 20.000,00 e sinalização de possível day trade. Base legal lida no Planalto em 2026-11-21 (Lei 11.033/2004, art. 3º, I; art. 2º, I e II; art. 3º, III e § 1º) com a ressalva da MP 1.303/2025; **não calcula imposto devido, DARF nem compensação de prejuízos** e lista tudo que não apura.
- `GET /api/me/portfolio/transactions` com filtros por ativo e datas e paginação; ADR 015.

### Corrigido (item #13)
- Uma importação de histórico em dólar falhava com "PTAX indisponível" mesmo com o Banco Central no ar (a consulta interativa dividia o balde de 8 fichas das consultas de fundo e não esperava; 14 compras em 1,2 s deram 8 x 200 e 6 x 503), e uma venda inválida em dólar virava 503 em vez de 400 porque o câmbio era buscado antes de validar. Agora a operação é validada antes do câmbio, o PTAX de datas passadas fica em cache por data e a consulta interativa espera até 5 s por uma ficha (achado do code review; 14 compras: 14 x 200, venda inválida: 400 imediato).
- O ano padrão do relatório de IR era o do fuso do servidor, enquanto o serviço valida pelo ano de Brasília: entre 21h e meia-noite de 31/12 (em UTC já é o ano seguinte) o relatório sem `year` responderia 400. Quem decide o ano corrente agora é o serviço, em Brasília. Os testes de data passaram a usar Brasília (a CI roda em UTC e pegou a diferença).
- O relatório de IR deixou de varrer a lista inteira de entradas a cada venda para achar a compra do mesmo dia.
- O hash do conteúdo da requisição montava a string de `1E999999999` (um gigabyte) antes de validar a magnitude e derrubava o servidor por falta de memória; a ordem de grandeza passou a ser conferida antes.

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
