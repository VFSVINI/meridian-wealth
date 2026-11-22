package dev.meridianwealth.infrastructure.observability.health;

import dev.meridianwealth.application.BrazilRatesService;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Indicadores oficiais do Banco Central: {@code UP} se todos em dia; {@code DEGRADED} se algum está desatualizado ou sem dado. */
@Component("officialRates")
public class OfficialRatesHealthIndicator implements HealthIndicator {
    private final BrazilRatesService rates;

    public OfficialRatesHealthIndicator(BrazilRatesService rates) { this.rates = rates; }

    @Override
    public Health health() {
        List<BrazilRatesService.Item> items = rates.items();
        List<String> late = items.stream().filter(i -> i.status() != BrazilRatesService.Status.OK).map(i -> i.series().label()).toList();
        Health.Builder b = late.isEmpty() ? Health.up() : Health.status(Degraded.STATUS);
        return b.withDetail("series", items.size()).withDetail("notOk", late).build();
    }
}
