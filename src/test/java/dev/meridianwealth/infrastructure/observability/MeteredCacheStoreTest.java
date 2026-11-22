package dev.meridianwealth.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.infrastructure.cache.InMemoryCacheStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MeteredCacheStoreTest {
    @Test void countsHitsMissesAndPutsWithoutChangingTheBehavior() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeteredCacheStore cache = new MeteredCacheStore(new InMemoryCacheStore(), registry);

        assertThat(cache.get("a")).isEmpty();
        cache.put("a", "1", Duration.ofMinutes(1));
        assertThat(cache.get("a")).contains("1");
        assertThat(cache.get("a")).contains("1");
        assertThat(cache.incrementWindow("w", Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(cache.incrementWindow("w", Duration.ofMinutes(1))).isEqualTo(2);

        assertThat(registry.get("meridianwealth.cache.requests").tag("result", "hit").tag("cache", "memory").counter().count()).isEqualTo(2);
        assertThat(registry.get("meridianwealth.cache.requests").tag("result", "miss").counter().count()).isEqualTo(1);
        assertThat(registry.get("meridianwealth.cache.puts").counter().count()).isEqualTo(1);
        assertThat(cache.kind()).isEqualTo("memory");
    }
}
