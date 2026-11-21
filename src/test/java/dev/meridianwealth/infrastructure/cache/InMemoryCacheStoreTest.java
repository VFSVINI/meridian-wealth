package dev.meridianwealth.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryCacheStoreTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final InMemoryCacheStore cache = new InMemoryCacheStore(now::get, 100);

    @Test void storesAndExpires() {
        cache.put("k", "v", Duration.ofMillis(40));
        assertThat(cache.get("k")).contains("v");
        now.addAndGet(40);
        assertThat(cache.get("k")).isEmpty();
    }

    @Test void windowCounterIncrements() {
        assertThat(cache.incrementWindow("rl:x", Duration.ofMinutes(10))).isEqualTo(1);
        assertThat(cache.incrementWindow("rl:x", Duration.ofMinutes(10))).isEqualTo(2);
        assertThat(cache.incrementWindow("rl:y", Duration.ofMinutes(10))).isEqualTo(1);
    }

    @Test void windowRolloverRestartsCount() {
        Duration minute = Duration.ofMinutes(1);
        now.set(60_000 * 10);
        cache.incrementWindow("rl:x", minute);
        cache.incrementWindow("rl:x", minute);
        now.addAndGet(60_000);
        assertThat(cache.incrementWindow("rl:x", minute)).isEqualTo(1);
    }

    @Test void expiredEntriesAreSweptWithoutBeingReadAgain() {
        for (int i = 0; i < 50; i++) {
            cache.put("bt:" + i, "{}", Duration.ofSeconds(60));
        }
        assertThat(cache.size()).isEqualTo(50);
        now.addAndGet(61_000);
        cache.put("outra", "v", Duration.ofSeconds(60));
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test void countersOfIdleClientsAreDiscarded() {
        for (int i = 0; i < 30; i++) {
            cache.incrementWindow("rl:10.0.0." + i, Duration.ofMinutes(1));
        }
        now.addAndGet(120_000);
        cache.incrementWindow("rl:ativo", Duration.ofMinutes(1));
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test void capacityCeilingDropsWritesInsteadOfGrowing() {
        for (int i = 0; i < 150; i++) {
            cache.put("k" + i, "v", Duration.ofMinutes(5));
        }
        assertThat(cache.size()).isEqualTo(100);
        assertThat(cache.get("k0")).contains("v");
        assertThat(cache.get("k149")).isEmpty();
        cache.put("k0", "novo", Duration.ofMinutes(5));
        assertThat(cache.get("k0")).contains("novo");
    }

    @Test void rejectsInvalidArguments() {
        assertThatThrownBy(() -> new InMemoryCacheStore(now::get, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.incrementWindow("rl:x", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void concurrentIncrementsAreNotLost() throws Exception {
        // Relógio fixo: a janela não pode virar no meio do teste.
        InMemoryCacheStore real = new InMemoryCacheStore(() -> 5_000L, 100);
        int threads = 8;
        int perThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    real.incrementWindow("rl:concorrente", Duration.ofHours(1));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(real.incrementWindow("rl:concorrente", Duration.ofHours(1)))
                .isEqualTo((long) threads * perThread + 1);
    }
}
