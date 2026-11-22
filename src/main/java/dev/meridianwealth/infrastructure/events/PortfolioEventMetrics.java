package dev.meridianwealth.infrastructure.events;

import dev.meridianwealth.domain.portfolio.PortfolioEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Consumer;

/**
 * Assinante dos eventos da carteira que os conta no Micrometer ({@code meridianwealth.portfolio.events}, por tipo). Só números: nenhum
 * identificador de usuário nem valor vira rótulo de métrica.
 */
public class PortfolioEventMetrics implements Consumer<PortfolioEvent> {
    private final MeterRegistry registry;

    public PortfolioEventMetrics(MeterRegistry registry) { this.registry = registry; }

    @Override
    public void accept(PortfolioEvent event) {
        registry.counter("meridianwealth.portfolio.events", "type", event.type()).increment();
    }
}
