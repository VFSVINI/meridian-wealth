package dev.meridianwealth.domain;

/**
 * Indicadores oficiais do Banco Central acompanhados. {@code sgsCode} é o código no Sistema Gerenciador de
 * Séries Temporais (nulo para o PTAX, que vem do serviço Olinda). Como cada código foi confirmado está em
 * {@code docs/mercados.md}.
 */
public enum RateSeries {
    /**
     * A série 432 é preenchida para a frente com o valor vigente (em 2026-11-21 ia até 09/12/2026, todos com
     * 13.75): a data é "vigente em", não a data de uma publicação.
     */
    SELIC_TARGET("selicTarget", 432, "Meta Selic definida pelo Copom", "% a.a.", Periodicity.DAILY, "BCB/SGS 432", true),
    CDI_DAILY("cdiDaily", 12, "CDI (taxa diária)", "% a.d.", Periodicity.DAILY, "BCB/SGS 12", false),
    CDI_ANNUAL("cdiAnnual", 4389, "CDI anualizado (base 252)", "% a.a.", Periodicity.DAILY, "BCB/SGS 4389", false),
    IPCA_MONTHLY("ipcaMonthly", 433, "IPCA, variação mensal", "% no mês", Periodicity.MONTHLY, "BCB/SGS 433", false),
    IPCA_12M("ipca12m", 13522, "IPCA, acumulado em 12 meses", "% em 12 meses", Periodicity.MONTHLY, "BCB/SGS 13522", false),
    PTAX_BUY("ptaxBuy", null, "PTAX, dólar (compra)", "R$ por US$", Periodicity.DAILY, "BCB/PTAX (Olinda)", false),
    PTAX_SELL("ptaxSell", null, "PTAX, dólar (venda)", "R$ por US$", Periodicity.DAILY, "BCB/PTAX (Olinda)", false);

    /** Frequência de publicação; define a tolerância antes de o dado ser tratado como desatualizado. */
    public enum Periodicity {
        /** Publicada em dias úteis; tolera até 5 dias (fim de semana prolongado ou feriado). */
        DAILY(5),
        /** Publicada uma vez por mês, com a data de referência no 1º dia do mês; tolera 75 dias. */
        MONTHLY(75);

        private final int toleranceDays;

        Periodicity(int toleranceDays) { this.toleranceDays = toleranceDays; }

        public int toleranceDays() { return toleranceDays; }
    }

    private final String key;
    private final Integer sgsCode;
    private final String label;
    private final String unit;
    private final Periodicity periodicity;
    private final String source;
    private final boolean forwardFilled;

    RateSeries(String key, Integer sgsCode, String label, String unit, Periodicity periodicity, String source,
               boolean forwardFilled) {
        this.key = key;
        this.sgsCode = sgsCode;
        this.label = label;
        this.unit = unit;
        this.periodicity = periodicity;
        this.source = source;
        this.forwardFilled = forwardFilled;
    }

    public String key() { return key; }

    public Integer sgsCode() { return sgsCode; }

    public String label() { return label; }

    public String unit() { return unit; }

    public Periodicity periodicity() { return periodicity; }

    public String source() { return source; }

    /** A série repete o valor vigente para datas futuras: a data não mede a idade do dado. */
    public boolean forwardFilled() { return forwardFilled; }

    /** Como ler a data: "vigente em" para séries preenchidas para a frente, "referência" para as demais. */
    public String dateLabel() { return forwardFilled ? "vigente em" : "referência"; }
}
