package dev.meridianwealth.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.TaxReport.MonthSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaxReportTest {
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static LedgerEntry entry(String symbol, AssetType type, Currency currency, EntryType entryType, String date, String qty, String price, String qtyAfter, String avgAfter,
                                     String avgBrlAfter, String realizedBrl, String fx) {
        BigDecimal realized = entryType == EntryType.SELL ? d("1") : null;
        return new LedgerEntry(UUID.randomUUID(), USER, UUID.randomUUID(), new AssetRef(symbol, type, symbol), entryType, LocalDate.parse(date), d(qty), price == null ? null : d(price),
                BigDecimal.ZERO, currency, fx == null ? null : d(fx), null, null, d(qtyAfter), avgAfter == null ? null : d(avgAfter), avgBrlAfter == null ? null : d(avgBrlAfter), realized,
                realizedBrl == null ? null : d(realizedBrl), 0, null, null, NOW);
    }

    private static MonthSummary month(TaxReport.Result r, int month, String group) {
        return r.months().stream().filter(m -> m.month() == month && m.group().equals(group)).findFirst().orElseThrow();
    }

    @Test void groupsFollowTheAssetTypeAndTheCurrency() {
        assertThat(TaxReport.groupOf(AssetType.ACAO, Currency.BRL)).isEqualTo("ACOES");
        assertThat(TaxReport.groupOf(AssetType.ACAO, Currency.USD)).as("ação em dólar é exterior: o limite de R$ 20.000 não se aplica").isEqualTo("EXTERIOR");
        assertThat(TaxReport.groupOf(AssetType.ETF, Currency.USD)).isEqualTo("EXTERIOR");
        assertThat(TaxReport.groupOf(AssetType.ETF, Currency.BRL)).isEqualTo("ETF");
        assertThat(TaxReport.groupOf(AssetType.FII, Currency.BRL)).isEqualTo("FII");
        assertThat(TaxReport.groupOf(AssetType.CRIPTO_ATIVO, Currency.USD)).isEqualTo("CRIPTO");
        assertThat(TaxReport.groupOf(AssetType.CRIPTO_ATIVO, Currency.BRL)).isEqualTo("CRIPTO");
        assertThat(TaxReport.groupOf(AssetType.LCI, Currency.BRL)).isEqualTo("OUTROS");
        assertThat(TaxReport.groupOf(AssetType.TESOURO_SELIC, Currency.BRL)).isEqualTo("OUTROS");
    }

    @Test void holdingsAreTheLastStateUpToDecemberThirtyFirstAndClosedOnesAreLeftOut() {
        List<LedgerEntry> entries = new ArrayList<>(List.of(
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.OPENING, "2025-03-01", "10", "30", "10", "30", "30", null, null),
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.BUY, "2025-11-01", "10", "40", "20", "35", "35", null, null),
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.SELL, "2026-02-01", "20", "50", "0", null, null, "300", null),
                entry("VALE3", AssetType.ACAO, Currency.BRL, EntryType.OPENING, "2026-06-01", "5", "60", "5", "60", "60", null, null)));
        TaxReport.Result y2025 = TaxReport.build(entries, 2025);
        assertThat(y2025.holdings()).hasSize(1);
        assertThat(y2025.holdings().get(0).assetKey()).isEqualTo("PETR4");
        assertThat(y2025.holdings().get(0).quantity()).isEqualByComparingTo("20");
        assertThat(y2025.holdings().get(0).averageCostBrl()).isEqualByComparingTo("35");
        assertThat(y2025.holdings().get(0).totalCostBrl()).isEqualByComparingTo("700.00");
        assertThat(y2025.months()).as("sem vendas em 2025").isEmpty();
        TaxReport.Result y2026 = TaxReport.build(entries, 2026);
        assertThat(y2026.holdings()).extracting(TaxReport.Holding::assetKey).containsExactly("VALE3");
        assertThat(TaxReport.build(entries, 2024).holdings()).isEmpty();
    }

    @Test void theMonthlyStockLimitIsInclusiveAtTwentyThousandAndDoesNotApplyToOtherGroups() {
        List<LedgerEntry> entries = List.of(
                entry("ITUB4", AssetType.ACAO, Currency.BRL, EntryType.OPENING, "2026-01-02", "1000", "10", "1000", "10", "10", null, null),
                entry("ITUB4", AssetType.ACAO, Currency.BRL, EntryType.SELL, "2026-03-10", "400", "50", "600", "10", "10", "16000", null),
                entry("ITUB4", AssetType.ACAO, Currency.BRL, EntryType.SELL, "2026-04-10", "400", "50.01", "200", "10", "10", "16004", null),
                entry("HGLG11", AssetType.FII, Currency.BRL, EntryType.OPENING, "2026-01-02", "1000", "10", "1000", "10", "10", null, null),
                entry("HGLG11", AssetType.FII, Currency.BRL, EntryType.SELL, "2026-03-11", "900", "30", "100", "10", "10", "18000", null));
        TaxReport.Result r = TaxReport.build(entries, 2026);
        assertThat(month(r, 3, "ACOES").salesGrossBrl()).isEqualByComparingTo("20000.00");
        assertThat(month(r, 3, "ACOES").withinStockExemptionLimit()).as("exatamente R$ 20.000,00 ainda está dentro").isTrue();
        assertThat(month(r, 4, "ACOES").salesGrossBrl()).isEqualByComparingTo("20004.00");
        assertThat(month(r, 4, "ACOES").withinStockExemptionLimit()).isFalse();
        assertThat(month(r, 3, "FII").salesGrossBrl()).isEqualByComparingTo("27000.00");
        assertThat(month(r, 3, "FII").withinStockExemptionLimit()).as("o limite só vale para ações").isNull();
        assertThat(month(r, 3, "ACOES").realizedResultBrl()).isEqualByComparingTo("16000.00");
        assertThat(r.months()).extracting(MonthSummary::month).containsExactly(3, 3, 4);
    }

    @Test void salesWithoutBrlValueAreCountedAndTheStockLimitBecomesUndetermined() {
        List<LedgerEntry> entries = List.of(
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.OPENING, "2026-01-02", "100", "10", "100", "10", "10", null, null),
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.SELL, "2026-03-10", "10", "20", "90", "10", "10", "100", null),
                entry("SPY", AssetType.ETF, Currency.USD, EntryType.OPENING, "2026-01-02", "10", "500", "10", "500", null, null, null),
                entry("SPY", AssetType.ETF, Currency.USD, EntryType.SELL, "2026-03-12", "1", "600", "9", "500", null, null, null));
        TaxReport.Result r = TaxReport.build(entries, 2026);
        MonthSummary spy = month(r, 3, "EXTERIOR");
        assertThat(spy.salesGrossBrl()).as("sem câmbio registrado, o valor em reais não existe").isNull();
        assertThat(spy.salesWithoutBrlValue()).isEqualTo(1);
        assertThat(spy.realizedResultBrl()).isEqualByComparingTo("0.00");
        assertThat(r.holdings().stream().filter(h -> h.assetKey().equals("SPY")).findFirst().orElseThrow().totalCostBrl()).as("posição sem custo em reais").isNull();
        MonthSummary stocks = month(r, 3, "ACOES");
        assertThat(stocks.withinStockExemptionLimit()).isTrue();
        assertThat(stocks.salesGrossBrl()).isEqualByComparingTo("200.00");
    }

    @Test void aDollarSaleUsesTheExchangeRateOfTheOperationForTheGrossValue() {
        List<LedgerEntry> entries = List.of(
                entry("SPY", AssetType.ETF, Currency.USD, EntryType.OPENING, "2026-01-02", "10", "500", "10", "500", "2500", null, "5.00"),
                entry("SPY", AssetType.ETF, Currency.USD, EntryType.SELL, "2026-03-12", "2", "600", "8", "500", "2500", "2200", "6.00"));
        MonthSummary m = month(TaxReport.build(entries, 2026), 3, "EXTERIOR");
        assertThat(m.salesGrossBrl()).as("2 x 600 x 6,00").isEqualByComparingTo("7200.00");
        assertThat(m.realizedResultBrl()).isEqualByComparingTo("2200.00");
        assertThat(m.withinStockExemptionLimit()).isNull();
    }

    @Test void aSaleOnTheSameDayAsAPurchaseOfTheSameAssetIsFlaggedAsPossibleDayTradeAndNotAppliedAnyRule() {
        List<LedgerEntry> entries = List.of(
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.OPENING, "2026-01-02", "100", "10", "100", "10", "10", null, null),
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.BUY, "2026-05-04", "10", "11", "110", "10.09", "10.09", null, null),
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.SELL, "2026-05-04", "10", "12", "100", "10.09", "10.09", "19.1", null),
                entry("PETR4", AssetType.ACAO, Currency.BRL, EntryType.SELL, "2026-05-05", "10", "12", "90", "10.09", "10.09", "19.1", null));
        MonthSummary m = month(TaxReport.build(entries, 2026), 5, "ACOES");
        assertThat(m.sales()).isEqualTo(2);
        assertThat(m.possibleDayTrades()).as("só a venda do mesmo dia da compra").isEqualTo(1);
    }

    @Test void theReportStatesItsLegalBasisAndWhatItDoesNotCompute() {
        TaxReport.Result r = TaxReport.build(List.of(), 2026);
        assertThat(r.holdings()).isEmpty();
        assertThat(r.months()).isEmpty();
        assertThat(r.stockMonthlySalesLimitBrl()).isEqualByComparingTo("20000.00");
        assertThat(r.legalBasis()).extracting(TaxReport.LegalNote::citation).containsExactly("Lei nº 11.033/2004, art. 3º, inciso I", "Lei nº 11.033/2004, art. 2º, incisos I e II",
                "Lei nº 11.033/2004, art. 3º, inciso III e § 1º");
        assertThat(r.legalBasis()).allSatisfy(n -> assertThat(n.status()).contains("2026-11-21").contains("Planalto"));
        assertThat(r.legalBasis().get(0).status()).contains("Medida Provisória nº 1.303/2025").contains("texto compilado");
        assertThat(String.join(" ", r.notComputed())).contains("Imposto devido, DARF e IRRF").contains("Compensação de prejuízos").contains("Day trade").contains("criptoativos")
                .contains("Tesouro Direto");
        assertThat(r.disclaimer()).contains("Não calcula imposto devido").contains("não substitui um contador");
    }
}
