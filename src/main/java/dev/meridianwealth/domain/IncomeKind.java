package dev.meridianwealth.domain;

/** Como o ativo costuma devolver renda ao investidor. */
public enum IncomeKind {
    /** Dividendos, JCP ou rendimentos distribuídos pelo emissor. */
    PROVENTOS("Proventos distribuídos"),
    /** Juros pagos a cada seis meses, com o principal no vencimento. */
    CUPOM_SEMESTRAL("Cupom semestral"),
    /** Principal e juros pagos juntos no vencimento. */
    NO_VENCIMENTO("Tudo no vencimento"),
    /** Depende do regulamento ou das condições da emissão. */
    CONFORME_REGRAS("Conforme emissão ou regulamento"),
    /** Sem renda periódica: o retorno vem da variação do preço ou da cota. */
    NENHUM("Sem renda periódica");

    private final String label;

    IncomeKind(String label) { this.label = label; }

    public String label() { return label; }
}
