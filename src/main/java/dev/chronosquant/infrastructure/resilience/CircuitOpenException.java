package dev.chronosquant.infrastructure.resilience;

public class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String name) { super("Circuit breaker aberto: " + name); }
}
