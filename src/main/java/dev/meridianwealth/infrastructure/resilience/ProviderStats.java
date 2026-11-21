package dev.meridianwealth.infrastructure.resilience;

public record ProviderStats(String name, boolean enabled, String circuit, long successes, long failures,
                            long shortCircuited, long rateLimited) {}
