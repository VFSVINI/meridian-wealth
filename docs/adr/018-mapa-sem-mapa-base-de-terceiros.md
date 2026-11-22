# ADR 018 — Mapa de ativos reais com Leaflet e sem mapa-base de terceiros

- **Status:** aceito
- **Data:** 2026-11-22
- **Issue:** #45 (item #16 do roadmap)

## Contexto

O protótipo aprovado mostra um mapa com os imóveis dos fundos imobiliários. Um mapa "de verdade" costuma usar tiles de um provedor (OpenStreetMap, CARTO, Google): cada abertura da tela manda o IP e a região vista de quem navega ao provedor, exige liberar o domínio na CSP (`img-src`) e traz termos de uso próprios (a política de tiles do OpenStreetMap, por exemplo, limita o uso e exige atribuição). O projeto acabou de fechar a CSP, publicou uma política de privacidade que diz "nenhum recurso de terceiros" e se apoia em dado curado e verificável. Pediram a opção que traga mais segurança.

## Decisão

1. **Leaflet 1.9.4 servido pelo projeto** (`static/vendor`, licença BSD-2 ao lado, SHA-256 travado em `VendorContractTest`), como o ECharts: nada de CDN.
2. **Sem mapa-base de terceiros.** A base é o **contorno dos 27 estados do IBGE** (Malha Municipal, qualidade mínima, dado público, ~98 KB em `static/data/brasil-ufs.geojson`, com SHA-256 travado e teste de que tem exatamente os 27 códigos). O mapa não faz **nenhuma** requisição a outro domínio (verificado na rede do navegador), a CSP não ganha exceção e a política de privacidade continua verdadeira.
3. **Coroplético honesto:** a cor do estado é a quantidade de imóveis listados nele (contagem do dado curado); os marcadores são só dos imóveis **com coordenada**, e a precisão (endereço localizado × posição aproximada por via) está no desenho do marcador, na legenda e no popup. Imóvel sem coordenada fica na lista com o motivo e nunca ganha marcador.
4. **Só o que tem dado.** Os painéis de exemplo do protótipo que não têm fonte (valor por tipo de imóvel, cap rate por região, mapa do bairro, "valor de avaliação") não entram.
5. **Atribuição:** as coordenadas vieram do Nominatim do OpenStreetMap, então a tela e a lista de fontes trazem "© colaboradores do OpenStreetMap (ODbL)" e a fonte e a data de cada fundo.
6. **Segurança do popup:** todo texto do dado entra por `esc()` e os links externos (Satélite e Street View) levam `rel="noopener noreferrer"`; os marcadores são focáveis por teclado (`tabindex`, `role="button"`, título).

## Consequências

- Perde-se a leitura de ruas e bairros: o mapa mostra onde está o imóvel no país e no estado, não em que rua. Para isso há o link de Satélite e de Street View de cada imóvel (que abre o Google Maps por escolha do usuário, em outra aba).
- Se um dia houver motivo para ter tiles (zoom de bairro), é uma decisão nova: escolher o provedor, liberar o domínio na CSP, atualizar a política de privacidade e respeitar os termos dele. Um provedor próprio de tiles (PMTiles servido pelo projeto) seria a forma de manter o isolamento.
- O contorno é de qualidade mínima: serve ao país e ao estado; não serve para ler limites de município.
- O identificador global `L` já pertence ao código do gráfico de preço (`app.js`), então o Leaflet é lido de `window.L` em `mapa.js`.

## Alternativas descartadas

- **Tiles do OpenStreetMap ou da CARTO:** vazam o IP e a região vista, exigem exceção na CSP e têm termos de uso próprios.
- **Mapa em SVG próprio, sem Leaflet:** menos código de terceiros, mas sem zoom, arrasto, popups nem teclado prontos; o Leaflet é pequeno (~150 KB) e maduro.
