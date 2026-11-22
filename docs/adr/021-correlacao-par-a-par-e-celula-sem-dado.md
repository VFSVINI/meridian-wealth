# ADR 021 — Correlação par a par, célula sem dado e risco da carteira com diversificação

- **Status:** aceito
- **Data:** 2026-11-22
- **Issue:** #51 (item #19 do roadmap)

## Contexto

O simulador pré-ordem calcula o risco da carteira como a média ponderada das volatilidades individuais, que é um limite superior e ignora que os ativos não se movem juntos (dívida 27). O item #19 pede a matriz de correlação e o mapa de concentração. Os pontos delicados são os de sempre neste projeto: o que fazer com o ativo sem histórico (renda fixa, fundos, ativo sem cotação) e com o par que tem poucos dados, sem inventar número.

## Decisão

1. **Correlação de Pearson sobre retornos diários reais**, nos últimos 252 pregões, **par a par**: cada par usa as datas que **os dois** ativos têm. Assim um ativo curto ou de outro mercado só apaga as células dele; o alinhamento global (usado no Monte Carlo) derrubaria todos os pares.
2. **Célula sem dado é `null`, nunca zero:** par com menos de 60 pregões em comum, ou série que não varia (a correlação não é definida), fica cinza e escrita "n/d" na tela, com o motivo no título. Zero seria uma afirmação ("independentes") que os dados não sustentam.
3. **Duas matrizes:** entre os ativos e entre as **classes**. A série de uma classe é o retorno diário médio, ponderado pelo valor, dos ativos dela que têm histórico em comum; classe sem histórico em comum suficiente fica fora e a tela diz. Com menos de duas classes não há matriz de classes.
4. **Risco da carteira com a correlação:** a volatilidade anualizada sobre os retornos alinhados dos ativos com histórico (pesos fixos pelo valor atual), ao lado da média ponderada das volatilidades individuais da mesma parte, e o **benefício da diversificação** (a diferença, nunca negativa, igual a zero quando a correlação é 1). Vale para a parte com histórico, e a cobertura em percentual do patrimônio está na saída. O simulador pré-ordem segue com o limite superior, rotulado como tal.
5. **Mapa de concentração:** peso de cada classe em cada faixa de risco que o motor de timing deu ao ativo (baixo, moderado, alto, extremo) e uma coluna "sem classificação" para o que o motor não avalia. Entram **todas** as posições, pelo valor em reais; a soma das células é 100% do patrimônio. HHI por posição e por classe, posições efetivas e as cinco maiores posições.
6. **Fora da correlação, dentro da concentração:** renda fixa, fundos sem cota e ativos sem dado real aparecem numa lista com o motivo, e pesam no mapa de concentração.
7. **Limites declarados:** ativos em dólar entram pelo retorno na moeda do ativo (o câmbio não entra); correlação mede movimento conjunto no passado e muda em crises; com poucos pregões a estimativa é ruidosa (por isso o piso de 60 e o número de pregões usados fica à vista no título da célula).
8. **Rota com login, sob o limite de cálculo pesado:** `GET /api/me/risk/correlation` fica sob `/api/me/risk/`, o mesmo balde baixo de requisições do Monte Carlo.

## Consequências

- A dívida 27 passa a ter resposta para a carteira salva; o simulador pré-ordem (carteira digitada) ainda usa o limite superior até que ganhe a mesma conta.
- O domínio `correlation` depende de `montecarlo` (reaproveita o alinhamento de retornos), declarado nas regras de módulo.
- A correlação de pares com histórico curto é instável: mostrar o número de pregões e o piso de 60 é a defesa, não uma garantia.

## Alternativas descartadas

- **Alinhar todos os ativos pelas datas comuns a todos:** um ativo de outro mercado, ou recém-listado, apagaria a matriz inteira.
- **Preencher com zero (ou com a média) a célula sem dado:** afirma independência que ninguém mediu.
- **Correlação de classes por índices de mercado externos:** exigiria dado que a plataforma não tem; as séries de classe saem da própria carteira.
