# ADR 029 — Globo 3D dos mercados com o estado real do servidor

- **Status:** aceito
- **Data:** 2026-11-24
- **Issue:** #72 (item #41 do roadmap)

## Contexto

O dono pediu "gráficos de bolsa e tudo o que for possível" em 3D. O projeto já calcula no servidor se cada bolsa está aberta, fechada ou em feriado (calendários oficiais, com o aviso de quais não foram verificados) e mostra isso numa linha de texto. Um globo é uma forma natural de ver "onde o mercado está aberto agora", sem inventar nenhum dado.

## Decisão

1. **O estado vem do servidor**, não é recalculado no navegador: o `app.js` já busca `/api/markets` a cada 30 s e passa o resultado pelo evento `mw:markets`; o globo só o consome. Como módulos executam depois do HTML, o primeiro evento sai antes de o módulo existir: o módulo **pede** o estado ao carregar (`mw:markets-need`).
2. **Geografia como fato, não como dado de mercado:** os continentes são o **Natural Earth 110m** (domínio público, `ne_110m_land.geojson`, 138.160 bytes, baixado com permissão do dono, conferido em duas origens e fixado por SHA-256 em teste, com o aviso ao lado). Os marcadores usam as coordenadas das cidades das bolsas; FX e cripto (24 h, sem cidade) são anéis em órbita, e um teste exige que toda bolsa do enum do servidor tenha cidade ou anel.
3. **Dia e noite reais:** a posição do Sol é calculada do horário UTC (declinação pelo dia do ano e longitude pela hora; sem a equação do tempo, erro de poucos graus, dito na tela). A noite escurece os pontos dos continentes e a base.
4. **Hora local = a da bolsa:** a API devolve a hora no fuso da bolsa (`2026-11-24T15:09:23+11:00`); a hora local dela é o `HH:MM` da própria string. Formatar com o fuso do navegador (ou UTC) mostrava hora errada, inclusive no `title` do relógio que já existia.
5. **Alternativa em texto e teclado:** o relógio de pregões virou botões (`aria-pressed`) que selecionam a bolsa no globo; o canvas é focável, tem `aria-label` com as bolsas abertas agora e aceita setas; o painel de seleção é uma região `aria-live`.
6. **Sem churn de GPU:** a cada atualização, se as bolsas são as mesmas, só se recolore (antes: ~170 buffers criados e destruídos por atualização). O globo só se monta e só baixa os continentes quando está à vista.

## Consequências

- O 3D usa o `kit3d.js`: pausa fora da tela (0 desenhos medidos), perda de contexto tratada e movimento reduzido.
- As coordenadas das cidades são de conhecimento geográfico geral, conferidas por teste só quanto à faixa válida; o posicionamento fino (cidade, não o endereço da bolsa) é aproximado e dito na tela.
- Calendário de feriados não verificado continua marcado com asterisco no texto, no tooltip e no painel.

## Alternativas descartadas

- **Recalcular o estado das bolsas no navegador:** duplicaria o calendário e poderia divergir do servidor.
- **Mapa-base de terceiros (tiles):** a CSP só aceita o próprio servidor, e o projeto escolheu não carregar mapa-base externo (ADR 018).
- **Globo sem continentes:** menos reconhecível; o dono autorizou o download.
