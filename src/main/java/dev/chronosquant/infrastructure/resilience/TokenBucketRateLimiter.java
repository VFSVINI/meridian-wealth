package dev.chronosquant.infrastructure.resilience;

import java.util.function.LongSupplier;

/** Rate limiter por balde de fichas: {@code capacity} de rajada e {@code refillPerSec} de reposição. */
public final class TokenBucketRateLimiter {
    private final int capacity;
    private final double refillPerSec;
    private final LongSupplier nanoClock;
    private double tokens;
    private long last;

    public TokenBucketRateLimiter(int capacity, double refillPerSec, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.last = nanoClock.getAsLong();
    }

    public synchronized boolean tryAcquire() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) / 1e9 * refillPerSec);
        last = now;
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }
}
