package dev.meridianwealth.application.portfolio;

/** Falha numa operação de carteira, com um código estável para a borda mapear em status HTTP. */
public class PortfolioException extends RuntimeException {
    public enum Code { INVALID_INPUT, NOT_FOUND, POSITION_EXISTS, VERSION_CONFLICT, LIMIT_REACHED, OUT_OF_ORDER, FX_UNAVAILABLE, IDEMPOTENCY_KEY_REUSED }

    private final Code code;

    public PortfolioException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
