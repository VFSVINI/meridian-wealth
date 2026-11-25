# ADR 033 — Cosmos: o protótipo aprovado vira a fundação visual (fontes, tokens, estrelas, shell e Início)

- **Status:** aceito
- **Data:** 2026-11-24
- **Issue:** #80 (item #47 do roadmap, Milestone 8)

## Contexto

Depois de ver o site no ar, o dono achou que havia informação demais, que as telas pareciam listas e que a primeira página brincava em vez de explicar. Ele gerou no Claude Design o protótipo "Cosmos" (18 telas, em `docs/design/cosmos/`) e pediu fidelidade total: layout, gráficos, transições e tipografia. O protótipo substitui o "Private Banking Dark Luxury" do ADR 012 como referência visual.

## Decisão

1. **Camada Cosmos** (`cosmos.css`, carregada depois do `app.css`): troca os tokens (fundo `#04070e`, âmbar `#ffb340`, ciano `#55d6ff`, verde `#38dd9c` e coral `#ff6d82` só para alta e baixa), os raios, a barra superior, o menu e o rodapé. O tema Ivory ganha os mesmos papéis semânticos. As páginas seguintes (itens #48 a #56) apoiam-se nestes tokens.
2. **Tipografia** (SIL OFL 1.1, servida pelo projeto, subconjunto latino, licenças em `/fonts`): **Big Shoulders Display** (títulos de cinema, variável, 35 436 B), **Inter** (texto, variável, 48 256 B) e **IBM Plex Mono** (números, já no projeto). Baixadas do Google Fonts em 2026-11-24 com a permissão do dono; o hash SHA-256 dos dois arquivos novos está fixado em teste. Cormorant Garamond e Figtree, do protótipo anterior, saíram do projeto.
3. **Céu vivo** (`stars.js`): estrelas em três profundidades que **derivam e reagem ao ponteiro** (paralaxe e um leve afastamento perto do cursor), **nebulosas** que orbitam devagar e o **salto de velocidade da luz** entre páginas: o roteador dispara `mw:warp`, as estrelas viram riscos que partem do centro e a página nova chega clareando (`irisIn` redefinido, 0,6 s). É ambiente puro: não desenha nem escreve dado (ADR 004), usa um canvas fixo a no máximo 30 quadros por segundo, pausa com a aba oculta e, com `prefers-reduced-motion`, fica uma imagem parada (sem deriva, reação nem salto); o Ivory não tem céu. Pedido do dono: o fundo não pode ficar parado, é o que dá o clima do protótipo.
4. **Shell**: menu em seções (Explorar, Entender, Simular, Mais) como no protótipo; páginas ainda sem backend aparecem como "em breve" com o item do roadmap no `title`; rodapé legal fixo ("Não é ordem nem recomendação de investimento").
5. **Início = apresentação**: título "Entenda o mercado antes de decidir", três passos e "por que confiar", sem moeda 3D e sem texto de instrução ("arraste"). O cartão "A leitura em uma frase" do protótipo, que ali é "exemplo", aqui é **real**: ativo com cotação e sinal calculados (PETR4 quando houver), preço, variação, fonte e horário reais, gráfico dos últimos 60 fechamentos e a tradução do sinal do motor em uma frase. Sem dado, o cartão diz isso.
6. **Choque assumido com o protótipo:** onde ele mostra números de "exemplo" ou ticks simulados, o produto mostra dado real com fonte ou "não informado" (ADR 004). A saudação por horário e o relógio da página inicial saíram, porque o protótipo não os tem. O globo de pregões, os sinais do dia, os maiores movimentos e as notícias passaram para o fim de Mercados até a repaginação (item #48) e o Mapa (item #53).

## Consequências

- O `hero3d.js` e o seu teste saíram; o `three.js` e o `kit3d.js` seguem nos gráficos 3D das demais páginas.
- Páginas que o protótipo ainda vai redesenhar herdam fontes, cores e títulos novos, mas mantêm o layout antigo até o seu item.
- O menu mostra "Cripto 24h", "Oportunidades" e "Academia" desabilitados até os itens #54, #55 e #49.

## Alternativas descartadas

- **Reescrever o `app.css`:** arriscaria as páginas que ainda não foram repaginadas. A camada por cima permite migrar página a página, com testes de contrato em cada passo.
- **Estrelas em WebGL (three.js):** custo de GPU sem ganho; o canvas 2D basta e pausa com facilidade.
- **Manter o hero 3D ao lado do novo Início:** o dono pediu explicitamente que a moeda saísse ("se colocar algo para o usuário interagir, deixe que ele se surpreenda").
