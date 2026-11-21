package dev.meridianwealth.domain.reports;

/**
 * Periodicidades com base regulatória nas fontes usadas: informe mensal de FII, informes trimestrais (ITR de
 * companhias; informe trimestral de FII) e anual (DFP; o 4T sai junto com ela). Bimestral e semestral não são
 * obrigações dos emissores cobertos e por isso não existem aqui.
 */
public enum ReportPeriodicity {
    MONTHLY("Mensal"),
    QUARTERLY("Trimestral"),
    ANNUAL("Anual");

    private final String label;

    ReportPeriodicity(String label) { this.label = label; }

    public String label() { return label; }
}
