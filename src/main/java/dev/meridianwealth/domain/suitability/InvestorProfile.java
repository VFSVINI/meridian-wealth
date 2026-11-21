package dev.meridianwealth.domain.suitability;

/**
 * Perfis de risco do produto, do mais cauteloso ao mais arrojado. A ordem do enum é a ordem de risco: os tetos do
 * questionário comparam perfis por ela.
 */
public enum InvestorProfile {
    CONSERVATIVE("CONSERVADOR"),
    MODERATE("MODERADO"),
    BOLD("ARROJADO"),
    AGGRESSIVE("AGRESSIVO");

    private final String label;

    InvestorProfile(String label) { this.label = label; }

    public String label() { return label; }

    /** O menor entre dois perfis (o mais cauteloso). */
    public static InvestorProfile min(InvestorProfile a, InvestorProfile b) { return a.compareTo(b) <= 0 ? a : b; }
}
