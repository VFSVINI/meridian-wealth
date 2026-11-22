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

## Health checks

| Caminho | O que verifica | Quando derruba |
|---|---|---|
| `/actuator/health/liveness` | o processo está vivo | só se o processo travar |
| `/actuator/health/readiness` | a aplicação aceita tráfego | só no desligamento; **não** depende de provedor externo |
| `/actuator/health/dependencies` | `marketData` (ativos com cotação ao vivo), `officialRates` (indicadores do Banco Central em dia), `providers` (circuitos) | nunca derruba: devolve `DEGRADED` (HTTP 200) com os detalhes |
| `/actuator/health` | resumo público, sem detalhes | |

A regra é deliberada: um provedor gratuito fora do ar é situação normal, não motivo para reiniciar a instância ou tirá-la do balanceador.
`DEGRADED` diz ao operador que há dado faltando; o produto já mostra isso ao usuário com os selos de frescor.
