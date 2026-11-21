package dev.meridianwealth.domain;

/**
 * Selo de frescor de uma cotação: diz ao usuário, sem rodeios, quão atual é o preço exibido.
 *
 * <p>O selo é atribuído pela fonte (contrato público do provedor) e pelo estado do pregão: com o
 * mercado fechado, qualquer cotação é o preço de fechamento.
 */
public enum Freshness {
    /** Negócio a negócio ou com atraso de segundos (ex.: Finnhub para ações dos EUA). */
    REAL_TIME("tempo real", "atraso de segundos"),
    /** Cotação atrasada pelas regras das bolsas para dados gratuitos (Yahoo, Stooq). */
    DELAYED("~15 min", "atraso típico de 15 minutos ou mais"),
    /** Preço de fechamento ou taxa de referência diária (pregão fechado, BCE via Frankfurter). */
    END_OF_DAY("fechamento", "preço de fechamento ou referência diária"),
    /** Nenhum provedor respondeu: último dado real gravado no banco. */
    STORED("último dado real gravado", "sem dado em tempo real; exibindo o último dado real do banco");

    private final String label;
    private final String description;

    Freshness(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }

    public String description() { return description; }
}
