package dev.chronosquant.infrastructure.cache;

import dev.chronosquant.application.port.CacheStore;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Cache em Redis. Falhas do Redis nunca derrubam o fluxo: viram miss de cache. */
@Component
@ConditionalOnProperty(name = "chronosquant.cache.type", havingValue = "redis")
public class RedisCacheStore implements CacheStore {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheStore.class);
    private static final String PREFIX = "chronosquant:";

    private final StringRedisTemplate redis;

    public RedisCacheStore(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + key));
        } catch (RuntimeException e) {
            log.warn("Redis indisponível (get {}): {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + key, value, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis indisponível (put {}): {}", key, e.getMessage());
        }
    }

    @Override
    public long incrementWindow(String key, Duration window) {
        try {
            String k = PREFIX + key + ":" + System.currentTimeMillis() / window.toMillis();
            Long n = redis.opsForValue().increment(k);
            if (n != null && n == 1) redis.expire(k, window.plusSeconds(1));
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.warn("Redis indisponível (rate limit): {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public String kind() { return "redis"; }
}
