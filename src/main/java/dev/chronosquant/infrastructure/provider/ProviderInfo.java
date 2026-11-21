package dev.chronosquant.infrastructure.provider;

import dev.chronosquant.infrastructure.resilience.ProviderStats;

public interface ProviderInfo {
    ProviderStats stats();
}
