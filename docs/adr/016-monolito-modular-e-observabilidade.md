# ADR 016 — Monólito modular com fronteiras verificadas e observabilidade por provedor

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #40 (item #14 do roadmap)

## Contexto

O projeto cresceu: mercado, composição, relatórios, suitability, impacto, teses, autenticação e carteira. Surgiu a pergunta natural de se
microsserviços seriam mais seguros que um monólito. O risco real que a pergunta aponta é o **acoplamento acidental** (um módulo passar a
depender de outro sem que alguém decida isso). Microsserviços não resolvem isso: trocam chamada de método por chamada de rede, e o
acoplamento continua, agora com latência, falha parcial e consistência eventual. Para uma ferramenta com um desenvolvedor, uma
instância e cotas gratuitas de provedores, o custo operacional (um deploy, um pipeline, uma observabilidade e uma versão de contrato por
serviço) supera o ganho.

Em paralelo, o produto depende de provedores gratuitos com cota. Sem medir quanto cada um é chamado, quanto falha e quanto o cache poupa,
a cota só aparece quando acaba.

## Decisão

1. **Monólito modular, não microsserviços.** A separação de módulos é feita por pacote e **verificada por ArchUnit**
   (`ModuleBoundariesTest`): cada módulo de negócio (`domain.*` e `application.auth`/`application.portfolio`) declara de quais outros pode
   depender; qualquer dependência nova quebra o `verify` e precisa ser decidida e escrita no teste. Ciclos entre módulos também quebram.
   O núcleo compartilhado (`Candle`, indicadores, sinal) não depende de módulo nenhum, e os serviços de mercado, composição, relatórios e
   teses não conhecem a carteira do usuário.
2. **Quando extrair um serviço** (gatilhos, qualquer um basta, e só então):
   - perfil de carga diferente que exija escalar à parte (ex.: ingestão de mercado contínua versus API por demanda);
   - cadência de entrega independente de uma equipe própria;
   - isolamento de falha ou de segurança que o processo único não dá;
   - infraestrutura que sustente a operação (orquestrador, observabilidade distribuída, contratos versionados).
   O candidato natural é a **ingestão de dados de mercado** (já atrás de `MarketDataPort`). **Carteira e livro ficam juntos**: a posição e
   a entrada do livro mudam na mesma transação (ADR 015), e separar isso trocaria uma garantia do banco por consistência eventual.
3. **Leituras do mercado sem monitor compartilhado (dívida 3).** `MarketService` tem um único escritor (o ciclo de atualização, sob um
   lock só para escrever) que publica um **mapa imutável de visões** (barras copiadas, avaliação já calculada). Todas as leituras leem a
   última visão publicada, sem bloqueio. Um teste prova que leituras respondem em menos de 100 ms enquanto uma atualização lenta dura mais
   de 250 ms (o mesmo teste falha no código anterior).
4. **Observabilidade como adaptador, sem tocar nos executores.** As métricas por provedor (`meridianwealth.provider.*`) são lidas do mesmo
   `ProviderStatus` que alimenta a tela de status, então há uma só fonte da verdade. O cache é decorado por `MeteredCacheStore`
   (`meridianwealth.cache.*`) por um `BeanPostProcessor` na borda. A latência vem do tempo de cada tentativa no `ResilientExecutor`.
5. **Correlation-ID.** Todo pedido recebe um identificador (aceita `X-Correlation-Id` só se casar `[A-Za-z0-9._-]{8,64}`, contra injeção em
   log; senão gera um UUID), devolvido no cabeçalho e presente em toda linha de log da requisição (MDC).
6. **Saúde em grupos.** `liveness` e `readiness` **não** consultam provedores externos: um provedor fora do ar não pode reiniciar nem tirar
   a instância do balanceador. O grupo `dependencies` (`marketData`, `officialRates`, `providers`) reporta `DEGRADED` (HTTP 200) quando
   há dado faltando ou circuito aberto.
7. **Métricas fechadas por token.** `/actuator/metrics` e `/actuator/prometheus` expõem memória, consumo e volume de uso. Sem
   `MW_METRICS_TOKEN` (mínimo de 24 caracteres) respondem 404; com ele, só a `Authorization: Bearer` correta (comparação em tempo
   constante).

## Consequências

- Um desvio de arquitetura entre módulos é pego na hora, não na revisão de um PR seis meses depois.
- A decisão de extrair um serviço passa a ser guiada por sinais medidos (as métricas deste item), não por receio.
- O contador por instância das travas de login e os eventos de domínio em processo (dívidas 34 e 38) seguem como estão: viram problema
  quando houver mais de uma instância, que é um dos gatilhos do item 2.
- As métricas dos provedores são acumuladas desde a partida; o histórico fica por conta de quem faz o *scrape* (Prometheus).

## Alternativas descartadas

- **Microsserviços agora:** custo operacional sem o problema que o justifica; não elimina acoplamento.
- **Módulos Maven separados:** isolam o compilador, mas custam um POM por módulo para um ganho que o ArchUnit já dá aqui.
- **Spring Modulith:** boa opção; descartada por ora para não acrescentar uma dependência quando o ArchUnit, já presente, cobre as regras
  necessárias.
