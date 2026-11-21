package dev.meridianwealth.infrastructure.resilience;

import java.util.function.DoubleSupplier;

/** Retry com backoff exponencial e jitter: atraso = min(max, inicial · mult^(tentativa-1)) · (1 ± jitter). */
public record RetryPolicy(int maxAttempts, long initialDelayMs, double multiplier, long maxDelayMs, double jitter) {

    public long delayMillis(int attempt, DoubleSupplier random) {
        double base = Math.min(maxDelayMs, initialDelayMs * Math.pow(multiplier, attempt - 1));
        double factor = 1 + jitter * (2 * random.getAsDouble() - 1);
        return Math.max(0, Math.round(base * factor));
    }
}
