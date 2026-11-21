package dev.meridianwealth.application;

/**
 * Saúde de um provedor externo: circuito, sucessos, falhas, chamadas barradas e rate limit local.
 * {@code detail} é um texto livre opcional para provedores com estado próprio (ex.: qual bolsa do
 * stream de cripto está ativa e quais estão bloqueadas).
 */
public record ProviderStatus(String name, boolean enabled, String circuit, long successes, long failures,
                             long shortCircuited, long rateLimited, String detail) {
    public ProviderStatus(String name, boolean enabled, String circuit, long successes, long failures,
                          long shortCircuited, long rateLimited) {
        this(name, enabled, circuit, successes, failures, shortCircuited, rateLimited, null);
    }
}
