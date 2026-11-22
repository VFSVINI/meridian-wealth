package dev.meridianwealth.infrastructure.observability.health;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class ProvidersHealthIndicatorTest {
    private static ProviderStatusSource src(String name, boolean enabled, String circuit) {
        return () -> new ProviderStatus(name, enabled, circuit, 0, 0, 0, 0);
    }

    @Test void upWhenNoEnabledProviderHasAnOpenCircuit() {
        var h = new ProvidersHealthIndicator(List.of(src("a", true, "CLOSED"), src("b", false, "OPEN"))).health();
        assertThat(h.getStatus()).as("provedor desligado com circuito aberto não conta").isEqualTo(Status.UP);
    }

    @Test void degradedNamesTheOpenCircuits() {
        var h = new ProvidersHealthIndicator(List.of(src("a", true, "CLOSED"), src("b", true, "OPEN"))).health();
        assertThat(h.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(h.getDetails().get("openCircuits")).isEqualTo(List.of("b"));
    }
}
