# ADR 027 — Transições e microinterações sem animar números

- **Status:** aceito
- **Data:** 2026-11-23
- **Issue:** #68 (item #45 do roadmap)

## Contexto

O dono pediu "mais efeitos 3D, telas de transição" e um site "repleto e completamente lindo". Os efeitos mais comuns em produtos financeiros incluem contadores de saldo e de preço que sobem de zero até o valor. Isso conflita com a regra mais forte do projeto (ADR 004): nenhum preço é simulado, interpolado ou animado, e o pulso visual só aparece quando chega uma cotação nova. Um contador que sobe de zero mostra valores que nunca existiram.

## Decisão

1. **Transição entre telas em camada visual**, não em troca de DOM: a página que perde o atributo `hidden` recebe a classe `enter` (revelação em círculo com leve rotação 3D e desfoque). O roteador continua trocando as páginas de forma síncrona, porque ECharts, Leaflet e Lightweight Charts medem o tamanho do contêiner na hora da troca; a API de transições de visualização (`startViewTransition`) adiaria a troca e quebraria esse contrato, e um teste proíbe seu uso no roteador.
2. **Inclinação 3D de cartões** por delegação de eventos (um listener no documento), só com mouse (`hover: hover` e `pointer: fine`), e **revelação ao rolar** só sob a classe `fx`, que o próprio JavaScript acrescenta: sem JavaScript, nada fica escondido.
3. **Nenhum número é animado.** O módulo não escreve texto em elemento nenhum (só classes e variáveis de CSS), e um teste proíbe `textContent =`, `innerText`, `innerHTML`, `nodeValue` e `requestAnimationFrame` no arquivo. Mini-gráficos que se desenham com o histórico real são permitidos (o quadro final é exatamente o dado); contadores não.
4. **Movimento reduzido:** com `prefers-reduced-motion`, o módulo nem inicia e o CSS desliga as animações.

## Consequências

- O `animationend` borbulha: o pulso de cotação nova de um filho pode terminar durante a transição; por isso a classe só sai com o fim da animação **da própria página** (nome `irisIn` e `target` igual à página). Foi achado medindo, não por leitura.
- Não foi testado em dispositivo real; o painel de pré-visualização oculto atrasa animações CSS, então a verificação visual do meio da transição é limitada.

## Alternativas descartadas

- **Contadores animados de preço e de patrimônio:** violam o ADR 004.
- **`document.startViewTransition` no roteador:** efeito bonito, mas adia a troca do DOM e quebra os gráficos que medem o contêiner.
- **Biblioteca de animação (GSAP e similares):** mais uma dependência para efeitos que CSS e 60 linhas resolvem.
