# ADR 019 — Cockpit com Lightweight Charts e shopping financeiro sem ofertas inventadas

- **Status:** aceito
- **Data:** 2026-11-22
- **Issue:** #47 (item #17 do roadmap)

## Contexto

O roadmap pede, no item #17, um "shopping financeiro multi-classe" e um "cockpit home broker (Lightweight Charts, paleta de comandos)". O cockpit já existia com um gráfico em canvas próprio (linha de fechamento, Bollinger e níveis), e a paleta de comandos (Ctrl+K) já existe desde a etapa 1 do #15. O protótipo aprovado traz o cockpit completo (candles, volume, SMA50, zonas, inspetor do candle, "Ver como tabela", "Exportar imagem", "Simular compra ou venda"), mas **não traz tela de shopping**. E o projeto não tem ofertas reais de emissores (taxas de CDB, LCI, debêntures): mostrá-las seria inventar dado, o que a regra de ouro do projeto proíbe.

## Decisão

1. **Cockpit em TradingView Lightweight Charts 4.2.3** (Apache-2.0), servido pelo projeto (`static/vendor`, licença ao lado, SHA-256 travado em `VendorContractTest`), sem CDN. Candles, volume, SMA50 e as três bandas de Bollinger são calculados no navegador **a partir dos candles reais** (mesma conta do motor: 20 barras e 2σ; 50 barras); a zona de entrada, a zona de realização, os alvos e a invalidação do motor de timing viram linhas de preço rotuladas. Um tick altera só a última barra (`update`), sem refazer a série. O gráfico em canvas saiu (`app.js` perdeu `draw()` e o estado de hover; `cockpit.js` define `draw()`).
2. **Acessibilidade e honestidade:** cor nunca é o único sinal (rótulos nas linhas, legenda, inspetor do candle com os números), "Ver como tabela" mostra as últimas 30 barras, "Exportar imagem" baixa o gráfico (`takeScreenshot`), períodos 1M, 3M, 6M e 1A e camadas ligáveis.
3. **Atribuição à TradingView por texto e link** na página, e o logotipo embutido da biblioteca fica desligado (`attributionLogo: false`): ele injeta um elemento `<style>`, que a CSP (`style-src 'self'`) bloqueia, e relaxar a CSP por um logotipo não vale a troca.
4. **Boleta de simulação, sem ordens:** "Simular compra ou venda" leva ao simulador pré-ordem com o ativo do cockpit já escolhido. A plataforma continua sem executar ordens.
5. **Dívida 26 resolvida:** o simulador pré-ordem e o tradutor geopolítico ganham "Usar minha carteira salva", que preenche as linhas com as posições reais em reais; posição sem valor em reais fica de fora e a tela diz quantas.
6. **Shopping financeiro = catálogo e referências oficiais.** `#/shopping` compara os tipos de ativo da taxonomia (indexador, rendimento, proteção do FGC, fonte do preço) e mostra, para cada indexador, a referência oficial do Banco Central (Selic, CDI anualizado, IPCA em 12 meses), deixando escrito que a taxa do produto não existe na plataforma ("a plataforma não a tem"). A tela afirma, em destaque, que não há ofertas de emissores. Os atalhos levam ao simulador e à abertura de posição; tipos de mercado (ação, ETF, FII) levam ao painel.
7. **Nomes globais únicos:** os scripts compartilham o escopo global e um nome repetido em outro arquivo sobrescreve o primeiro em silêncio. O `mapa.js` do item #16 sobrescreveu `renderList` do `app.js` e quebrou a lista da página Mercados; todas as funções do mapa ganharam o prefixo `mp` e `CspReadinessTest.noTwoScriptsDeclareTheSameGlobalName` passou a derrubar o `verify` se isso se repetir.

## Consequências

- Perde-se o logotipo da TradingView no canto do gráfico; o crédito segue visível, com link, abaixo dele.
- O catálogo é pobre de propósito: sem ofertas de emissores não há taxa de produto a comparar. Se um dia houver fonte real (por exemplo, os preços e taxas do Tesouro Direto nos dados abertos da STN), o catálogo ganha uma coluna com a fonte e a data.
- As séries do cockpit dependem dos candles que a API já entrega (até 400 barras); períodos além disso mostram o que existe.

## Alternativas descartadas

- **Manter o canvas próprio:** sem zoom, sem pan, sem inspetor nativo e com mais código para manter.
- **Liberar `style-src 'unsafe-inline'` para o logotipo:** abre a CSP inteira por um adorno.
- **Mostrar taxas "de exemplo" de CDB e LCI:** é dado inventado.
