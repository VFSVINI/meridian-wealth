package dev.meridianwealth.domain;

/**
 * Séries do Banco Central com histórico para os gráficos da página Brasil (item #50). Todas são do SGS e foram conferidas contra a API real em
 * 2026-11-27: a 432 é a meta Selic (preenchida para a frente com o valor vigente), a 4389 o CDI anualizado, a 13522 e a 433 o IPCA, e a série 1 é o dólar
 * comercial de venda, que coincide com o PTAX de venda (5,7446 nas duas fontes em 26/11/2026).
 */
public enum HistorySeries {
    SELIC_TARGET("selic", 432, "Meta Selic definida pelo Copom", "% a.a.", true),
    CDI_ANNUAL("cdiAnnual", 4389, "CDI anualizado (base 252)", "% a.a.", false),
    IPCA_12M("ipca12m", 13522, "IPCA, acumulado em 12 meses", "% em 12 meses", false),
    IPCA_MONTHLY("ipcaMonthly", 433, "IPCA, variação mensal", "% no mês", false),
    PTAX_SELL("ptax", 1, "Dólar PTAX (venda)", "R$ por US$", false);

    private final String key;
    private final int sgsCode;
    private final String label;
    private final String unit;
    private final boolean forwardFilled;

    HistorySeries(String key, int sgsCode, String label, String unit, boolean forwardFilled) {
        this.key = key;
        this.sgsCode = sgsCode;
        this.label = label;
        this.unit = unit;
        this.forwardFilled = forwardFilled;
    }

    public String key() { return key; }

    public int sgsCode() { return sgsCode; }

    public String label() { return label; }

    public String unit() { return unit; }

    /** A série repete o valor vigente: a data de cada ponto é "vigente em", não a de uma publicação. */
    public boolean forwardFilled() { return forwardFilled; }

    public String source() { return "BCB/SGS " + sgsCode; }
}
