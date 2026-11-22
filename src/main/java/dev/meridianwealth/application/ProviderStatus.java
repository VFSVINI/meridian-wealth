package dev.meridianwealth.application;

/**
 * Saúde de um provedor externo: circuito, sucessos, falhas, chamadas barradas e rate limit local.
 * {@code detail} é um texto livre opcional para provedores com estado próprio (ex.: qual bolsa do
 * stream de cripto está ativa e quais estão bloqueadas).
 *
 * <p>{@code attempts} e {@code callNanos} medem as chamadas que de fato saíram (cada tentativa, inclusive as repetidas) e o tempo total
 * gasto nelas; alimentam a métrica de latência por provedor. Provedores que não medem deixam zero.
 */
public record ProviderStatus(String name, boolean enabled, String circuit, long successes, long failures,
                             long shortCircuited, long rateLimited, String detail, long attempts, long callNanos) {
    public ProviderStatus(String name, boolean enabled, String circuit, long successes, long failures,
                          long shortCircuited, long rateLimited, String detail) {
        this(name, enabled, circuit, successes, failures, shortCircuited, rateLimited, detail, 0, 0);
    }

    public ProviderStatus(String name, boolean enabled, String circuit, long successes, long failures,
                          long shortCircuited, long rateLimited) {
        this(name, enabled, circuit, successes, failures, shortCircuited, rateLimited, null, 0, 0);
    }
}
