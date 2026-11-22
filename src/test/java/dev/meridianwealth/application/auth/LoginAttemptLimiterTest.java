package dev.meridianwealth.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-21T12:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(10), clock);

    @Test void blocksAfterTheMaximumNumberOfFailuresInsideTheWindow() {
        assertThat(limiter.blocked("k")).isFalse();
        limiter.recordFailure("k");
        limiter.recordFailure("k");
        assertThat(limiter.blocked("k")).isFalse();
        limiter.recordFailure("k");
        assertThat(limiter.blocked("k")).isTrue();
        assertThat(limiter.blocked("outra")).as("a trava é por chave").isFalse();
    }

    @Test void failuresOlderThanTheWindowStopCounting() {
        limiter.recordFailure("k");
        limiter.recordFailure("k");
        clock.advance(Duration.ofMinutes(6));
        limiter.recordFailure("k");
        assertThat(limiter.blocked("k")).isTrue();
        clock.advance(Duration.ofMinutes(5)); // as duas primeiras saíram da janela
        assertThat(limiter.blocked("k")).isFalse();
    }

    @Test void aSuccessResetsTheKey() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("k");
        limiter.recordSuccess("k");
        assertThat(limiter.blocked("k")).isFalse();
    }

    @Test void rejectsAnInvalidLimit() {
        assertThatThrownBy(() -> new LoginAttemptLimiter(0, Duration.ofMinutes(1), clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
