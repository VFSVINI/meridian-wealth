# ADR 040 — Questionário de perfil com sete perguntas, uma por tela

- **Status:** aceito (revê a contagem de perguntas do ADR 009)
- **Data:** 2026-11-28
- **Issue:** #96 (item #52 do roadmap)

## Contexto

O protótipo Cosmos pede um questionário de **sete perguntas, uma por tela**; o nosso tinha nove, em formulário único. O ADR 009 fixou nove perguntas para cobrir os itens mínimos do art. 3 da Resolução CVM 30 (objetivos, situação financeira e conhecimento). Tirar duas perguntas sem cuidado deixaria itens da norma sem cobertura.

## Decisão

1. **Duas junções, nenhum item da estrutura perdido.** "Necessidade do dinheiro em 12 meses" e "reserva de emergência" (ambas §2, III) viraram uma pergunta, `LIQUIDITY_RESERVE`, com quatro opções ordenadas por risco (vou precisar; não devo precisar e não tenho reserva; reserva de menos de 6 meses; reserva de 6 meses ou mais). "Familiaridade com produtos" e "tempo investindo" (§3, I e II) viraram `KNOWLEDGE`, com quatro opções.
2. **Pontuação:** cada pergunta continua indo de 0 a 4 pontos, com o mesmo peso; o total máximo passa de 36 para **28** e as faixas da nota (30, 55 e 80) não mudam. Os tetos foram mantidos com o mesmo efeito: "vou precisar em até 12 meses" limita a conservador e "sem reserva" a moderado.
3. **Perda aceita:** a opção "talvez uma parte" da necessidade de caixa deixou de existir, e as combinações entre produto e tempo de experiência ficaram agrupadas em quatro degraus. A pergunta fica menos fina, em troca de sete telas.
4. **Perfis já salvos continuam válidos:** o que a conta guarda é o perfil, não as respostas.
5. **Tela:** uma pergunta por vez, com barra de progresso, "Voltar" e "Próxima"; as respostas ficam só na memória da página até o envio, como antes.

## Consequências

- Quem refaz o questionário pode cair numa faixa um pouco diferente da anterior, porque a nota é calculada sobre outras perguntas. A API e os testes passam a refletir as sete perguntas (`GET /api/suitability/questionnaire`).
- O texto do produto continua sem alegar conformidade com a norma: ela é só a estrutura das dimensões.
