package dev.meridianwealth.infrastructure.observability;

import dev.meridianwealth.application.port.CacheStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;

/**
 * Decora um {@link CacheStore} com contadores de acerto e erro ({@code meridianwealth.cache.requests{result}}) e de gravação
 * ({@code meridianwealth.cache.puts}). Cada acerto é uma chamada a um provedor gratuito que não foi feita: a razão acertos/total é o
 * indicador direto de quanta cota o cache está poupando (FinOps).
 */
public class MeteredCacheStore implements CacheStore {
    private final CacheStore delegate;
    private final Counter hits;
    private final Counter misses;
    private final Counter puts;

    public MeteredCacheStore(CacheStore delegate, MeterRegistry registry) {
        this.delegate = delegate;
        String kind = delegate.kind();
        this.hits = Counter.builder("meridianwealth.cache.requests").tag("cache", kind).tag("result", "hit").register(registry);
        this.misses = Counter.builder("meridianwealth.cache.requests").tag("cache", kind).tag("result", "miss").register(registry);
        this.puts = Counter.builder("meridianwealth.cache.puts").tag("cache", kind).register(registry);
    }

    @Override
    public Optional<String> get(String key) {
        Optional<String> v = delegate.get(key);
        (v.isPresent() ? hits : misses).increment();
        return v;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        delegate.put(key, value, ttl);
        puts.increment();
    }

    @Override
    public long incrementWindow(String key, Duration window) { return delegate.incrementWindow(key, window); }

    @Override
    public String kind() { return delegate.kind(); }
}
