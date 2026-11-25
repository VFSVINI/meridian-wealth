# ADR 034 — Catálogo de ativos negociados e abertura sob demanda com sinal do motor

- **Status:** aceito
- **Data:** 2026-11-25
- **Issue:** #82 (item #33 do roadmap)

## Contexto

O universo curado tem 33 ativos. O dono viu que o XPSF11, e quase tudo o que é negociado na B3 e nos EUA, não aparecia, e pediu que "se é negociado em bolsa a plataforma tem o dever de mostrar" e que esses ativos tivessem o sinal do motor. Acompanhar milhares de ativos o tempo todo é inviável no servidor gratuito (memória de 280 MB e limites dos provedores), então o catálogo precisa ser só cadastro, com preço, histórico e sinal buscados quando o ativo é aberto.

## Decisão

1. **Catálogo só de cadastro** (`catalog/assets.tsv`, 14 013 ativos, 934 KB, gerado por `scripts/build-catalog.py`): símbolo, nome, classe, bolsa, setor e tipo. **Nenhum preço no arquivo** (teste). Fontes, baixadas em 2026-11-25 com a permissão do dono: Nasdaq Trader (`nasdaqlisted.txt`, 348 KB, e `otherlisted.txt`, 545 KB; ações e ETFs dos EUA, sem emissões de teste, warrants, direitos e preferenciais) e a lista de cotações da brapi (2 326 ativos da B3: ações, units, BDRs, ETFs e fundos listados; a brapi é um agregador, não o arquivo da B3, e só se usa o símbolo e o nome). Para regenerar basta rodar o script com as listas baixadas.
2. **Prioridade do universo curado:** se um símbolo existe nos dois (BHP, BTC, ETH e outros), vale o curado. Os fundos listados que não são FII (Fiagro, FI-Infra, FIP, FIDC) entram com a classe "FII" para o motor e com o tipo real no catálogo.
3. **Busca** (`GET /api/catalog/search`): símbolo ou nome, sem acento e sem diferenciar maiúscula; ordem símbolo igual, símbolo que começa, nome que começa, nome que contém; a B3 antes dos EUA em cada grupo; só cadastro (nenhum preço).
4. **Abertura sob demanda** (`POST /api/catalog/{símbolo}/track`): o `MarketService.track` busca o histórico real fora do lock, instala a série e passa a atualizá-la a cada ciclo; **com barras suficientes o motor calcula o sinal como o dos demais**, com benchmark IBOV (ações da B3) ou SPY (ações dos EUA). Sem dado real o ativo **não entra** (503, "nada foi inventado"). Até 40 ativos sob demanda: ao passar disso sai o aberto há mais tempo, e o curado nunca sai. A rota tem o limite baixo das rotas pesadas (cada chamada vai a provedores externos); a busca não.
5. **App:** o seletor de ativo e o Ctrl+K buscam no catálogo depois de uma pausa na digitação (resposta velha nunca sobrescreve a nova), marcam "catálogo" e abrem o ativo com o aviso na tela quando não há dado real.
6. **Correção que o catálogo exigiu:** antes da abertura do pregão a Yahoo manda a barra do dia com abertura e mínima 0 (medido em HGLG11 e XPSF11); o parser aceitava o zero e a tela calcularia variação infinita. Agora preço não positivo vira o fechamento e a barra do dia sem abertura real não é aproveitada.

## Consequências

- Um ativo só tem sinal enquanto está acompanhado: quem nunca foi aberto mostra cadastro e, ao abrir, preço, gráfico e sinal. Uma varredura em lote do catálogo inteiro ficou fora (custo e limites dos provedores).
- Ativos fora do universo curado não têm fundamentos (Finnhub só cobre os curados) nem composição e mapa dos FIIs (item #34).
- Nem todo ativo do catálogo tem dado grátis: se nenhum provedor responde, a tela diz isso.

## Alternativas descartadas

- **Acompanhar todo o catálogo desde a partida:** milhares de chamadas a cada ciclo, estouraria memória e limites.
- **Consultar o provedor a cada digitação na busca:** lento e sujeito a bloqueio; o cadastro local responde na hora.
- **Guardar o preço do dia no catálogo:** ficaria velho; preço só ao vivo, com fonte e horário.
