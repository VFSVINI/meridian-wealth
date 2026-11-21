package dev.meridianwealth.application;

/** O ativo existe, mas não há dado real suficiente para responder (nenhum valor é inventado). */
public class DataUnavailableException extends RuntimeException {
    private final String symbol;
    private final DataStatus status;

    public DataUnavailableException(String symbol, DataStatus status, String message) {
        super(message);
        this.symbol = symbol;
        this.status = status;
    }

    public String symbol() { return symbol; }

    public DataStatus status() { return status; }
}
