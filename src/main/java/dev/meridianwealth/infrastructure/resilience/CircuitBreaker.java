package dev.meridianwealth.infrastructure.resilience;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Circuit breaker CLOSED, OPEN e HALF_OPEN (uma chamada de sondagem por vez). */
public final class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMillis;
    private final LongSupplier clockMillis;
    private State state = State.CLOSED;
    private int failures;
    private long openedAt;
    private boolean probeInFlight;

    public CircuitBreaker(int failureThreshold, Duration openDuration, LongSupplier clockMillis) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openDuration.toMillis();
        this.clockMillis = clockMillis;
    }

    public synchronized boolean allowRequest() {
        if (state == State.OPEN && clockMillis.getAsLong() - openedAt >= openMillis) {
            state = State.HALF_OPEN;
            probeInFlight = false;
        }
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (probeInFlight) yield false;
                probeInFlight = true;
                yield true;
            }
        };
    }

    public synchronized void onSuccess() {
        failures = 0;
        probeInFlight = false;
        state = State.CLOSED;
    }

    public synchronized void onFailure() {
        probeInFlight = false;
        if (state == State.HALF_OPEN || ++failures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clockMillis.getAsLong();
        }
    }

    public synchronized State state() { return state; }
}
