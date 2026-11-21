package dev.meridianwealth.domain;

/**
 * De onde o preço do ativo deve vir. É a intenção de projeto: hoje só {@link #MERCADO} está implementado
 * (cotação em bolsa ou provedor); as demais fontes entram junto com a carteira (itens #12 e #13).
 */
public enum PriceSource {
    /** Cotação de mercado por provedor ou bolsa. */
    MERCADO("Cotação de mercado"),
    /** Tabela oficial publicada (ex.: preços e taxas do Tesouro Direto, cotas de fundos). */
    TABELA_OFICIAL("Tabela oficial publicada"),
    /** Sem cotação pública: taxa e valor informados pelo investidor. */
    INFORMADO_PELO_USUARIO("Informado pelo investidor");

    private final String label;

    PriceSource(String label) { this.label = label; }

    public String label() { return label; }
}
