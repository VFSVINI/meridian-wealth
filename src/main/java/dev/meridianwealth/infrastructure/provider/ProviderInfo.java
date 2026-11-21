package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.infrastructure.resilience.ProviderStats;

public interface ProviderInfo {
    ProviderStats stats();
}
