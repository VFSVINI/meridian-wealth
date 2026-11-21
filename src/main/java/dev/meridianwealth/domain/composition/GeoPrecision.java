package dev.meridianwealth.domain.composition;

/** Até onde a coordenada localiza o imóvel. */
public enum GeoPrecision {
    /** O OpenStreetMap achou o mesmo número de porta do endereço publicado. */
    ADDRESS("Endereço (número de porta conferido)"),
    /** Só a rua foi encontrada: o ponto é o centro da via, uma posição aproximada. */
    STREET("Via (posição aproximada)");

    private final String label;

    GeoPrecision(String label) { this.label = label; }

    public String label() { return label; }
}
