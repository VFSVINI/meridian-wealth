# Observabilidade e FinOps

Como saber, sem adivinhar, o que o MeridianWealth está fazendo e quanto das cotas gratuitas está gastando. Decisões no
[ADR 016](adr/016-monolito-modular-e-observabilidade.md).

## Correlation-ID

Cada requisição ganha um identificador. O cliente pode enviar `X-Correlation-Id` (8 a 64 caracteres de `A-Za-z0-9._-`); qualquer outro valor
é descartado e substituído por um UUID, para que o cabeçalho não injete linhas falsas no log. O valor volta na resposta e aparece em toda
linha de log como `[id]`. Para `/api/**` (menos o stream) sai uma linha `http GET /api/... -> 200 (12 ms)` no logger `meridianwealth.http`;
desligue com `meridianwealth.observability.access-log=false`.

## Métricas (Prometheus)

`GET /actuator/prometheus` e `GET /actuator/metrics` exigem `Authorization: Bearer <MW_METRICS_TOKEN>`. Sem a variável, os dois caminhos
respondem 404 (não existem). O token precisa ter pelo menos 24 caracteres (`openssl rand -base64 32`).

| Métrica | Tags | Significado |
|---|---|---|
| `meridianwealth_provider_calls_total` | `provider`, `outcome` (`success`, `failure`, `short_circuited`, `rate_limited`) | chamadas por resultado, desde a partida |
| `meridianwealth_provider_circuit` | `provider` | 0 saudável, 1 em recuperação, 2 aberto, -1 desligado |
| `meridianwealth_provider_enabled` | `provider` | 1 se habilitado (tem chave, quando exige) |
| `meridianwealth_provider_latency_seconds_{count,sum}` | `provider` | tentativas que saíram e tempo total (cada repetição conta) |
| `meridianwealth_cache_requests_total` | `cache`, `result` (`hit`, `miss`) | consultas ao cache |
| `meridianwealth_cache_puts_total` | `cache` | gravações |
| `meridianwealth_portfolio_events_total` | `type` | eventos de domínio da carteira (item #13) |

### Consultas úteis (PromQL)

```promql
# taxa de falha por provedor nos últimos 5 minutos
sum by (provider) (rate(meridianwealth_provider_calls_total{outcome="failure"}[5m]))
  / sum by (provider) (rate(meridianwealth_provider_calls_total[5m]))

# chamadas por hora (compare com a cota do plano gratuito do provedor)
sum by (provider) (increase(meridianwealth_provider_calls_total{outcome=~"success|failure"}[1h]))

# quanto o cache poupa: razão de acertos
sum(rate(meridianwealth_cache_requests_total{result="hit"}[5m])) / sum(rate(meridianwealth_cache_requests_total[5m]))

# latência média por tentativa
rate(meridianwealth_provider_latency_seconds_sum[5m]) / rate(meridianwealth_provider_latency_seconds_count[5m])

# algum circuito aberto
max by (provider) (meridianwealth_provider_circuit) == 2
```

O acumulado é desde a partida do processo; por isso use `rate` e `increase`, que tratam o reinício. Os *scrapes* são de quem opera o
Prometheus: o projeto só expõe.

## Logs em JSON

Por padrão o log sai em texto, com o correlation-id entre colchetes. Com o perfil `json-logs` (`SPRING_PROFILES_ACTIVE=json-logs`) cada evento sai como
**uma linha de JSON** (`@timestamp`, `level`, `logger_name`, `message`, `stack_trace` e tudo que estiver no MDC, como `correlationId`) mais o campo fixo
`service`: pronto para qualquer agregador de logs, e dá para filtrar uma requisição inteira pelo `correlationId`. A configuração está em
`src/main/resources/logback-spring.xml`. No Render, basta acrescentar `SPRING_PROFILES_ACTIVE=json-logs` às variáveis de ambiente.

```json
{"@timestamp":"2026-11-28T04:16:51-03:00","message":"http GET /api/status -> 200 (3 ms)","logger_name":"meridianwealth.http","level":"INFO","correlationId":"teste-json-123","service":"meridianwealth"}
```

## Pilha local: Prometheus e Grafana (sem custo)

Em `ops/observability/` há um Compose com Prometheus (coleta a cada 30 s, guarda 15 dias), Grafana com o painel **MeridianWealth · visão geral** já provisionado e
as regras de alerta (`alerts.yml`). Nada sai da sua máquina e não há conta em nuvem.

1. Suba o app com um token (`MW_METRICS_TOKEN`, 24 caracteres ou mais): `$env:MW_METRICS_TOKEN = '<token>'; ./mvnw spring-boot:run`.
2. Grave o mesmo token em `ops/observability/secrets/metrics-token` (a pasta é ignorada pelo Git).
3. `docker compose -f ops/observability/docker-compose.yml up -d`.
4. Abra o Grafana em <http://localhost:3000> (usuário `admin`, senha `admin` ou `GRAFANA_ADMIN_PASSWORD`) e o Prometheus em <http://localhost:9090>.

O painel mostra requisições por segundo e p95 por rota, erros 5xx, taxa de falha, latência e circuito de cada provedor, chamadas por hora (para comparar com as
cotas dos planos gratuitos), acertos do cache, heap, CPU, conexões do banco e eventos da carteira. Os alertas cobrem app fora do ar, circuito aberto por 5 minutos,
provedor falhando mais da metade das chamadas, 5xx e heap acima de 90%. A CI valida o Compose e a configuração com o `promtool` a cada mudança, e um teste confere que
todo painel e todo alerta consultam só métricas que a aplicação realmente expõe.

Para observar o serviço no Render sem abrir a porta de métricas ao mundo, mantenha o `MW_METRICS_TOKEN` só no painel do Render e rode o Prometheus local apontando
`targets` para o endereço público do serviço (o endpoint continua exigindo o token).

## Health checks

| Caminho | O que verifica | Quando derruba |
|---|---|---|
| `/actuator/health/liveness` | o processo está vivo | só se o processo travar |
| `/actuator/health/readiness` | a aplicação aceita tráfego | só no desligamento; **não** depende de provedor externo |
| `/actuator/health/dependencies` | `marketData` (ativos com cotação ao vivo), `officialRates` (indicadores do Banco Central em dia), `providers` (circuitos) | nunca derruba: devolve `DEGRADED` (HTTP 200) com os detalhes |
| `/actuator/health` | resumo público, sem detalhes | |

A regra é deliberada: um provedor gratuito fora do ar é situação normal, não motivo para reiniciar a instância ou tirá-la do balanceador.
`DEGRADED` diz ao operador que há dado faltando; o produto já mostra isso ao usuário com os selos de frescor.
