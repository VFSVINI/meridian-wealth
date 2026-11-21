package dev.meridianwealth.domain.reports;

/** Leitura mecânica dos números publicados, não previsão de preço nem recomendação. */
public enum Verdict {
    POSITIVE("POSITIVO"),
    NEUTRAL("NEUTRO"),
    NEGATIVE("NEGATIVO");

    private final String label;

    Verdict(String label) { this.label = label; }

    public String label() { return label; }
}
