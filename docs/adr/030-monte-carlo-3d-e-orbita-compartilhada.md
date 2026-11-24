# ADR 030 — Monte Carlo em 3D e câmera orbital compartilhada

- **Status:** aceito
- **Data:** 2026-11-24
- **Issue:** #74 (item #44 do roadmap)

## Contexto

O Monte Carlo (ADR 020) mostra o cone patrimonial em 2D. O dono pediu o bloco 3D. A regra do projeto: o resultado de uma simulação é uma **projeção** e precisa estar rotulado como tal sempre; nenhum dado é inventado. A câmera orbital (arrastar com inércia, zoom, teclado, restaurar) já existia no cockpit 3D e este é o segundo gráfico a precisar dela (o terceiro, a correlação, vem a seguir): o ADR 028 pediu o módulo comum antes de repetir.

## Decisão

1. **Superfície 3D** (`montecarlo3d.js`): x = tempo, y = patrimônio, z = percentil. As **cinco curvas são os percentis exatos** da simulação (5, 25, 50, 75 e 95); a superfície só liga as curvas (nenhum percentil intermediário é inventado). O histograma do patrimônio final é uma parede no fim do cone e a meta é um plano, ambos **na mesma escala de patrimônio**. As cores vão do pior percentil ao melhor com os tokens do tema.
2. **Mesmo resultado do 2D:** o módulo consome o evento `mw:mc-data`, que o `montecarlo.js` emite com o `MC.data` que a API devolveu; não busca, não sorteia e não anima (teste). Pede o dado ao ativar (`mw:mc-need`).
3. **Projeção sempre à vista:** rótulo fixo na tela, no tooltip ("· projeção") e no `aria-label` ("É uma projeção, não uma promessa de retorno").
4. **`createOrbit` no `kit3d.js`:** a câmera orbital compartilhada (só o botão principal do mouse arrasta, a roda só dá zoom com Ctrl ou com o canvas focado, teclado, duplo clique restaura, ajuste à largura do palco, sem balanço nem inércia com movimento reduzido), mais `makeLabel` (rótulos de texto) e `disposeGroup` (libera a GPU, inclusive os buffers dos `InstancedMesh`). O cockpit foi migrado e refeito o conjunto de medições.
5. **Alternativas:** o botão "Cone 3D" alterna com o gráfico 2D (ECharts) e a tabela continua disponível.

## Consequências

- O próximo gráfico 3D (correlação) já nasce com órbita, rótulos, liberação de recursos, pausa fora da tela e tratamento de perda de contexto.
- A superfície com poucos pontos de percentil (cinco) é uma interpolação visual entre curvas reais; o tooltip mostra só os números reais de cada mês.
- Não foi testado em dispositivo real; a câmera por toque é só arrastar (zoom pelos botões).

## Alternativas descartadas

- **Mostrar trajetórias individuais em 3D:** a API não devolve trajetórias, só percentis e histograma; simulá-las no navegador seria inventar dado.
- **Duplicar o código de câmera no segundo gráfico:** é exatamente o que o ADR 028 evita.
