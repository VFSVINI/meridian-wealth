package dev.meridianwealth.infrastructure.observability;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Métricas por provedor, lidas do mesmo {@link ProviderStatus} que alimenta a tela de status (uma só fonte da verdade, nenhum executor
 * muda). É o que torna o consumo das cotas gratuitas observável (FinOps): quantas chamadas cada provedor recebeu, quantas falharam, quantas
 * o circuito ou o limite local barraram, em que estado está o circuito e quanto as chamadas demoram.
 *
 * <ul>
 *   <li>{@code meridianwealth.provider.calls{provider,outcome}}: contador por resultado ({@code success}, {@code failure},
 *       {@code short_circuited}, {@code rate_limited}).
 *   <li>{@code meridianwealth.provider.circuit{provider}}: 0 fechado (ou conectado), 1 meio aberto (ou reconectando), 2 aberto, -1 desligado
 *       ou desconhecido.
 *   <li>{@code meridianwealth.provider.enabled{provider}}: 1 se o provedor está habilitado (tem chave, quando exige).
 *   <li>{@code meridianwealth.provider.latency{provider}}: tempo e contagem das tentativas que saíram.
 * </ul>
 */
@Component
public class ProviderMetrics implements MeterBinder {
    private final List<ProviderStatusSource> sources;

    public ProviderMetrics(List<ProviderStatusSource> sources) { this.sources = List.copyOf(sources); }

    /** Estado do circuito como número: 0 saudável, 1 em recuperação, 2 aberto, -1 desligado ou desconhecido. */
    static double circuitValue(String circuit) {
        if (circuit == null) return -1;
        return switch (circuit) {
            case "CLOSED", "CONECTADO" -> 0;
            case "HALF_OPEN", "RECONECTANDO" -> 1;
            case "OPEN" -> 2;
            default -> -1;
        };
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (ProviderStatusSource source : sources) {
            String name = source.status().name();
            counter(registry, source, name, "success", ProviderStatus::successes);
            counter(registry, source, name, "failure", ProviderStatus::failures);
            counter(registry, source, name, "short_circuited", ProviderStatus::shortCircuited);
            counter(registry, source, name, "rate_limited", ProviderStatus::rateLimited);
            Gauge.builder("meridianwealth.provider.circuit", source, s -> circuitValue(s.status().circuit())).tag("provider", name)
                    .description("Estado do circuito: 0 saudável, 1 em recuperação, 2 aberto, -1 desligado").register(registry);
            Gauge.builder("meridianwealth.provider.enabled", source, s -> s.status().enabled() ? 1 : 0).tag("provider", name).register(registry);
            FunctionTimer.builder("meridianwealth.provider.latency", source, s -> s.status().attempts(), s -> s.status().callNanos(), TimeUnit.NANOSECONDS)
                    .tag("provider", name).description("Tempo das chamadas ao provedor (cada tentativa)").register(registry);
        }
    }

    private static void counter(MeterRegistry registry, ProviderStatusSource source, String name, String outcome, java.util.function.ToLongFunction<ProviderStatus> value) {
        FunctionCounter.builder("meridianwealth.provider.calls", source, s -> value.applyAsLong(s.status())).tag("provider", name).tag("outcome", outcome)
                .description("Chamadas ao provedor por resultado").register(registry);
    }
}
