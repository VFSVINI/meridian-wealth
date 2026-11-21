package dev.meridianwealth.domain;

/** Indexador da remuneração de um título de renda fixa. */
public enum Indexer {
    PRE_FIXADO("Pré-fixado"),
    IPCA_MAIS("IPCA + taxa"),
    SELIC("Selic"),
    /** CDI, pré ou IPCA: o papel define o indexador na emissão (LCI, LCA, CRI, CRA, notas). */
    CONFORME_EMISSAO("Definido na emissão"),
    NENHUM("Sem indexador");

    private final String label;

    Indexer(String label) { this.label = label; }

    public String label() { return label; }
}
