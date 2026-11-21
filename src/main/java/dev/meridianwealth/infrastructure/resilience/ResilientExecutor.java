package dev.meridianwealth.infrastructure.resilience;

import dev.meridianwealth.application.ProviderStatus;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compõe circuit breaker, rate limiter e retry com backoff exponencial numa chamada.
 * Rate limit local e circuito aberto falham rápido (para o chamador tentar o próximo provedor).
 */
public final class ResilientExecutor {
    public interface Sleeper { void sleep(long millis) throws InterruptedException; }

    private final String name;
    private final CircuitBreaker breaker;
    private final TokenBucketRateLimiter limiter;
    private final RetryPolicy retry;
    private final Sleeper sleeper;
    private final AtomicLong ok = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong shorted = new AtomicLong();
    private final AtomicLong limited = new AtomicLong();

    public ResilientExecutor(String name, CircuitBreaker breaker, TokenBucketRateLimiter limiter,
                             RetryPolicy retry, Sleeper sleeper) {
        this.name = name;
        this.breaker = breaker;
        this.limiter = limiter;
        this.retry = retry;
        this.sleeper = sleeper;
    }

    /** Teto de espera por token nas cargas em segundo plano (históricos). */
    public static final Duration BACKGROUND_WAIT = Duration.ofSeconds(20);

    /** Configuração padrão: 3 tentativas (250 ms, x2), circuito abre após 4 falhas por 60 s. */
    public static ResilientExecutor withDefaults(String name, int burst, double refillPerSec) {
        return new ResilientExecutor(name,
                new CircuitBreaker(4, Duration.ofSeconds(60), System::currentTimeMillis),
                new TokenBucketRateLimiter(burst, refillPerSec, System::nanoTime),
                new RetryPolicy(3, 250, 2.0, 2000, 0.2), Thread::sleep);
    }

    /** Chamada sensível a latência: sem token disponível, falha na hora para o chamador tentar outro provedor. */
    public <T> T call(Callable<T> action) {
        return execute(action, 0);
    }

    /**
     * Chamada em segundo plano (ex.: carga de histórico): aguarda o próximo token do rate limiter
     * por até {@code maxWait} em vez de descartar a requisição.
     */
    public <T> T callPatiently(Callable<T> action, Duration maxWait) {
        return execute(action, maxWait.toNanos());
    }

    private boolean acquireToken(long maxWaitNanos) {
        if (maxWaitNanos <= 0) return limiter.tryAcquire();
        try {
            return limiter.acquire(maxWaitNanos, sleeper);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private <T> T execute(Callable<T> action, long maxWaitNanos) {
        if (!breaker.allowRequest()) {
            shorted.incrementAndGet();
            throw new CircuitOpenException(name);
        }
        Exception last = null;
        for (int attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            if (!acquireToken(maxWaitNanos)) {
                limited.incrementAndGet();
                // Rejeição local não diz nada sobre a saúde do provedor: neutra para o circuito.
                breaker.onAbandoned();
                throw new RateLimitedException(name);
            }
            try {
                T result = action.call();
                breaker.onSuccess();
                ok.incrementAndGet();
                return result;
            } catch (ClientErrorException e) {
                breaker.onSuccess();
                throw e;
            } catch (Exception e) {
                last = e;
                if (attempt < retry.maxAttempts()) {
                    try {
                        sleeper.sleep(retry.delayMillis(attempt, () -> ThreadLocalRandom.current().nextDouble()));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        breaker.onFailure();
        failed.incrementAndGet();
        throw new ProviderException(name, last == null ? new IllegalStateException("interrompido") : last);
    }

    public ProviderStatus stats(boolean enabled) {
        return new ProviderStatus(name, enabled, breaker.state().name(), ok.get(), failed.get(), shorted.get(), limited.get());
    }

    public String name() { return name; }
}
