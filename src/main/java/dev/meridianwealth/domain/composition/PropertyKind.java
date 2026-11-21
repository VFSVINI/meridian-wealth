package dev.meridianwealth.domain.composition;

/** Tipo de imóvel, somente quando a fonte o informa. */
public enum PropertyKind {
    GALPAO_LOGISTICO("Galpão logístico"),
    EDIFICIO_COMERCIAL("Edifício comercial");

    private final String label;

    PropertyKind(String label) { this.label = label; }

    public String label() { return label; }
}
