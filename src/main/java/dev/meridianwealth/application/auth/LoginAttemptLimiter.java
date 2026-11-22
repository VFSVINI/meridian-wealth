package dev.meridianwealth.application.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trava de tentativas de login erradas por chave (e-mail e IP): depois de {@code maxFailures} falhas dentro da janela, a chave
 * fica bloqueada até a falha mais antiga sair da janela. Um login certo zera a chave. É em memória (por instância); o limite por
 * minuto do filtro de API continua valendo por cima.
 */
public final class LoginAttemptLimiter {
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
        if (maxFailures < 1) throw new IllegalArgumentException("maxFailures");
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean blocked(String key) {
        Deque<Instant> d = failures.get(key);
        if (d == null) return false;
        synchronized (d) {
            prune(d);
            return d.size() >= maxFailures;
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > 100_000) failures.entrySet().removeIf(e -> { synchronized (e.getValue()) { prune(e.getValue()); return e.getValue().isEmpty(); } });
        Deque<Instant> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            prune(d);
            d.addLast(clock.instant());
        }
    }

    public void recordSuccess(String key) { failures.remove(key); }

    private void prune(Deque<Instant> d) {
        Instant cutoff = clock.instant().minus(window);
        while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) d.removeFirst();
    }
}
