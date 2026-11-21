package dev.meridianwealth.infrastructure.resilience;

import java.util.function.LongSupplier;

/**
 * Rate limiter por balde de fichas: {@code capacity} de rajada e {@code refillPerSec} de reposição.
 *
 * <p>{@link #tryAcquire()} falha na hora (cotações no polling, onde há failover e latência importa);
 * {@link #acquire(long, ResilientExecutor.Sleeper)} aguarda o próximo token até um teto (cargas em
 * segundo plano, como históricos), dormindo sempre fora do monitor.
 */
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
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /**
     * Aguarda um token por até {@code maxWaitNanos}. Calcula a espera exata pelo déficit, dorme
     * fora do lock e tenta de novo (outra thread pode ter levado o token). Devolve {@code false}
     * sem dormir quando a espera necessária ultrapassa o prazo.
     */
    public boolean acquire(long maxWaitNanos, ResilientExecutor.Sleeper sleeper) throws InterruptedException {
        long deadline = nanoClock.getAsLong() + Math.max(0, maxWaitNanos);
        while (true) {
            long waitNanos;
            synchronized (this) {
                refill();
                if (tokens >= 1) {
                    tokens -= 1;
                    return true;
                }
                if (refillPerSec <= 0) return false;
                waitNanos = (long) Math.ceil((1 - tokens) / refillPerSec * 1e9);
            }
            if (nanoClock.getAsLong() + waitNanos > deadline) return false;
            sleeper.sleep(Math.max(1, (waitNanos + 999_999) / 1_000_000));
        }
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) / 1e9 * refillPerSec);
        last = now;
    }
}
