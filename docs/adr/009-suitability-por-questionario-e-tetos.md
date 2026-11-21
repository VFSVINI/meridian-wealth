# ADR 009 — Suitability por questionário, tetos e carteira modelo

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #21

## Contexto

Montar uma carteira só faz sentido à luz do perfil do investidor. O risco é parecer uma classificação regulada ou uma
recomendação, quando é uma ferramenta educacional, e classificar errado alguém sem reserva ou sem horizonte.

## Decisão

1. **Questionário de 9 perguntas** que cobre as três dimensões do art. 3 da Resolução CVM 30 (objetivos, situação financeira e
   conhecimento), com os itens mínimos dos §1 a §3 citados em cada pergunta. A norma é só estrutura: não há alegação de
   conformidade, porque o produto não recomenda nem executa ordens.
2. **Pontuação e faixas transparentes**, definidas pelo produto, todas as perguntas com o mesmo peso. A API devolve as regras.
3. **Tetos de perfil** por respostas incompatíveis com risco alto (horizonte curto, necessidade de caixa, sem reserva, venda em
   pânico): o perfil final é o menor entre o da nota e os tetos, e cada teto aplicado é explicado. Evita um perfil arrojado por
   nota alta para quem não pode perder.
4. **Metas por classe sobre a taxonomia** (`AssetCategory`), somando 100, **rotuladas como ilustrativas e sem fonte externa**.
5. **Aderência descritiva:** desvio em pontos percentuais por classe, nota por distância de variação total, tolerância de ±5 p.p.
   Não indica compra nem venda.
6. **Sem estado e sem registro:** respostas e carteira são processadas na requisição e descartadas (dado pessoal).
7. **Entrada inválida é erro** (400), nunca preenchida.

## Consequências

- **Positivas:** regra inspecionável e testada (limites das faixas, tetos, soma das metas, borda da tolerância); a mesma classe
  de ativo da taxonomia serve ao futuro painel de carteira (itens #12 e #15); o Login com Google (item #11) pode levar o usuário
  novo a este fluxo.
- **Negativas:** pontos, faixas, tetos e metas são escolhas do produto, não validadas por profissional habilitado; o perfil não
  é guardado entre sessões até haver conta (item #11); a carteira é informada à mão até o livro de movimentações (item #13).

## Limitação

O questionário **não substitui a análise de um profissional habilitado**. Nenhuma saída é recomendação de investimento.

## Alternativas rejeitadas

- **Só a nota, sem tetos:** daria perfil agressivo a quem precisa do dinheiro em 12 meses.
- **Metas copiadas de uma instituição:** sem licença nem fonte verificável.
- **Guardar as respostas para "melhorar" a recomendação:** dado pessoal sem finalidade nem consentimento.
