# ADR 031 — Correlação em 3D com célula sem dado nunca como zero

- **Status:** aceito
- **Data:** 2026-11-24
- **Issue:** #76 (item #42 do roadmap)

## Contexto

A página de correlação (ADR 021) mostra a matriz de Pearson entre os ativos da carteira como tabela, com a regra mais importante do item: célula sem pregões em comum suficientes é **"não informado", nunca zero**, porque zero pareceria "sem relação". Um gráfico de barras 3D tem um risco próprio: uma barra de altura zero é visualmente igual a correlação nula.

## Decisão

1. **Barras sobre a mesma matriz da tabela** (`correlacao3d.js`), recebida pelo evento `mw:cr-data` que o `correlacao.js` emite depois de desenhar a tabela (e `mw:cr-need` ao carregar). O módulo não busca, não sorteia e não anima dado.
2. **Altura = correlação:** positiva sobe em verde, negativa desce em vermelho (tokens `--up` e `--down`), na mesma escala; a diagonal (1,0) é a mais alta.
3. **Célula sem dado = marcador cinza achatado em outra malha**, sem barra; o tooltip diz "não informado" e quantos pregões em comum havia contra o mínimo exigido. A barra tem altura mínima só para correlação real. Um teste confere o ramo, e foi exercitado no navegador com uma matriz que tinha células nulas.
4. **Estimativa frágil** (menos de 120 pregões em comum) é dita no tooltip, como na tabela.
5. **Kit compartilhado:** órbita (`createOrbit`), rótulos (`makeLabel`) e liberação de GPU (`disposeGroup`), com pausa fora da tela e perda de contexto tratadas. A tabela fica logo acima como alternativa em texto, e o canvas é focável, com `aria-label` que explica a leitura.

## Consequências

- Com muitos ativos (dezenas) as barras ficam pequenas e os rótulos se encurtam em 14 caracteres; a câmera se afasta pela quantidade de ativos. Carteiras muito grandes não foram testadas.
- O card só aparece quando a API devolve uma matriz de ativos (duas ou mais posições com histórico real).
- Não foi testado em dispositivo real.

## Alternativas descartadas

- **Barra de altura zero para "não informado":** é exatamente o que o ADR 021 proíbe, em forma gráfica.
- **Mapa de calor 3D contínuo:** suavizaria entre ativos que não têm relação de vizinhança; a matriz é discreta.
