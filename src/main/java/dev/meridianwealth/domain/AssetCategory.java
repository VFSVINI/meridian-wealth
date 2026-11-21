package dev.meridianwealth.domain;

/** Grande classe do ativo, usada para agrupar a carteira e, mais adiante, o perfil de suitability. */
public enum AssetCategory {
    RENDA_VARIAVEL("Renda variável"),
    FUNDOS("Fundos de investimento"),
    RENDA_FIXA_PUBLICA("Renda fixa pública"),
    RENDA_FIXA_PRIVADA("Renda fixa privada"),
    CRIPTO("Criptoativos"),
    CAMBIO_E_COMMODITIES("Câmbio e commodities"),
    /** Índices e taxas: servem de referência, não se compram. */
    REFERENCIA("Referência (não negociável)");

    private final String label;

    AssetCategory(String label) { this.label = label; }

    public String label() { return label; }
}
