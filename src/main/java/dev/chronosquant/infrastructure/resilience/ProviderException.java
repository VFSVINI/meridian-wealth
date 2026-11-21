package dev.chronosquant.infrastructure.resilience;

public class ProviderException extends RuntimeException {
    public ProviderException(String provider, Throwable cause) {
        super("Falha no provedor " + provider + ": " + cause.getMessage(), cause);
    }

    public ProviderException(String message) { super(message); }
}
