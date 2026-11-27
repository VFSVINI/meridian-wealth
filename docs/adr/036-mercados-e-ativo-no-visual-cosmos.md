# ADR 036 — Mercados e Ativo no visual Cosmos

- **Status:** aceito
- **Data:** 2026-11-27
- **Issue:** #88 (item #48 do roadmap)

## Contexto

O protótipo Cosmos pede uma página de Mercados que responda "como estão os mercados agora?" e uma página de Ativo que responda "o que o gráfico diz?", com a leitura simples primeiro e o detalhe só quando a pessoa quiser. As duas páginas existiam como tabela e cockpit densos. Os dados do protótipo são de "exemplo"; aqui tudo precisa ser real (ADR 004).

## Decisão

1. **Mercados** (`mercados.js`): busca, pílulas por classe com contagem (Ações BR, FIIs, Ações EUA, Ações do mundo, ETFs, Cripto, Câmbio, Commodities e Índices), três visões (Lista, Tabela e Mapa) e, na Lista, a coluna de ativos com mini-gráfico ao lado do ativo em destaque (candles, selos de frescor e fonte, leitura em uma frase, RSI, volatilidade e média de 50 dias). O destaque **troca sozinho a cada 7 segundos**, com barra de progresso e "Pausar troca"; escolher um ativo pausa a troca; **com movimento reduzido ela não existe**. A troca só muda qual ativo está em destaque, nenhum número é animado. A visão Mapa é o globo 3D de pregões (o ADR 029); a Tabela é a antiga tabela, que também é a alternativa em texto. Os blocos provisórios da página inicial (sinais, maiores movimentos, notícias e atalhos) saíram; as manchetes voltam em Geopolítica (item #51).
2. **API** `GET /api/sparklines?bars=30` (5 a 120): os fechamentos recentes de todos os ativos acompanhados numa chamada, para os mini-gráficos. Só dado do snapshot: ativo sem barras fica de fora, nunca com série inventada. O candle do destaque vem de `/api/candles`, com cache de 55 s; variação sem abertura positiva é "não informado", nunca infinito.
3. **Ativo** (`ativo.js` e `cockpit.js`): título "SÍMBOLO: o que o gráfico diz?", chips para trocar de ativo, o cartão **"A leitura em uma frase"** (sinal e frase do motor, confiança em Baixa, Média ou Alta, "por quê" com o primeiro fator a favor e o primeiro contra do próprio motor, e "o que mudaria essa leitura" com o alvo e a invalidação reais), o gráfico em **Área** ou **Velas** com períodos e camadas independentes de Entrada, Alvos e Invalidação (Bollinger, média de 50 e 3D como camadas técnicas, desligadas por padrão), "Conheça o ativo antes de decidir" em cinco abas e **"Ver análise completa"** recolhido, que guarda tudo o que o cockpit já tinha (fatores, backtest, timing, inspetor, métricas, microeconomia e histórico de sinais).
4. **Conheça o ativo:** os textos de classe (o que é uma ação, um FII, um ETF...) são educativos e gerais; nada é dito sobre uma empresa ou fundo que a plataforma não saiba. "Do que é feito" usa a composição real dos FIIs (CVM, ADR 035) e diz "não informado" nos demais. Riscos, exposição e métricas usam os números do motor.

## Consequências

- O protótipo mostra "REDUZIR" com texto de uma empresa; aqui a frase vem do sinal do motor e é a mesma para qualquer ativo naquele sinal.
- Um ativo recém-aberto do catálogo aparece na lista assim que o servidor passa a acompanhá-lo.
- O cockpit antigo continua inteiro, só que atrás de "Ver análise completa".

## Alternativas descartadas

- **Um segundo Lightweight Charts na lista:** custo de instâncias e de eventos; os candles do destaque são um SVG leve, e o gráfico completo fica no Ativo.
- **Buscar o histórico de cada linha da lista:** dezenas de chamadas; o endpoint de sparklines faz uma só.
