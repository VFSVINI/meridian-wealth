package dev.meridianwealth.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProviderMetricsTest {
    private static ProviderStatus status(String circuit, boolean enabled, long ok, long fail, long shorted, long limited, long attempts, long nanos) {
        return new ProviderStatus("yahoo", enabled, circuit, ok, fail, shorted, limited, null, attempts, nanos);
    }

    @Test void exposesCountersGaugesAndLatencyReadingTheLiveStatus() {
        AtomicReference<ProviderStatus> current = new AtomicReference<>(status("CLOSED", true, 10, 2, 1, 3, 12, 6_000_000_000L));
        ProviderStatusSource source = current::get;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ProviderMetrics(List.of(source)).bindTo(registry);

        assertThat(registry.get("meridianwealth.provider.calls").tag("provider", "yahoo").tag("outcome", "success").functionCounter().count()).isEqualTo(10);
        assertThat(registry.get("meridianwealth.provider.calls").tag("outcome", "failure").functionCounter().count()).isEqualTo(2);
        assertThat(registry.get("meridianwealth.provider.calls").tag("outcome", "short_circuited").functionCounter().count()).isEqualTo(1);
        assertThat(registry.get("meridianwealth.provider.calls").tag("outcome", "rate_limited").functionCounter().count()).isEqualTo(3);
        assertThat(registry.get("meridianwealth.provider.circuit").gauge().value()).isZero();
        assertThat(registry.get("meridianwealth.provider.enabled").gauge().value()).isEqualTo(1);
        assertThat(registry.get("meridianwealth.provider.latency").functionTimer().count()).isEqualTo(12);
        assertThat(registry.get("meridianwealth.provider.latency").functionTimer().totalTime(TimeUnit.SECONDS)).isEqualTo(6);

        current.set(status("OPEN", false, 11, 3, 5, 3, 13, 7_000_000_000L));
        assertThat(registry.get("meridianwealth.provider.circuit").gauge().value()).as("a métrica acompanha o estado atual").isEqualTo(2);
        assertThat(registry.get("meridianwealth.provider.enabled").gauge().value()).isZero();
        assertThat(registry.get("meridianwealth.provider.calls").tag("outcome", "success").functionCounter().count()).isEqualTo(11);
    }

    @Test void mapsEveryCircuitStateToANumber() {
        assertThat(ProviderMetrics.circuitValue("CLOSED")).isZero();
        assertThat(ProviderMetrics.circuitValue("CONECTADO")).isZero();
        assertThat(ProviderMetrics.circuitValue("HALF_OPEN")).isEqualTo(1);
        assertThat(ProviderMetrics.circuitValue("RECONECTANDO")).isEqualTo(1);
        assertThat(ProviderMetrics.circuitValue("OPEN")).isEqualTo(2);
        assertThat(ProviderMetrics.circuitValue("DESLIGADO")).isEqualTo(-1);
        assertThat(ProviderMetrics.circuitValue(null)).isEqualTo(-1);
    }
}
