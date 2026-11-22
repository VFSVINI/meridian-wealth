# ADR 020 — Monte Carlo reprodutível e a exceção estreita à regra de aleatoriedade

- **Status:** aceito
- **Data:** 2026-11-22
- **Issue:** #49 (item #18 do roadmap)

## Contexto

O ADR 004 proíbe aleatoriedade no produto, com regra executável no ArchUnit: `Random`, `ThreadLocalRandom` e `SplittableRandom` só no jitter do retry, porque sorteio ao lado de preço é como dado sintético entra num sistema que promete dado real. O item #18 pede Monte Carlo, que **é** sorteio: milhares de trajetórias de patrimônio. A tensão é real e não dá para resolver escondendo: ou o Monte Carlo não existe, ou a regra ganha uma exceção.

A diferença que justifica a exceção: o Monte Carlo não gera **preço** nenhum, gera **cenários** de patrimônio, rotulados "projeção" em toda a cadeia (domínio, API, tela), e os retornos que ele reamostra são reais.

## Decisão

1. **Exceção estreita:** `java.util.SplittableRandom`, com **semente explícita**, só em `dev.meridianwealth.domain.montecarlo`. `Random` e `ThreadLocalRandom` continuam proibidos em todo o produto; `SplittableRandom` continua proibido fora do retry e do Monte Carlo. Três regras ArchUnit: `randomnessOnlyForRetryJitter`, `seededRandomnessOnlyForRetryJitterAndMonteCarlo` e `monteCarloIsPureDomain` (só JDK e ele mesmo).
2. **Reprodutível por construção:** a mesma entrada e a mesma semente dão exatamente a mesma projeção (testado). Sem semente na requisição, ela é derivada da própria entrada (e do usuário, no modo carteira), então reabrir a tela mostra o mesmo cone; uma semente informada permite explorar outro sorteio.
3. **Dois modos, ambos rotulados projeção:**
   - **Premissas manuais:** retorno esperado e volatilidade anuais informados pela pessoa, log-normal mensal (o retorno esperado composto bate com a premissa). Não toca em dado pessoal.
   - **Minha carteira:** reamostragem por blocos de 21 pregões de retornos diários **reais**, alinhados por data entre os ativos, o que preserva a correlação (resolve em parte a dívida 27 no risco projetado). Pesos fixos pelo valor atual em reais, rebalanceados todo mês.
4. **Nada é estimado para tapar a falta:** posição sem histórico real (renda fixa, fundos, ativo sem cotação, histórico curto ou sem datas em comum) fica **parada** no valor atual, sem render, e a saída lista cada posição com o tratamento e o motivo, mais a cobertura em percentual do patrimônio. Sem nenhum ativo com histórico suficiente, a rota recusa (422) e manda usar as premissas manuais.
5. **Honestidade sobre os limites**, escritos na saída: valores nominais, em reais, sem impostos, custos nem inflação; ativos em dólar entram pelo retorno na moeda do ativo (o câmbio não entra); o histórico passado não garante o futuro; os percentis resumem trajetórias simuladas, não o que vai acontecer.
6. **Estatística em `double`, dinheiro em `BigDecimal`:** o motor usa `double` (como o motor de mercado, ADR 002); as entradas em reais passam por `MoneyBounds` antes de qualquer conta (notação científica não vira negação de serviço) e a resposta arredonda para 2 casas.
7. **Cálculo pesado é recurso escasso:** uma simulação custa cerca de 0,3 s de CPU e a rota paramétrica é pública. As rotas de risco têm um limite próprio (10 por minuto, por IP ou usuário) além do geral, e o serviço limita a 4 simulações simultâneas (503 com `Retry-After` quando cheio).
8. **Fronteiras de módulo:** `domain.montecarlo` sem dependências; `application.risk` depende só de `auth` e `portfolio`.

## Consequências

- A regra de "sem aleatoriedade" continua verificável e agora diz exatamente onde é permitida e por quê.
- A projeção é boa para entender ordem de grandeza e sensibilidade, não para prever: 5.000 trajetórias dão percentis estáveis, mas o modelo (blocos de 21 pregões, pesos fixos) ignora regimes, impostos e mudanças de alocação.
- Ativos de renda fixa pesando muito na carteira reduzem a cobertura e o risco projetado: a tela mostra isso em vez de esconder.

## Alternativas descartadas

- **Não ter Monte Carlo:** o item do roadmap e o protótipo o pedem, e a análise de meta é útil para o planejamento.
- **Gerador sem semente (`ThreadLocalRandom`):** resultados que mudam a cada clique, impossíveis de testar e de reproduzir.
- **Modelo paramétrico também para a carteira:** exigiria estimar covariância e distribuição; a reamostragem de retornos reais evita assumir uma forma para a distribuição e preserva a correlação.
- **Preencher a renda fixa com a Selic ou o CDI:** seria inventar o rendimento do produto, que a plataforma não conhece.
