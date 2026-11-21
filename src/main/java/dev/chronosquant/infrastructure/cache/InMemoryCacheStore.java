package dev.chronosquant.infrastructure.cache;

import dev.chronosquant.application.port.CacheStore;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chronosquant.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryCacheStore implements CacheStore {
    private record Entry(String value, long expiresAt) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        Entry e = entries.get(key);
        if (e == null) return Optional.empty();
        if (e.expiresAt() < System.currentTimeMillis()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public long incrementWindow(String key, Duration window) {
        long bucket = System.currentTimeMillis() / window.toMillis();
        String k = key + ":" + bucket;
        counters.keySet().removeIf(old -> old.startsWith(key + ":") && !old.equals(k));
        return counters.computeIfAbsent(k, x -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public String kind() { return "memory"; }
}
