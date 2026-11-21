package dev.meridianwealth.infrastructure.cache;

import dev.meridianwealth.application.port.CacheStore;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cache em memória com TTL e contador por janela fixa, para uso sem Redis.
 *
 * <p>Garantias de memória: entradas expiradas e janelas vencidas são removidas por uma limpeza
 * amortizada (no máximo uma varredura por {@link #SWEEP_INTERVAL_MS}, executada pela thread que
 * vencer um compare-and-set), mesmo que nunca sejam lidas de novo. Um teto de entradas impede
 * que chaves controladas por clientes (ex.: parâmetros de backtest) esgotem o heap: ao atingir o
 * teto, a escrita é descartada, o que é aceitável para um cache.
 */
@Component
@ConditionalOnProperty(name = "meridianwealth.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryCacheStore implements CacheStore {
    static final int DEFAULT_MAX_ENTRIES = 10_000;
    static final long SWEEP_INTERVAL_MS = 1_000;

    private record Entry(String value, long expiresAt) {}

    /** Contagem de uma chave dentro de uma janela; imutável e trocada atomicamente via compute. */
    private record Window(long bucket, long expiresAt, long count) {}

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong();
    private final LongSupplier clock;
    private final int maxEntries;

    @Autowired
    public InMemoryCacheStore() {
        this(System::currentTimeMillis, DEFAULT_MAX_ENTRIES);
    }

    InMemoryCacheStore(LongSupplier clock, int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries deve ser positivo");
        this.clock = clock;
        this.maxEntries = maxEntries;
        this.lastSweep.set(clock.getAsLong());
    }

    @Override
    public Optional<String> get(String key) {
        long now = clock.getAsLong();
        maybeSweep(now);
        Entry e = entries.get(key);
        if (e == null) return Optional.empty();
        if (e.expiresAt() <= now) {
            entries.remove(key, e);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        long now = clock.getAsLong();
        maybeSweep(now);
        if (!entries.containsKey(key) && entries.size() >= maxEntries) {
            sweep(now);
            if (entries.size() >= maxEntries) return;
        }
        entries.put(key, new Entry(value, now + ttl.toMillis()));
    }

    @Override
    public long incrementWindow(String key, Duration window) {
        long now = clock.getAsLong();
        maybeSweep(now);
        long size = window.toMillis();
        if (size <= 0) throw new IllegalArgumentException("A janela deve ser positiva");
        long bucket = now / size;
        Window w = windows.compute(key, (k, old) -> old == null || old.bucket() != bucket
                ? new Window(bucket, (bucket + 1) * size, 1)
                : new Window(bucket, old.expiresAt(), old.count() + 1));
        return w.count();
    }

    @Override
    public String kind() { return "memory"; }

    int size() {
        return entries.size() + windows.size();
    }

    private void maybeSweep(long now) {
        long last = lastSweep.get();
        if (now - last >= SWEEP_INTERVAL_MS && lastSweep.compareAndSet(last, now)) {
            sweep(now);
        }
    }

    private void sweep(long now) {
        entries.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        windows.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }
}
