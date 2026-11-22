package dev.meridianwealth.domain.portfolio;

import dev.meridianwealth.domain.AssetType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Apoio ao imposto de renda a partir do livro de movimentações: o que o investidor precisa para preencher a declaração (posição e custo
 * em reais em 31/12) e o resultado realizado de cada mês, por classe. <b>Não calcula imposto devido, não gera DARF e não preenche a
 * declaração</b>: só apresenta o que a base legal confirmada em fonte oficial sustenta (ver {@link #LEGAL_BASIS}) e declara
 * explicitamente o que não é apurado ({@link #NOT_COMPUTED}).
 *
 * <p>Todo valor sai do livro (estado após cada entrada, resultado realizado de cada venda) e é arredondado uma só vez a partir do valor
 * exato. Sem câmbio registrado, o valor em reais fica nulo ("não informado") e é contado à parte.
 */
public final class TaxReport {
    /** Limite mensal de vendas de ações no mercado à vista para a isenção do ganho líquido (Lei 11.033/2004, art. 3º, I). */
    public static final BigDecimal STOCK_MONTHLY_SALES_LIMIT_BRL = new BigDecimal("20000.00");
    public static final String DISCLAIMER = "Apoio educacional ao imposto de renda, a partir do seu livro de movimentações. Não calcula imposto devido, não gera DARF, não "
            + "preenche a declaração e não substitui um contador. Confira a legislação vigente antes de usar qualquer número.";

    public record Holding(String assetKey, String label, AssetType type, String group, Currency currency, BigDecimal quantity, BigDecimal averagePrice,
                          BigDecimal averageCostBrl, BigDecimal totalCostBrl) {}

    /**
     * Resumo de um mês e uma classe. {@code withinStockExemptionLimit} só existe para ações da B3 e é nulo quando alguma venda do mês
     * não tem valor em reais. Vendas com {@code possibleDayTrade} (compra do mesmo ativo no mesmo dia) têm outro tratamento e ficam
     * contadas, não apuradas.
     */
    public record MonthSummary(int month, String group, int sales, BigDecimal salesGrossBrl, BigDecimal realizedResultBrl, int salesWithoutBrlValue,
                               int possibleDayTrades, Boolean withinStockExemptionLimit) {}

    public record LegalNote(String topic, String citation, String status) {}

    public record Result(int year, List<Holding> holdings, List<MonthSummary> months, BigDecimal stockMonthlySalesLimitBrl, List<LegalNote> legalBasis,
                         List<String> notComputed, String disclaimer) {}

    public static final List<LegalNote> LEGAL_BASIS = List.of(
            new LegalNote("Isenção do ganho líquido na venda de ações no mercado à vista, quando o total das vendas no mês é de até R$ 20.000,00",
                    "Lei nº 11.033/2004, art. 3º, inciso I",
                    "Texto confirmado no Planalto em 2026-11-21. A página anota a Medida Provisória nº 1.303/2025 com vigência encerrada: confira o texto compilado vigente. "
                            + "Usado só como indicador por mês; a plataforma não aplica a isenção a nenhum imposto."),
            new LegalNote("Alíquotas de referência sobre o ganho líquido em bolsa: 20% em day trade e 15% nas demais operações",
                    "Lei nº 11.033/2004, art. 2º, incisos I e II",
                    "Texto confirmado no Planalto em 2026-11-21 (mesma anotação sobre a MP 1.303/2025). Citadas como referência: o imposto devido não é calculado aqui."),
            new LegalNote("Rendimentos distribuídos por fundos imobiliários (FII) a pessoa física: isentos na fonte e na declaração quando cumpridas as condições do § 1º",
                    "Lei nº 11.033/2004, art. 3º, inciso III e § 1º",
                    "Texto confirmado no Planalto em 2026-11-21. A plataforma não registra rendimentos distribuídos, então este ponto é só informativo."));

    public static final List<String> NOT_COMPUTED = List.of(
            "Imposto devido, DARF e IRRF: não são calculados.",
            "Compensação de prejuízos: a base legal não foi confirmada em fonte oficial, então nenhum prejuízo é compensado.",
            "Day trade: vendas de um ativo comprado no mesmo dia são só sinalizadas (a alíquota de referência é diferente), não apuradas.",
            "Ganho de capital em cotas de FII, em ETF, em criptoativos e em ativos no exterior: a regra aplicável não foi confirmada em fonte oficial.",
            "Rendimento de renda fixa (Tesouro Direto, LCI, LCA, CRI, CRA) e de fundos: não há dado de rendimento no livro.",
            "Dividendos, juros sobre capital próprio e rendimentos de FII: a plataforma não os registra.");

    private TaxReport() {}

    /** Classe de apuração do ativo (ações da B3, ETF, FII, cripto, ativos em dólar ou outros). */
    public static String groupOf(AssetType type, Currency currency) {
        if (type == AssetType.CRIPTO_ATIVO) return "CRIPTO";
        if (currency == Currency.USD) return "EXTERIOR";
        return switch (type) {
            case ACAO -> "ACOES";
            case FII -> "FII";
            case ETF -> "ETF";
            default -> "OUTROS";
        };
    }

    /** @param entries todas as entradas do usuário, em ordem de gravação */
    public static Result build(List<LedgerEntry> entries, int year) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        Map<String, LedgerEntry> lastUpToYearEnd = new LinkedHashMap<>();
        for (LedgerEntry e : entries) if (!e.tradeDate().isAfter(yearEnd)) lastUpToYearEnd.put(e.asset().key(), e);
        List<Holding> holdings = new ArrayList<>();
        for (LedgerEntry e : lastUpToYearEnd.values()) {
            if (e.quantityAfter().signum() <= 0) continue;
            BigDecimal avgBrl = e.averageCostBrlAfter();
            holdings.add(new Holding(e.asset().key(), e.asset().label(), e.asset().type(), groupOf(e.asset().type(), e.currency()), e.currency(), e.quantityAfter(),
                    e.averagePriceAfter(), avgBrl, avgBrl == null ? null : e.quantityAfter().multiply(avgBrl).setScale(2, RoundingMode.HALF_EVEN)));
        }
        holdings.sort(Comparator.comparing(Holding::group).thenComparing(Holding::label));

        record Key(int month, String group) {}
        Set<String> buyDays = new HashSet<>();
        for (LedgerEntry e : entries) if (e.type() == EntryType.BUY) buyDays.add(e.asset().key() + "|" + e.tradeDate());
        Map<Key, List<LedgerEntry>> sales = new TreeMap<>(Comparator.comparingInt(Key::month).thenComparing(Key::group));
        for (LedgerEntry e : entries) {
            if (e.type() == EntryType.SELL && e.tradeDate().getYear() == year) {
                sales.computeIfAbsent(new Key(e.tradeDate().getMonthValue(), groupOf(e.asset().type(), e.currency())), k -> new ArrayList<>()).add(e);
            }
        }
        List<MonthSummary> months = new ArrayList<>();
        for (var kv : sales.entrySet()) {
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal result = BigDecimal.ZERO;
            int withoutBrl = 0;
            int dayTrades = 0;
            boolean grossKnown = true;
            for (LedgerEntry s : kv.getValue()) {
                BigDecimal g = s.grossBrl();
                if (g == null) grossKnown = false;
                else gross = gross.add(g);
                if (s.realizedPnlBrl() == null) withoutBrl++;
                else result = result.add(s.realizedPnlBrl());
                if (buyDays.contains(s.asset().key() + "|" + s.tradeDate())) dayTrades++;
            }
            BigDecimal grossRounded = gross.setScale(2, RoundingMode.HALF_EVEN);
            Boolean within = "ACOES".equals(kv.getKey().group()) && grossKnown ? grossRounded.compareTo(STOCK_MONTHLY_SALES_LIMIT_BRL) <= 0 : null;
            months.add(new MonthSummary(kv.getKey().month(), kv.getKey().group(), kv.getValue().size(), grossKnown ? grossRounded : null, result.setScale(2, RoundingMode.HALF_EVEN),
                    withoutBrl, dayTrades, within));
        }
        return new Result(year, List.copyOf(holdings), List.copyOf(months), STOCK_MONTHLY_SALES_LIMIT_BRL, LEGAL_BASIS, NOT_COMPUTED, DISCLAIMER);
    }
}
