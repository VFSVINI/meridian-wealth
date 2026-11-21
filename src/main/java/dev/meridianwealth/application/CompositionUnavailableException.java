package dev.meridianwealth.application;

/** O ativo existe, mas não há composição curada publicada para ele (nada é inventado para preencher). */
public class CompositionUnavailableException extends RuntimeException {
    private final String symbol;

    public CompositionUnavailableException(String symbol) {
        super("Sem composição publicada para " + symbol + ": só há dados curados com fonte para alguns fundos imobiliários.");
        this.symbol = symbol;
    }

    public String symbol() { return symbol; }
}
