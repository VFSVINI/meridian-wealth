# Princípios de design da interface

Base para o item #15 (Private Banking Dark Luxury). A referência de estudo foi um artigo público sobre sites minimalistas
(Hostinger, "Sites minimalistas: 15 melhores sites com design simples e elegante", lido em 2026-11-21). Nada do conteúdo ou da
identidade visual dos sites citados foi copiado: só os princípios abaixo, adaptados a um painel de dados.

| Princípio (da referência) | Como entra no MeridianWealth |
|---|---|
| Paleta enxuta e contraste legível | Grafite escuro com **um** dourado de destaque (`--gold`); verde e vermelho só para direção de preço e veredito |
| Espaço negativo generoso | Escala de espaço (4, 8, 12, 16, 24 px), cartões com respiro, linha de 1,55 |
| Hierarquia visual clara, tipografia legível | Serifa (Georgia) só em marca e títulos, sans para texto, mono tabular para números |
| Remover o desnecessário, sem pop-ups nem animação chamativa | Sem modais promocionais; animações só nos preços e desligadas com `prefers-reduced-motion` |
| Informação sob demanda (revelação progressiva) | Cada relatório do radar abre com `<details>`: o resumo vem primeiro, os números e a regra ao clicar |
| Seções claras numa página só | Três colunas no desktop; uma coluna abaixo de 1150 px |
| Responsividade e desempenho | Sem build, sem webfont, sem biblioteca; layout móvel abaixo de 700 px sem rolagem horizontal |
| Acessibilidade | Link "Ir para o conteúdo", foco visível dourado, `aria-label` nos controles |

Fica para o item #15: tema completo, painel "Minha Carteira", botão "Continuar com Google" e conferência de contraste (WCAG).

## Protótipo aprovado

A partir de 2026-11-21 a interface segue o protótipo aprovado ([design-prototipo.md](design-prototipo.md), [ADR 012](adr/012-prototipo-visual-como-referencia.md)). As imagens principais estão em `docs/design/`. Os princípios acima continuam valendo; onde divergirem, vale o protótipo.
