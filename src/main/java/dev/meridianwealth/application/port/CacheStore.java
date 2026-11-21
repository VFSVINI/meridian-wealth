package dev.meridianwealth.application.port;

import java.time.Duration;
import java.util.Optional;

/** Cache chave-valor com TTL e contador por janela (usado no rate limit da API). */
public interface CacheStore {
    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);

    long incrementWindow(String key, Duration window);

    String kind();
}
