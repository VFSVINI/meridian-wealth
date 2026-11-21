package dev.meridianwealth.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryCacheStoreTest {
    private final InMemoryCacheStore cache = new InMemoryCacheStore();

    @Test void storesAndExpires() throws InterruptedException {
        cache.put("k", "v", Duration.ofMillis(40));
        assertThat(cache.get("k")).contains("v");
        Thread.sleep(80);
        assertThat(cache.get("k")).isEmpty();
    }

    @Test void windowCounterIncrements() {
        assertThat(cache.incrementWindow("rl:x", Duration.ofMinutes(10))).isEqualTo(1);
        assertThat(cache.incrementWindow("rl:x", Duration.ofMinutes(10))).isEqualTo(2);
        assertThat(cache.incrementWindow("rl:y", Duration.ofMinutes(10))).isEqualTo(1);
    }
}
