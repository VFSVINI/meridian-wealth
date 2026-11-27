# ADR 038 — Brasil: juros e inflação com histórico real do Banco Central

- **Status:** aceito
- **Data:** 2026-11-29
- **Issue:** #92 (item #50 do roadmap)

## Contexto

A página Brasil mostrava só o último valor de cada indicador, em texto. O protótipo Cosmos pede "o que os juros e a inflação fazem com o meu dinheiro?", com um gráfico de cada indicador, a explicação do que muda no bolso, o juro real e a linha do tempo das decisões. Os gráficos do protótipo são de "exemplo"; aqui tudo precisa ser dado oficial (ADR 004).

## Decisão

1. **Porta própria para o histórico** (`BrazilHistoryPort`, com o adaptador `BcbHistory` sobre o `BcbClient` já existente): a porta de leitura do último valor ficou como estava, e quem só precisa dela não ganha métodos que não usa. A enumeração `HistorySeries` lista as cinco séries do SGS usadas nos gráficos e foi conferida na API real em 2026-11-27: meta Selic (432, preenchida para a frente), CDI anualizado (4389), IPCA em 12 meses (13522), IPCA mensal (433) e o **dólar comercial de venda (série 1), que coincide com o PTAX de venda** (5,7446 nas duas fontes em 26/11/2026), no lugar do serviço Olinda, que só devolve uma cotação por chamada.
2. **`BrazilHistoryService`:** janela de 12 meses para os gráficos e de 24 meses para a meta Selic; **cache de 30 minutos** por série, **último dado bom** se o Banco Central falha (marcado como desatualizado depois de duas vezes o prazo) e **espera de 2 minutos depois de uma falha** antes de tentar de novo, para que uma série fora do ar não seja consultada a cada visita. O histórico nunca existe vazio nem fora de ordem (`RateHistory` recusa). O que não chegou aparece em `unavailable`; nada é estimado.
3. **Linha do tempo = mudanças reais da meta Selic**, derivadas da própria série 432 (ela só muda nas reuniões do Copom que alteram a meta). As reuniões em que a meta ficou igual não estão na série pública, e a tela diz isso em vez de inventá-las.
4. **Juro real** com `BigDecimal`: `(1 + taxa) ÷ (1 + IPCA de 12 meses) − 1`, em pontos percentuais com duas casas. A comparação traz Tesouro Selic (a meta Selic), CDB a 100% do CDI (o CDI anualizado), a inflação e a **poupança pela regra legal** (0,5% ao mês composto, cerca de 6,17% ao ano, quando a Selic está acima de 8,5%; 70% da Selic até isso; TR não incluída), com a regra escrita na tela. Tudo antes de imposto e taxas.
5. **Explicação "no seu bolso"** em três abas (Selic, Inflação e Dólar), com contas simples feitas sobre o valor real mais recente e rotuladas como contas simples.
6. **API** `GET /api/brazil/history` devolve os valores como texto exato; a lista completa de indicadores continua na página, dentro de "Todos os indicadores oficiais".

## Consequências

- A primeira visita depois de uma partida faz cinco consultas ao Banco Central (o limitador do cliente cobre isso); as seguintes saem do cache.
- O IPCA tem defasagem de publicação: o último mês mostrado é o da última divulgação, com a data de referência à vista.
- A poupança é calculada por regra, não consultada; se a regra legal mudar, a conta precisa mudar.

## Alternativas descartadas

- **PTAX pelo Olinda:** uma chamada por data, sem série; o SGS 1 traz o ano inteiro de uma vez e coincide com o PTAX de venda.
- **Listar todas as reuniões do Copom:** não há fonte pública em série para as que mantiveram a taxa; inventar "Copom mantém" violaria o ADR 004.
- **Juro real por subtração:** (taxa − inflação) engana; o cálculo correto é a razão.
