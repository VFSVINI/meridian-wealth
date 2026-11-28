# ADR 043 — Observabilidade: logs em JSON e pilha local Prometheus + Grafana

- **Status:** aceito
- **Data:** 2026-11-28
- **Issue:** #109 (item #61 do roadmap, Milestone 10)

## Contexto

O ADR 016 já deu ao projeto o correlation-id, o Actuator com métricas Prometheus (atrás de `MW_METRICS_TOKEN`) e os health checks. Faltava poder **ver** isso: um painel, alertas e logs que um agregador consiga ler, tudo sem custo (o projeto roda no plano gratuito do Render).

## Decisão

1. **Logs em JSON por perfil, não por padrão.** `logback-spring.xml` mantém o texto do Spring Boot e troca para uma linha de JSON por evento (`LogstashEncoder`, com `correlationId` do MDC e `service`) quando o perfil `json-logs` está ligado. Mantém o desenvolvimento legível e liga o formato de máquina onde ele importa.
2. **Pilha local em `ops/observability/`** (Prometheus e Grafana em Compose), ligada só a `127.0.0.1`. A alternativa de um serviço de métricas na nuvem foi descartada: exige conta, e os planos gratuitos têm limites de séries e retenção que um projeto de portfólio não precisa aceitar.
3. **O token de métricas nunca entra em arquivo versionado:** o Prometheus o lê de `secrets/metrics-token` (pasta ignorada pelo Git), e um teste trava qualquer `credentials:` escrito no `prometheus.yml`.
4. **Painel e alertas provisionados como código** (Grafana provisioning e `alerts.yml`), versionados, em português, e **verificados**: um teste confere que todo painel e todo alerta consultam só métricas que a aplicação expõe (a lista conhecida está no teste e em `docs/observabilidade.md`), e a CI roda `docker compose config` e `promtool check config` sobre a pilha.
5. **Histograma de latência ligado** apenas para `http.server.requests`, o que dá o p95 por rota sem multiplicar séries de outras métricas.
6. **Alertas só do que é acionável:** app fora do ar, circuito aberto por 5 minutos, provedor falhando mais da metade das chamadas, 5xx e heap acima de 90%. Provedor gratuito fora do ar é situação normal e já tem selo de frescor para o usuário; por isso nenhum alerta reinicia nada.

## Consequências

- Uma dependência nova (`logstash-logback-encoder`), usada só com o perfil ligado.
- Ver o painel exige Docker na máquina de quem opera; a validação em CI não exige que você o rode.
- O histograma acrescenta algumas dezenas de séries por rota.
