package dev.meridianwealth.infrastructure.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ResilienceTest {

    @Test void tokenBucketAllowsBurstThenRefills() {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter l = new TokenBucketRateLimiter(2, 1.0, nanos::get);
        assertThat(l.tryAcquire()).isTrue();
        assertThat(l.tryAcquire()).isTrue();
        assertThat(l.tryAcquire()).isFalse();
        nanos.addAndGet(1_000_000_000L);
        assertThat(l.tryAcquire()).isTrue();
        assertThat(l.tryAcquire()).isFalse();
    }

    @Test void circuitOpensAfterThresholdAndProbesAfterCooldown() {
        AtomicLong now = new AtomicLong();
        CircuitBreaker cb = new CircuitBreaker(2, Duration.ofSeconds(10), now::get);
        cb.onFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        cb.onFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
        now.set(10_000);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isFalse();
        cb.onFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        now.set(25_000);
        assertThat(cb.allowRequest()).isTrue();
        cb.onSuccess();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test void backoffGrowsExponentiallyAndIsCapped() {
        RetryPolicy p = new RetryPolicy(5, 100, 2.0, 500, 0.0);
        assertThat(p.delayMillis(1, () -> 0.5)).isEqualTo(100);
        assertThat(p.delayMillis(2, () -> 0.5)).isEqualTo(200);
        assertThat(p.delayMillis(3, () -> 0.5)).isEqualTo(400);
        assertThat(p.delayMillis(4, () -> 0.5)).isEqualTo(500);
    }

    private static ResilientExecutor executor(List<Long> sleeps, int threshold) {
        return new ResilientExecutor("t", new CircuitBreaker(threshold, Duration.ofMinutes(1), System::currentTimeMillis),
                new TokenBucketRateLimiter(100, 100, System::nanoTime), new RetryPolicy(3, 100, 2.0, 1000, 0.0), sleeps::add);
    }

    @Test void retriesWithBackoffThenSucceeds() {
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String r = executor(sleeps, 4).call(() -> {
            if (calls.incrementAndGet() < 3) throw new java.io.IOException("HTTP 503");
            return "ok";
        });
        assertThat(r).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
        assertThat(sleeps).containsExactly(100L, 200L);
    }

    @Test void clientErrorIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        ResilientExecutor ex = executor(new ArrayList<>(), 4);
        assertThatThrownBy(() -> ex.call(() -> {
            calls.incrementAndGet();
            throw new ClientErrorException("HTTP 404");
        })).isInstanceOf(ClientErrorException.class);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(ex.stats(true).circuit()).isEqualTo("CLOSED");
    }

    @Test void persistentFailuresOpenTheCircuitAndShortCircuit() {
        ResilientExecutor ex = executor(new ArrayList<>(), 2);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> ex.call(() -> { throw new java.io.IOException("HTTP 500"); }))
                    .isInstanceOf(ProviderException.class);
        }
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> ex.call(() -> calls.incrementAndGet())).isInstanceOf(CircuitOpenException.class);
        assertThat(calls.get()).isZero();
        assertThat(ex.stats(true).shortCircuited()).isEqualTo(1);
    }

    @Test void patientAcquireWaitsExactlyForTheNextToken() throws InterruptedException {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter l = new TokenBucketRateLimiter(1, 2.0, nanos::get);
        ResilientExecutor.Sleeper advance = ms -> nanos.addAndGet(ms * 1_000_000L);
        assertThat(l.acquire(1_000_000_000L, advance)).isTrue();
        assertThat(nanos.get()).isZero();
        assertThat(l.acquire(1_000_000_000L, advance)).isTrue();
        assertThat(nanos.get()).isEqualTo(500_000_000L);
    }

    @Test void patientAcquireGivesUpWithoutSleepingWhenTheDeadlineIsTooShort() throws InterruptedException {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter l = new TokenBucketRateLimiter(1, 0.5, nanos::get);
        List<Long> sleeps = new ArrayList<>();
        assertThat(l.acquire(0, sleeps::add)).isTrue();
        assertThat(l.acquire(1_000_000_000L, sleeps::add)).isFalse();
        assertThat(sleeps).isEmpty();
    }

    @Test void patientAcquireSleepsOutsideTheMonitor() throws InterruptedException {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter l = new TokenBucketRateLimiter(1, 10.0, nanos::get);
        List<Boolean> heldLock = new ArrayList<>();
        l.tryAcquire();
        assertThat(l.acquire(1_000_000_000L, ms -> {
            heldLock.add(Thread.holdsLock(l));
            nanos.addAndGet(ms * 1_000_000L);
        })).isTrue();
        assertThat(heldLock).containsOnly(false).isNotEmpty();
    }

    @Test void startupBurstLargerThanTheBucketIsServedInsteadOfDropped() throws Exception {
        ResilientExecutor ex = new ResilientExecutor("t", new CircuitBreaker(4, Duration.ofMinutes(1), System::currentTimeMillis),
                new TokenBucketRateLimiter(2, 50.0, System::nanoTime), new RetryPolicy(1, 1, 2.0, 5, 0.0), Thread::sleep);
        int calls = 10;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        List<java.util.concurrent.Future<String>> results = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            results.add(pool.submit(() -> ex.callPatiently(() -> "ok", Duration.ofSeconds(5))));
        }
        for (java.util.concurrent.Future<String> f : results) assertThat(f.get()).isEqualTo("ok");
        pool.shutdown();
        assertThat(ex.stats(true).successes()).isEqualTo(calls);
        assertThat(ex.stats(true).rateLimited()).isZero();
    }

    @Test void localRateLimitIsNeutralForTheCircuitBreaker() {
        AtomicLong nanos = new AtomicLong();
        CircuitBreaker cb = new CircuitBreaker(2, Duration.ofMinutes(1), System::currentTimeMillis);
        ResilientExecutor ex = new ResilientExecutor("t", cb, new TokenBucketRateLimiter(1, 1.0, nanos::get),
                new RetryPolicy(1, 1, 2.0, 5, 0.0), ms -> { });
        java.util.concurrent.Callable<String> boom = () -> { throw new java.io.IOException("HTTP 503"); };
        assertThatThrownBy(() -> ex.call(boom)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> ex.call(() -> "x")).isInstanceOf(RateLimitedException.class);
        nanos.addAndGet(1_000_000_000L);
        assertThatThrownBy(() -> ex.call(boom)).isInstanceOf(ProviderException.class);
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test void abandonedHalfOpenProbeDoesNotWedgeTheCircuit() {
        AtomicLong now = new AtomicLong();
        CircuitBreaker cb = new CircuitBreaker(1, Duration.ofSeconds(10), now::get);
        cb.onFailure();
        now.addAndGet(10_000);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.allowRequest()).isFalse();
        cb.onAbandoned();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test void localRateLimitFailsFast() {
        ResilientExecutor ex = new ResilientExecutor("t", new CircuitBreaker(4, Duration.ofMinutes(1), System::currentTimeMillis),
                new TokenBucketRateLimiter(1, 0.0001, System::nanoTime), new RetryPolicy(3, 1, 2.0, 5, 0.0), ms -> { });
        assertThat(ex.call(() -> "a")).isEqualTo("a");
        assertThatThrownBy(() -> ex.call(() -> "b")).isInstanceOf(RateLimitedException.class);
    }
}
