# ADR 010 — Impacto pré-ordem e glossário ligado às métricas

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #22

## Contexto

Antes de uma ordem, o investidor deveria ver o efeito dela na carteira e entender cada número do painel. Os riscos: uma
"simulação" ser lida como cotação ou ordem, o risco da carteira parecer mais preciso do que é, e o glossário descrever o conceito
de livro e não o que o código faz.

## Decisão

1. **Simulação rotulada e sem preço:** a análise trabalha com valores em reais informados pelo usuário; nenhuma cotação entra e
   não existe rota de ordem. A resposta e a tela dizem "SIMULAÇÃO: não é cotação nem ordem".
2. **Métricas simples e auditáveis:** maior posição, HHI e posições efetivas (concentração); média ponderada das volatilidades
   com cobertura declarada (risco); aderência do item #8. O risco da carteira é **limite superior** (sem correlação) e a cobertura
   mostra quanto do valor tem dado real. Sem dado, a posição fica fora do risco; nunca se estima.
3. **Achados descritivos:** só comparam antes e depois; o único limite fixo (25% na maior posição) é do produto e é rotulado assim.
4. **Defesa de magnitude compartilhada:** `MoneyBounds` valida todo valor monetário de entrada antes de qualquer conta (herança
   da falha de notação científica achada no item #8).
5. **Glossário no domínio, fiel ao código:** cada entrada vem de ler a implementação (fórmulas, janelas, limites), e as diferenças
   para o conceito clássico são ditas no texto.
6. **Ligação testada:** a tabela rótulo → id do `index.html` é lida por teste, que exige id existente e cobertura de todo rótulo do
   painel. Métrica nova sem glossário quebra a build.
7. **Nomes `Impact`:** a regra do ArchUnit contra `Simulat` em produção foi mantida; o recurso é cálculo sobre valores do usuário.

## Consequências

- **Positivas:** o usuário vê concentração, risco e aderência antes e depois de uma decisão; o critério "cada métrica liga ao
  glossário" é verificado por teste, não por inspeção.
- **Negativas:** o risco ignora correlação; o conteúdo do glossário não foi revisado por profissional; a lista de rótulos do teste
  de cobertura é espelhada à mão (um rótulo dinâmico novo precisa entrar nela).

## Alternativas rejeitadas

- **Volatilidade de carteira com matriz de covariância agora:** exigiria históricos alinhados de todas as posições e trataria mal
  as sem dado; fica como evolução (dívida 27).
- **Afrouxar a regra do ArchUnit:** a regra protege a promessa de dados 100% reais.
- **Glossário como página estática solta:** sem ligação testada com o painel.
