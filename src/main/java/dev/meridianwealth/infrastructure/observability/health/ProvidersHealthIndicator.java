package dev.meridianwealth.infrastructure.observability.health;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Circuitos dos provedores externos: {@code UP} se nenhum está aberto; {@code DEGRADED} se algum está (a aplicação segue, com failover). */
@Component("providers")
public class ProvidersHealthIndicator implements HealthIndicator {
    private final List<ProviderStatusSource> sources;

    public ProvidersHealthIndicator(List<ProviderStatusSource> sources) { this.sources = List.copyOf(sources); }

    @Override
    public Health health() {
        List<ProviderStatus> all = sources.stream().map(ProviderStatusSource::status).toList();
        List<String> open = all.stream().filter(s -> s.enabled() && "OPEN".equals(s.circuit())).map(ProviderStatus::name).toList();
        Health.Builder b = open.isEmpty() ? Health.up() : Health.status(Degraded.STATUS);
        return b.withDetail("providers", all.size()).withDetail("openCircuits", open).build();
    }
}
