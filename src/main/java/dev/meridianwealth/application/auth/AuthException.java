package dev.meridianwealth.application.auth;

/** Falha de autenticação ou cadastro, com um código estável para a borda mapear em status HTTP. */
public class AuthException extends RuntimeException {
    public enum Code {
        INVALID_INPUT,
        INVALID_CREDENTIALS,
        EMAIL_TAKEN,
        EMAIL_REGISTERED_WITH_PASSWORD,
        EMAIL_REGISTERED_WITH_GOOGLE,
        INVALID_REFRESH_TOKEN,
        REFRESH_TOKEN_REUSED,
        INVALID_GOOGLE_TOKEN,
        GOOGLE_NOT_CONFIGURED,
        DEMO_DISABLED,
        TOO_MANY_ATTEMPTS,
        CAPACITY_REACHED,
        FORBIDDEN
    }

    private final Code code;

    public AuthException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
