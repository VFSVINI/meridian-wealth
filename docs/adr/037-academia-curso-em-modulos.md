# ADR 037 — Academia: curso em módulos com quiz e progresso no navegador

- **Status:** aceito
- **Data:** 2026-11-28
- **Issue:** #90 (item #49 do roadmap)

## Contexto

O dono achou o glossário em forma de lista "horrível" e pediu um curso por módulos para quem nunca estudou investimentos. O protótipo Cosmos traz a Academia: nove módulos em grade, "n de N capítulos" em cada um e o cartão "Seu progresso".

## Decisão

1. **Conteúdo editorial em `data/academia.json`** (9 módulos, 26 capítulos), servido como arquivo estático e lido por `academia.js`: Fundamentos, Renda variável, Renda fixa (2 capítulos), FIIs, Cripto, Câmbio e commodities, Indicadores técnicos, Risco e carteira e Macro e geopolítica. Cada capítulo tem ideia central em dois ou três blocos, diagrama, exemplo, "por que importa para o seu dinheiro" e mini-quiz com explicação.
2. **Exemplos didáticos, nunca dado de mercado:** todo exemplo se chama "Exemplo hipotético" e as contas são conferidas por teste (juros compostos de R$ 1.000 a 10% por 10 anos, juro real, IPCA+, P/L, P/VP, diversificação, concentração, queda de 50% que exige alta de 100%). Nenhum preço, taxa ou indicador de mercado é afirmado como atual. Um teste varre o texto que o curso afirma e recusa promessa de retorno e recomendação.
3. **Diagramas sem biblioteca:** fluxos, colunas, barras, faixas e escalas em HTML e CSS (leitura por teclado e leitor de tela), mais três SVG (candle, dispersão de risco e retorno, e as curvas de juros simples e compostos, calculadas na hora).
4. **Progresso só no navegador:** capítulos concluídos e respostas em `localStorage` (`mw-academia`), em `try/catch`, sem conta e sem enviar nada ao servidor. O capítulo conclui quando todas as perguntas do quiz foram respondidas certo; o cartão mostra o percentual. As opções do quiz são embaralhadas de forma determinística por capítulo (a resposta certa não fica sempre na primeira).
5. **Rotas** `#/academia`, `#/academia/m3` e `#/academia/m3/m3c1`. O menu "Entender" passa a ter a Academia (o glossário saiu do menu e continua em "Abrir o glossário de termos", na própria Academia, e nos botões "?" de cada indicador). Os botões "Ver a Academia" e "Abrir Academia" do Início levam a ela.

## Consequências

- Mudar o curso é editar o JSON, sem tocar no código; o teste de conteúdo protege a estrutura, as contas e o tom.
- Quem trocar de navegador ou limpar os dados perde o progresso (é o preço de não ter conta); a tela avisa.
- Os textos de cada capítulo são curtos de propósito. O curso não substitui orientação profissional.

## Alternativas descartadas

- **Guardar o progresso na conta:** exigiria login para aprender; o protótipo diz que fica no navegador.
- **Conteúdo escrito em JavaScript:** misturaria texto e código e dificultaria revisar e testar o conteúdo.
- **Vídeos e certificados:** fora do escopo e sem como garantir o conteúdo de terceiros.
