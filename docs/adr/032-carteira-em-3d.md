# ADR 032 — Minha Carteira em 3D: rosca de alocação sem guardar dado depois do logout

- **Status:** aceito
- **Data:** 2026-11-24
- **Issue:** #78 (item #43 do roadmap)

## Contexto

Minha Carteira mostra a alocação como lista, treemap (ECharts) e tabela de posições, todos com dado real da carteira salva do usuário. O dono pediu o bloco 3D. A carteira é dado pessoal: o projeto trata exportação, exclusão e sessão com cuidado (LGPD). A revisão do #42 mostrou que um módulo 3D pode manter a matriz do usuário na memória depois do logout.

## Decisão

1. **Rosca 3D** (`carteira3d.js`): o anel de fora são as **classes** (os percentuais exatos que a API devolve); o anel de dentro são as **posições**, com o peso no valor em reais de hoje. Só entram posições com valor em reais; as demais ficam fora e o texto do card e o `aria-label` dizem isso. Cada anel é normalizado pela própria soma e fecha em 100% (medido: 100,00 e 100,00). As cores das classes são as mesmas do treemap (a paleta `CAT_COL` vai no evento).
2. **Mesmos números das alternativas:** o módulo consome o evento `mw:ct-data`, que o `carteira.js` emite depois de desenhar a lista e a tabela; não busca, não sorteia e não anima dado (teste). Passar o mouse numa fatia só a **afasta alguns centésimos** (efeito visual) e mostra o texto; nenhum número muda.
3. **Privacidade:** quando a página mostra a tela de entrada (`gate()`), o `carteira.js` emite `mw:ct-clear`; o módulo esquece a carteira, esvazia a cena (`disposeGroup`), esconde o card e troca o `aria-label` por um genérico. Um novo login monta tudo do zero (medido com outra conta de 1 posição). O 3D nunca persiste nada (sem `localStorage`).
4. **Kit compartilhado:** órbita, rótulos, liberação de GPU, pausa fora da tela e perda de contexto tratada; a montagem não roda com o contexto perdido.
5. **Alternativas:** lista de classes, treemap 2D e tabela de posições continuam; o canvas é focável e tem `aria-label` com as classes e os percentuais.

## Consequências

- Carteiras com muitas posições têm fatias pequenas: rótulos só nas fatias de 7% ou mais, o resto pelo tooltip.
- O valor em reais aparece no tooltip das posições (é da própria carteira do usuário, já visível na tabela).
- Não foi testado em dispositivo real.

## Alternativas descartadas

- **Altura proporcional ao peso:** duplicaria o percentual numa segunda dimensão e dificultaria comparar fatias; a altura é fixa e só o ângulo carrega o dado.
- **Substituir o treemap 2D:** o 2D segue como leitura precisa e alternativa de acesso.
