package dev.meridianwealth.domain.composition;

/** Natureza da fonte de um dado: documento do próprio fundo ou da gestora, ou um agregador de terceiros. */
public enum SourceKind {
    OFFICIAL("Fonte oficial"),
    THIRD_PARTY("Fonte de terceiros");

    private final String label;

    SourceKind(String label) { this.label = label; }

    public String label() { return label; }
}
