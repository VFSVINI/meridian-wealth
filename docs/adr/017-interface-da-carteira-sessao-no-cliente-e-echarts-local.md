# ADR 017 — Interface da carteira: sessão no cliente, ECharts local e Google na CSP

- **Status:** aceito
- **Data:** 2026-11-22
- **Issue:** #26 (item #15 do roadmap, etapa 2)

## Contexto

O backend de contas e carteira (itens #11 a #13) não tinha cliente. A tela precisa de login por senha, Google e demonstração, de gráficos
analíticos (alocação, radar, ganho por posição) e de escrever na carteira sem nunca perder precisão nem aplicar uma operação duas vezes.
A CSP do projeto (ADR 013) só aceita o próprio servidor, e a stack segue sem etapa de build (ADR 003).

## Decisão

1. **ECharts servido pelo projeto.** Apache ECharts 5.5.1 em `static/vendor/echarts.min.js`, com `LICENSE` e `NOTICE` ao lado e o SHA-256 travado em
   `VendorContractTest`. Nada vem de CDN. Sem etapa de build: o arquivo é o artefato de distribuição do próprio projeto. Só entram gráficos
   para os quais há dado (alocação, aderência, resultado por posição); "por corretora" e "do aporte ao saldo" do protótipo ficam de fora porque o
   modelo não guarda corretora nem aportes.
2. **Sessão no cliente.** `auth.js` guarda a sessão no `localStorage` e envolve o `fetch` com o Bearer; um 401 dispara uma renovação por refresh,
   única e compartilhada entre chamadas simultâneas, e a chamada é repetida uma vez. Refresh recusado encerra a sessão. É uma troca consciente
   (dívidas 34 e 42): a CSP sem script embutido é a defesa contra XSS, e um cookie `HttpOnly` fica para quando houver motivo.
3. **Google Identity Services só quando configurado.** O servidor expõe o `googleClientId` (público) em `/api/auth/config` apenas com
   `MW_GOOGLE_CLIENT_ID`, e só então a CSP ganha `script-src https://accounts.google.com/gsi/client`, `style-src .../gsi/style`, `connect-src` e
   `frame-src https://accounts.google.com/gsi/`. Sem a variável a CSP não menciona o Google e o botão fica desabilitado com aviso. O avatar do
   Google não é carregado (a interface mostra a inicial do nome), então nenhuma imagem externa é liberada. Os estados do botão: carregando,
   habilitado (botão oficial do Google), desabilitado e erro.
4. **Decimais exatos e escrita idempotente.** Os formulários enviam quantidade, preço, taxas e câmbio como o texto digitado (validado por
   expressão regular, sem passar por `double`), porque o servidor trabalha em `BigDecimal`. Cada formulário gera uma `Idempotency-Key` e a repete
   até a operação dar certo (uma falha de rede seguida de nova tentativa não duplica); conflito de versão e PTAX indisponível têm mensagem própria.
5. **A aderência reaproveita o endpoint do questionário.** O cliente agrupa o valor atual em reais por tipo e chama `POST /api/suitability/adherence`
   com o perfil salvo: nenhuma regra de negócio nova no JavaScript, só apresentação.

## Consequências

- O que não tem dado continua "não informado", na tabela e nos gráficos (posições sem cotação ou sem câmbio de compra ficam fora do gráfico, com a nota).
- O fluxo do botão do Google com um ID de cliente real não foi validado ao vivo (dívida 41); a CSP e o endpoint de config têm teste.
- A biblioteca de gráficos pesa cerca de 1 MB e só é usada na carteira; carregá-la sob demanda é uma otimização futura.

## Alternativas descartadas

- **Gráficos em SVG próprio:** menos recursos e mais código a manter, sem ganho de segurança.
- **ECharts por CDN:** quebraria a CSP e o princípio de nenhum recurso de terceiros.
- **Cookie `HttpOnly` agora:** exigiria CSRF e mudanças de CORS fora do escopo desta etapa.
