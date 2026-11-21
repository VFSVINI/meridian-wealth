package dev.meridianwealth.application;

/** Saúde de um provedor externo: circuito, sucessos, falhas, chamadas barradas e rate limit local. */
public record ProviderStatus(String name, boolean enabled, String circuit, long successes, long failures,
                             long shortCircuited, long rateLimited) {}
