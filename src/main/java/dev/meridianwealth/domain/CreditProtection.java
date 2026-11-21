package dev.meridianwealth.domain;

import java.math.BigDecimal;

/**
 * Proteção ao crédito do investidor, conforme a lista pública do Fundo Garantidor de Créditos (FGC).
 *
 * <p>Fonte: fgc.org.br ("Sobre a garantia FGC"), lida em 2026-11-20. Garantidos: conta corrente e
 * poupança, CDB, RDB, LCI, LCD, LCA, LH e LC. Não garantidos, entre outros: títulos públicos, fundos de
 * renda fixa, debêntures, CRI e CRA. Notas comerciais e notas promissórias não constam entre os
 * produtos garantidos.
 */
public enum CreditProtection {
    /** Título emitido pela União (Tesouro Nacional); o FGC não cobre títulos públicos. */
    RISCO_SOBERANO("Risco soberano (União)"),
    /** Produto da lista de garantidos do FGC, até o limite por CPF e instituição. */
    FGC_ATE_LIMITE("Garantido pelo FGC até o limite"),
    /** Produto que não consta na lista de garantidos do FGC. */
    SEM_GARANTIA_FGC("Sem garantia do FGC"),
    NAO_SE_APLICA("Não se aplica (risco de mercado)");

    /** Limite do FGC por CPF e por instituição ou conglomerado, em reais (fgc.org.br, 2026-11-20). */
    public static final BigDecimal FGC_LIMIT_PER_CPF_BRL = new BigDecimal("250000");
    /** Teto do FGC por CPF em um período de quatro anos, em reais (fgc.org.br, 2026-11-20). */
    public static final BigDecimal FGC_LIMIT_FOUR_YEARS_BRL = new BigDecimal("1000000");

    private final String label;

    CreditProtection(String label) { this.label = label; }

    public String label() { return label; }
}
