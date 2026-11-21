package dev.meridianwealth.domain.reports;

/** Tipo de emissor, que define as regras de prazo e de veredito. */
public enum IssuerKind {
    COMPANY("Companhia aberta"),
    FII("Fundo imobiliário");

    private final String label;

    IssuerKind(String label) { this.label = label; }

    public String label() { return label; }
}
