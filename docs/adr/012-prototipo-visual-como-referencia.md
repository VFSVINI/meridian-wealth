# ADR 012 — Protótipo visual como referência obrigatória da interface

- **Status:** aceito
- **Data:** 2026-11-21

## Contexto

O protótipo "MeridianWealth — protótipo e sistema de design" (18 páginas, estados de primeira classe, mobile, marca, shell e
notas de implementação) foi produzido no Claude Design a partir de `docs/prompt-claude-design.md` e aprovado pelo responsável
do produto, que determinou que a interface siga "a risca" o que ele mostra. O material recebido são **imagens** (PNG), sem código
nem arquivos de design: gráficos, mapas e dados do protótipo são ilustrativos (etiqueta "exemplo").

## Decisão

1. **O protótipo é a referência visual e de conteúdo da interface.** Tokens, tipografia, selos de honestidade, estrutura do shell
   (navbar, sidebar com grupos), páginas e estados seguem o que ele mostra; a transcrição em texto está em
   [design-prototipo.md](../design-prototipo.md).
2. **As regras do produto prevalecem sobre o desenho.** Onde o protótipo traz número de exemplo, o produto mostra dado real com
   fonte, horário e atraso, ou "não informado"; projeção continua rotulada; o aviso de que a plataforma não executa ordens fica
   visível.
3. **Adoção por etapas, dentro do roadmap:** o shell e o sistema de design entram no item #15 (protótipo visual + Minha
   Carteira), o mapa no #16 e o cockpit no #17. Páginas cujo backend ainda não existe (Monte Carlo, correlação, stress test,
   alertas) só são construídas quando o item correspondente existir; antes disso não há tela com dado inventado.
4. **Pilha:** o ADR 003 (frontend sem build) continua valendo. As bibliotecas citadas nas notas (Lightweight Charts, ECharts,
   Leaflet) entram como arquivos estáticos versionados, sem etapa de build. Mudar para uma pilha com build exige um ADR próprio.
5. **Fontes:** Cormorant Garamond, Figtree e IBM Plex Mono precisam ser servidas pelo próprio projeto (arquivos versionados)
   quando a política de segurança do item #11 (CSP) for aplicada; até lá, a pilha de fallback do protótipo (Georgia, Segoe UI,
   monoespaçada do sistema) é obrigatória.

## Consequências

- **Positivas:** uma única fonte de verdade visual; critério de aceite objetivo para os itens #15 a #17.
- **Negativas:** o protótipo existe só como imagem, então valores exatos de cor e medida vêm de leitura da imagem (a página
  "Sistema de design" lista os tokens em texto e é a referência para eles); as 8 imagens principais estão versionadas comprimidas em `docs/design/` e as
  demais ficam fora do repositório (peso).

## Alternativas rejeitadas

- **Reescrever a interface agora, fora do roadmap:** quebraria a regra de um item por PR e adiantaria telas sem backend.
- **Adotar React e Tailwind** (menção na Issue do item #15): exigiria build e reabriria o ADR 003 sem necessidade comprovada.
