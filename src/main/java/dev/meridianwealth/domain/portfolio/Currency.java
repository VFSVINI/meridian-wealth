package dev.meridianwealth.domain.portfolio;

/** Moedas em que uma posição pode ser mantida. Só BRL e USD: são as que têm conversão oficial (PTAX) na plataforma. */
public enum Currency {
    BRL("R$"),
    USD("US$");

    private final String symbol;

    Currency(String symbol) { this.symbol = symbol; }

    public String symbol() { return symbol; }

    /** Código de moeda de um instrumento do universo (por exemplo "BRL"); vazio se não for suportada (EUR, JPY, GBp...). */
    public static java.util.Optional<Currency> fromCode(String code) {
        if (code == null) return java.util.Optional.empty();
        for (Currency c : values()) if (c.name().equals(code)) return java.util.Optional.of(c);
        return java.util.Optional.empty();
    }
}
