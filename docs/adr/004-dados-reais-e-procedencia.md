# ADR 004 — Dados 100% reais e procedência explícita

- **Status:** aceito
- **Data:** 2026-11-20
- **Issue:** #2

## Contexto

A base herdada tinha dois mecanismos que produziam preços sintéticos no produto:

1. **Simulador de fallback** (`SimulatedFlow`): quando nenhum provedor respondia, gerava um histórico por movimento browniano e o exibia como se fosse o ativo (com o rótulo "SIMULADO").
2. **Fluxo simulado** sobre a cotação real: entre duas cotações atrasadas, aplicava um micro-ruído gaussiano para "manter o painel vivo".

Mesmo rotulados, os dois colocam na tela números que nunca foram negociados. Numa plataforma de patrimônio isso é inaceitável: um usuário pode decidir com base num preço inventado, e um rótulo pequeno não compensa um gráfico convincente. O dono do produto definiu a regra: **dados completamente reais**.

## Decisão

1. **Nenhum preço sintético no produto.** Simuladores, geradores aleatórios de preço e interpolações não existem em `src/main`. Geradores determinísticos ficam apenas em `src/test`.
2. **Failover honesto:** provedores reais → último dado real gravado no banco (rotulado com data e hora) → **sem dado**. A ausência é um estado de primeira classe (`DataStatus.UNAVAILABLE`), não um erro escondido.
3. **Procedência em toda cotação:** fonte, horário da cotação na origem, selo de atraso (`Freshness`: tempo real, ~15 min, fechamento, último dado real gravado) e próxima atualização prevista.
4. **Selo pela fonte e pelo pregão:** com o pregão fechado, a cotação é o fechamento; o Frankfurter (BCE) é referência diária; Yahoo e Stooq são atrasados; o Finnhub é tempo real segundo a documentação pública, a confirmar na prática no PR #3.
5. **API explícita:** um ativo sem dado real responde **503** com `"Sem dado em tempo real..."`; listas vêm vazias; o macro indisponível vira `null` por indicador e o motor não aplica ajuste macro.
6. **Interface sem disfarce:** estados vazios desenhados, aviso de cotação desatualizada, pulso visual apenas quando chega uma cotação nova.
7. **Projeções são outra coisa:** backtests (e, no futuro, Monte Carlo e stress tests) simulam cenários e levam sempre o rótulo **projeção**, nunca aparecem como cotação.
8. **Regra executável:** ArchUnit proíbe classes de produção com `Simulat`, `Synthetic` ou `Fake` no nome e o uso de `Random`/`ThreadLocalRandom` fora do jitter do retry.

## Consequências

- **Positivas:** o que aparece na tela aconteceu de fato no mercado, com origem e idade conhecidas; a confiança do usuário não depende de ler um rótulo.
- **Negativas:** com provedores gratuitos fora do ar, o painel fica parcialmente vazio; fora do pregão os preços ficam parados (é o comportamento correto, mas parece menos "vivo"); os testes precisam de fixtures reais em vez de um simulador conveniente.
- **Mitigações:** nova tentativa automática dos ativos sem dado, persistência do último dado real (com Postgres ligado) e, no roadmap, fontes adicionais (brapi, WebSocket Finnhub e Binance) e um adapter plugável para feed pago.

## Alternativas consideradas

- **Manter o simulador com rótulo bem visível:** rejeitado; o gráfico sintético continua persuasivo e o rótulo pode ser ignorado ou cortado em capturas.
- **Interpolar entre cotações reais:** rejeitado; ainda é preço inventado.
- **Esconder ativos sem dado:** rejeitado; esconder a ausência também é desonesto e confunde quem procura o ativo.
