# ADR 028 — Módulo 3D compartilhado e cockpit em 3D com o histórico real

- **Status:** aceito
- **Data:** 2026-11-24
- **Issue:** #70 (item #40 do roadmap)

## Contexto

O dono pediu gráficos de bolsa em 3D e "tudo o que for possível imaginar". O ADR 026 prometeu que, no segundo uso do three.js, o que é comum a toda cena (tamanho, pausa fora da tela, perda de contexto, movimento reduzido) viraria um módulo compartilhado. O cockpit é o segundo uso. A regra do projeto continua: só dado real, com fonte e horário (ADR 004), e nada de número animado (ADR 027).

## Decisão

1. **`kit3d.js`:** `createStage` cria o renderizador, a cena e a câmera; cuida do tamanho do palco, de **só desenhar o que se vê** (fora da tela, em outra página ou com a aba oculta nenhum evento gasta GPU), da perda e da restauração do contexto (com os ganchos `onLost` e `onRestore` para quem tem recursos na GPU) e do movimento reduzido; desenha **sob demanda** (`invalidate`) e mantém o laço só enquanto `onFrame` devolve verdadeiro. Só o kit cria `WebGLRenderer` (um teste confere). O hero foi migrado e passou pelas mesmas medições de antes.
2. **Cockpit em 3D** (`cockpit3d.js`): lê **os mesmos candles do gráfico 2D**, pelo evento `mw:ck-data` que o `cockpit.js` emite ao desenhar (as mesmas contas de Bollinger e SMA50); o módulo não busca, não sorteia e não anima dado (um teste proíbe `fetch`, `Math.random`, `setInterval` e `innerHTML`). Corpos e pavios são `InstancedMesh`; o volume é profundidade, em barras no piso, à frente; as escalas são sprites de texto. Câmera orbital com arrastar, inércia, botões e teclado; a roda só dá zoom com Ctrl ou com o gráfico focado, para o 3D não roubar a rolagem da página.
3. **Acoplamento por eventos:** `mw:ck-data` (cockpit para 3D), `mw:ck-need` (3D pede os dados ao ativar) e `mw:ck-hover` (3D para o cockpit, que atualiza o cartão "Inspetor do candle"). Sem depender de variáveis globais do `app.js`.
4. **Acessibilidade e alternativas:** o botão "Gráfico 3D" tem `aria-pressed`; o canvas é focável (`tabindex`), tem `role="img"` e um `aria-label` com ativo, período, número de barras, mínima e máxima; o 2D e a tabela continuam; a exportação de imagem fica desligada no 3D (vale para o 2D).
5. **Cores dos tokens do tema** (`--up`, `--down`, `--gold`, `--info`): os dois temas funcionam e a cena se refaz ao trocá-lo.

## Consequências

- Cada gráfico 3D novo (globo, correlação, carteira, Monte Carlo) reaproveita o kit e ganha pausa, perda de contexto e movimento reduzido sem repetir código.
- O terreno mostra até 400 barras (o limite que o cockpit já carrega). O uso de memória da GPU em celulares fracos não foi medido.
- Não foi testado em dispositivo real; a câmera por toque é só arrastar (sem pinça): o zoom no toque vem dos botões.

## Alternativas descartadas

- **Biblioteca de gráficos 3D (ECharts GL):** mais um download grande e um modelo de dados próprio; o three.js já está no projeto.
- **Ler as variáveis globais do cockpit:** acopla os arquivos pela ordem de carga; um evento é explícito.
- **Trocar o 2D pelo 3D:** o 2D (TradingView) segue sendo a leitura precisa; o 3D é uma segunda forma de ver os mesmos dados.
