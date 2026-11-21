package dev.chronosquant.infrastructure.resilience;

public class RateLimitedException extends RuntimeException {
    public RateLimitedException(String name) { super("Rate limit local atingido: " + name); }
}
