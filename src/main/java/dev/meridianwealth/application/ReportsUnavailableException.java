package dev.meridianwealth.application;

/** O ativo existe, mas não há relatórios curados para ele (nada é inventado para preencher). */
public class ReportsUnavailableException extends RuntimeException {
    private final String symbol;

    public ReportsUnavailableException(String symbol) {
        super("Sem relatórios publicados para " + symbol + ": o radar cobre ações e FIIs brasileiros com dados abertos da CVM; "
                + "ativos de outros mercados não têm fonte integrada.");
        this.symbol = symbol;
    }

    public String symbol() { return symbol; }
}
