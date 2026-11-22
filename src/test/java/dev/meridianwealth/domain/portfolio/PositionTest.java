package dev.meridianwealth.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.Position.SaleOutcome;
import dev.meridianwealth.domain.portfolio.PositionValuation.Basis;
import dev.meridianwealth.domain.portfolio.PositionValuation.FxRate;
import dev.meridianwealth.domain.portfolio.PositionValuation.MarketPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionTest {
    private static final Instant T0 = Instant.parse("2026-11-21T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-21T13:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final BigDecimal NO_FEES = BigDecimal.ZERO;

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static Position petr(String qty, String price) {
        return Position.open(USER, new AssetRef("petr4", AssetType.ACAO, "PETR4 · Petrobras PN"), d(qty), d(price), Currency.BRL, null, T0);
    }

    private static Position usd(String qty, String price, String fx) {
        return Position.open(USER, new AssetRef("SPY", AssetType.ETF, "SPY"), d(qty), d(price), Currency.USD, fx == null ? null : d(fx), T0);
    }

    private static Position buy(Position p, String qty, String price) { return p.purchase(d(qty), d(price), NO_FEES, null, T1); }

    private static SaleOutcome sell(Position p, String qty, String price) { return p.sale(d(qty), d(price), NO_FEES, null, T1); }

    @Test void aPurchaseComputesTheWeightedAveragePriceByHand() {
        // 100 a 10,00 + 50 a 13,00: custo 1.000 + 650 = 1.650 sobre 150 cotas = 11,00
        Position p = buy(petr("100", "10.00"), "50", "13.00");
        assertThat(p.quantity()).isEqualByComparingTo("150");
        assertThat(p.averagePrice()).isEqualByComparingTo("11.000000");
        assertThat(p.averageCostBrl()).as("em BRL, o custo em reais é o próprio preço médio").isEqualByComparingTo("11.000000");
        assertThat(p.updatedAt()).isEqualTo(T1);
        assertThat(p.createdAt()).isEqualTo(T0);
    }

    @Test void theAverageIsRoundedToSixPlacesHalfEven() {
        assertThat(buy(petr("100", "10"), "30", "12.5").averagePrice()).as("(1.000 + 375) / 130").isEqualByComparingTo("10.576923");
        assertThat(buy(petr("1", "1"), "2", "1.000001").averagePrice()).isEqualByComparingTo("1.000001");
        assertThat(buy(petr("1", "0.000001"), "1", "0.000002").averagePrice()).as("0,0000015 -> par: 0,000002").isEqualByComparingTo("0.000002");
        assertThat(buy(petr("1", "0.000003"), "1", "0.000004").averagePrice()).as("0,0000035 -> par: 0,000004").isEqualByComparingTo("0.000004");
    }

    @Test void feesEnterTheCostOfAPurchase() {
        // 100 a 10 + 100 a 12 com R$ 20 de corretagem: (1.000 + 1.200 + 20) / 200 = 11,10
        Position p = petr("100", "10").purchase(d("100"), d("12"), d("20"), null, T1);
        assertThat(p.averagePrice()).isEqualByComparingTo("11.100000");
        assertThat(p.averageCostBrl()).isEqualByComparingTo("11.100000");
        assertThatThrownBy(() -> petr("1", "1").purchase(d("1"), d("1"), d("-1"), null, T1)).hasMessageContaining("negativo");
        assertThatThrownBy(() -> petr("1", "1").purchase(d("1"), d("1"), d("0.0000001"), null, T1)).hasMessageContaining("6 casas");
    }

    @Test void successivePurchasesAccumulateWithoutDrift() {
        Position p = petr("10", "100");
        for (int i = 0; i < 5; i++) p = buy(p, "10", "110");
        assertThat(p.quantity()).isEqualByComparingTo("60");
        assertThat(p.averagePrice()).as("(1.000 + 5.500) / 60").isEqualByComparingTo("108.333333");
    }

    @Test void aSaleKeepsTheAveragePriceAndRealizesTheNetResult() {
        // 100 a 10,50; vende 40 a 12,00 com R$ 5 de taxas: líquido 480 - 5 = 475; custo 40 x 10,50 = 420; resultado 55
        SaleOutcome out = petr("100", "10.50").sale(d("40"), d("12"), d("5"), null, T1);
        Position rest = out.remaining().orElseThrow();
        assertThat(rest.quantity()).isEqualByComparingTo("60");
        assertThat(rest.averagePrice()).as("a venda mantém o preço médio").isEqualByComparingTo("10.50");
        assertThat(rest.costBasis()).isEqualByComparingTo("630.00");
        assertThat(out.proceedsNet()).isEqualByComparingTo("475.00");
        assertThat(out.costOfSold()).isEqualByComparingTo("420.00");
        assertThat(out.realized()).isEqualByComparingTo("55.00");
        assertThat(out.realizedBrl()).as("em BRL o resultado em reais é o mesmo").isEqualByComparingTo("55.00");
    }

    @Test void aLossIsANegativeRealizedResult() {
        SaleOutcome out = sell(petr("10", "20"), "10", "15");
        assertThat(out.realized()).isEqualByComparingTo("-50.00");
        assertThat(out.remaining()).isEmpty();
    }

    @Test void realizedResultIsRoundedOnceFromTheExactAmount() {
        // custo 3 x 0,333333 = 0,999999; venda 3 x 0,333334 = 1,000002; resultado exato 0,000003 -> 0,00
        SaleOutcome out = petr("3", "0.333333").sale(d("3"), d("0.333334"), NO_FEES, null, T1);
        assertThat(out.proceedsNet()).isEqualByComparingTo("1.00");
        assertThat(out.costOfSold()).isEqualByComparingTo("1.00");
        assertThat(out.realized()).isEqualByComparingTo("0.00");
        // 1 a 1.234,565 vendida a 1.234,575: resultado exato 0,010 -> 0,01 (arredondar os dois lados antes daria 1.234,58 - 1.234,56 = 0,02)
        Position p = Position.open(USER, new AssetRef("X", AssetType.ACAO, "X"), d("1"), d("1234.565"), Currency.BRL, null, T0);
        assertThat(p.sale(d("1"), d("1234.575"), NO_FEES, null, T1).realized()).isEqualByComparingTo("0.01");
    }

    @Test void sellingEverythingClosesThePositionAndSellingMoreIsRefused() {
        assertThat(sell(petr("100", "10"), "100", "11").remaining()).isEmpty();
        assertThatThrownBy(() -> sell(petr("100", "10"), "100.00000001", "11")).hasMessageContaining("passa da quantidade");
        assertThatThrownBy(() -> sell(petr("100", "10"), "0", "11")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sell(petr("100", "10"), "-1", "11")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sell(petr("100", "10"), "1", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> petr("100", "10").sale(d("1"), d("10"), d("11"), null, T1)).hasMessageContaining("taxas são maiores");
    }

    @Test void quantitiesAcceptEightDecimalsAndPricesSixAndNothingBeyond() {
        assertThat(petr("0.00000001", "50000").costBasis()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> petr("0.000000001", "10")).hasMessageContaining("8 casas");
        assertThatThrownBy(() -> petr("1", "10.0000001")).hasMessageContaining("6 casas");
        assertThatThrownBy(() -> petr("1", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> petr("-1", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void scientificNotationIsRefusedBeforeAnyArithmetic() {
        assertThatThrownBy(() -> petr("1E999999999", "10")).hasMessageContaining("fora do intervalo");
        assertThatThrownBy(() -> petr("1", "1E999999999")).hasMessageContaining("fora do intervalo");
        assertThatThrownBy(() -> buy(petr("10", "10"), "1E999999999", "10")).hasMessageContaining("fora do intervalo");
        assertThatThrownBy(() -> petr("10", "10").purchase(d("1"), d("1"), d("1E999999999"), null, T1)).hasMessageContaining("fora do intervalo");
        assertThatThrownBy(() -> buy(petr("999999999999999", "10"), "999999999999999", "10")).as("a soma estoura o limite").hasMessageContaining("quantidade total");
    }

    @Test void declareReplacesQuantityAndAveragePriceAndKeepsTheIdentity() {
        Position p = petr("100", "10");
        Position q = p.declare(d("25.5"), d("42.123456"), null, T1);
        assertThat(q.id()).isEqualTo(p.id());
        assertThat(q.quantity()).isEqualByComparingTo("25.5");
        assertThat(q.averagePrice()).isEqualByComparingTo("42.123456");
        assertThat(q.averageCostBrl()).isEqualByComparingTo("42.123456");
        assertThat(q.withVersion(7).version()).isEqualTo(7);
        assertThat(p.version()).isZero();
    }

    @Test void dollarPositionsKeepTheBrlCostAtTheExchangeRateOfEachPurchase() {
        // abre 10 a US$ 500 com câmbio 5,00: custo em reais por unidade = 500 x 5,00 = 2.500
        Position p = usd("10", "500", "5.00");
        assertThat(p.averageCostBrl()).isEqualByComparingTo("2500.000000");
        // compra 10 a US$ 600 com câmbio 6,00 (R$ 3.600 por unidade): (10 x 2.500 + 10 x 3.600) / 20 = 3.050
        Position q = p.purchase(d("10"), d("600"), NO_FEES, d("6.00"), T1);
        assertThat(q.averagePrice()).as("em dólar: (5.000 + 6.000) / 20").isEqualByComparingTo("550.000000");
        assertThat(q.averageCostBrl()).isEqualByComparingTo("3050.000000");
        assertThat(q.exactCostBrl()).isEqualByComparingTo("61000");
        // as taxas em dólar também entram no custo em reais, ao câmbio da compra
        Position withFee = p.purchase(d("10"), d("600"), d("10"), d("6.00"), T1);
        assertThat(withFee.averageCostBrl()).as("(25.000 + (6.000 + 10) x 6,00) / 20").isEqualByComparingTo("3053.000000");
    }

    @Test void aDollarSaleRealizesTheResultInDollarsAndInReaisUsingTheBrlCost() {
        Position p = usd("10", "500", "5.00");
        // vende 4 a US$ 600 com câmbio 6,00 e US$ 8 de taxas: líquido US$ 2.392; custo US$ 2.000; resultado US$ 392
        SaleOutcome out = p.sale(d("4"), d("600"), d("8"), d("6.00"), T1);
        assertThat(out.realized()).isEqualByComparingTo("392.00");
        // em reais: líquido 2.392 x 6,00 = 14.352; custo 4 x 2.500 = 10.000; resultado 4.352 (inclui a variação cambial)
        assertThat(out.proceedsNetBrl()).isEqualByComparingTo("14352.00");
        assertThat(out.costOfSoldBrl()).isEqualByComparingTo("10000.00");
        assertThat(out.realizedBrl()).isEqualByComparingTo("4352.00");
        assertThat(out.remaining().orElseThrow().averageCostBrl()).as("o custo em reais da sobra não muda").isEqualByComparingTo("2500.000000");
    }

    @Test void withoutAnExchangeRateTheBrlFiguresStayUnknownInsteadOfBeingInvented() {
        Position legacy = usd("10", "500", null);
        assertThat(legacy.averageCostBrl()).isNull();
        assertThat(legacy.exactCostBrl()).isNull();
        Position q = legacy.purchase(d("10"), d("600"), NO_FEES, d("6.00"), T1);
        assertThat(q.averagePrice()).isEqualByComparingTo("550.000000");
        assertThat(q.averageCostBrl()).as("a parte antiga não tem câmbio: a média em reais não existe").isNull();
        SaleOutcome out = legacy.sale(d("1"), d("600"), NO_FEES, d("6.00"), T1);
        assertThat(out.realized()).isEqualByComparingTo("100.00");
        assertThat(out.realizedBrl()).isNull();
        assertThat(out.proceedsNetBrl()).isNull();
        Position fixed = legacy.declare(d("10"), d("500"), d("5.50"), T1);
        assertThat(fixed.averageCostBrl()).as("um ajuste com câmbio reabilita o custo em reais").isEqualByComparingTo("2750.000000");
    }

    @Test void assetKeysDistinguishSymbolsFromTypedPositionsAndNormalizeLabels() {
        assertThat(new AssetRef(" petr4 ", AssetType.ACAO, "x").key()).isEqualTo("PETR4");
        assertThat(new AssetRef(null, AssetType.LCI, "LCI Banco Ação 2028!").key()).isEqualTo("T:LCI:lci-banco-acao-2028");
        assertThat(new AssetRef(null, AssetType.LCI, "  lci   BANCO acao 2028 ").key()).as("rótulos equivalentes são a mesma posição").isEqualTo("T:LCI:lci-banco-acao-2028");
        assertThat(new AssetRef(null, AssetType.LCI, "LCI Outro Banco").key()).isNotEqualTo(new AssetRef(null, AssetType.LCI, "LCI Banco").key());
        assertThat(new AssetRef(null, AssetType.TESOURO_SELIC, null).label()).isEqualTo("Tesouro Selic");
        assertThat(new AssetRef(null, AssetType.CRI, "!!!").key()).isEqualTo("T:CRI:sem-nome");
        assertThat(new AssetRef(null, AssetType.CRI, "x".repeat(120)).key().length()).isLessThanOrEqualTo(120);
        assertThatThrownBy(() -> new AssetRef("IBOV", AssetType.INDICE, null)).hasMessageContaining("só referência");
        assertThatThrownBy(() -> new AssetRef(null, AssetType.LCI, "x".repeat(121))).hasMessageContaining("120");
        assertThatThrownBy(() -> new AssetRef(null, AssetType.LCI, "a\u0000b")).hasMessageContaining("inválidos");
    }

    @Test void onlyBrlAndUsdAreSupported() {
        assertThat(Currency.fromCode("BRL")).contains(Currency.BRL);
        assertThat(Currency.fromCode("USD")).contains(Currency.USD);
        for (String other : new String[] {"EUR", "JPY", "GBp", "ZAc", "", null}) assertThat(Currency.fromCode(other)).as(String.valueOf(other)).isEmpty();
    }

    @Test void valuationUsesMarketPriceInTheSameCurrencyAndComputesPnlByHand() {
        Position p = petr("100", "10.50");
        MarketPrice price = new MarketPrice(d("12.00"), Currency.BRL, "brapi", T1, "fechamento", false);
        PositionValuation.Result v = PositionValuation.value(p, price, null);
        assertThat(v.basis()).isEqualTo(Basis.MARKET);
        assertThat(v.costBasis()).isEqualByComparingTo("1050.00");
        assertThat(v.marketValue()).isEqualByComparingTo("1200.00");
        assertThat(v.unrealizedPnl()).isEqualByComparingTo("150.00");
        assertThat(v.unrealizedPnlPercent()).isEqualByComparingTo("14.29");
        assertThat(v.currentValueBrl()).isEqualByComparingTo("1200.00");
        assertThat(v.costBasisBrl()).isEqualByComparingTo("1050.00");
        assertThat(v.unrealizedPnlBrl()).isEqualByComparingTo("150.00");
    }

    @Test void withoutAQuoteThePositionIsValuedAtCostAndNothingIsInvented() {
        PositionValuation.Result v = PositionValuation.value(petr("100", "10.50"), null, null);
        assertThat(v.basis()).isEqualTo(Basis.COST);
        assertThat(v.marketValue()).isNull();
        assertThat(v.unrealizedPnl()).isNull();
        assertThat(v.unrealizedPnlPercent()).isNull();
        assertThat(v.unrealizedPnlBrl()).isNull();
        assertThat(v.currentValueBrl()).isEqualByComparingTo("1050.00");
    }

    @Test void aQuoteInAnotherCurrencyIsIgnoredInsteadOfBeingMixed() {
        MarketPrice other = new MarketPrice(d("12.00"), Currency.USD, "yahoo", T1, "fechamento", false);
        PositionValuation.Result v = PositionValuation.value(petr("100", "10.50"), other, null);
        assertThat(v.basis()).isEqualTo(Basis.COST);
        assertThat(v.price()).isNull();
    }

    @Test void dollarPositionsUseTheBrlCostOfEachPurchaseAndTodaysPtaxForTheCurrentValue() {
        // 10 a US$ 500 comprados com câmbio 5,00 (custo em reais 25.000); hoje o ativo vale US$ 520,25 e o PTAX é 5,4321
        Position spy = usd("10", "500", "5.00");
        MarketPrice price = new MarketPrice(d("520.25"), Currency.USD, "yahoo", T1, "fechamento", false);
        FxRate fx = new FxRate(d("5.4321"), LocalDate.parse("2026-11-20"), "PTAX (venda)", false);
        PositionValuation.Result v = PositionValuation.value(spy, price, fx);
        assertThat(v.marketValue()).isEqualByComparingTo("5202.50");
        assertThat(v.unrealizedPnl()).as("resultado na moeda da posição: só variação de preço").isEqualByComparingTo("202.50");
        assertThat(v.costBasisBrl()).as("5.000 x 5,00: câmbio da compra, não o de hoje").isEqualByComparingTo("25000.00");
        assertThat(v.currentValueBrl()).as("5.202,50 x 5,4321 = 28.260,50025").isEqualByComparingTo("28260.50");
        assertThat(v.unrealizedPnlBrl()).as("28.260,50025 - 25.000: inclui a variação cambial").isEqualByComparingTo("3260.50");
        assertThat(v.unrealizedPnlPercentBrl()).isEqualByComparingTo("13.04");
        PositionValuation.Result noFx = PositionValuation.value(spy, price, null);
        assertThat(noFx.currentValueBrl()).isNull();
        assertThat(noFx.unrealizedPnlBrl()).isNull();
        assertThat(noFx.costBasisBrl()).as("o custo em reais não depende do PTAX de hoje").isEqualByComparingTo("25000.00");
        assertThat(noFx.marketValue()).as("o valor em dólar continua informado").isEqualByComparingTo("5202.50");
        assertThatThrownBy(() -> new FxRate(BigDecimal.ZERO, LocalDate.now(), "x", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aLegacyDollarPositionHasNoBrlCostOrBrlResult() {
        Position legacy = usd("10", "500", null);
        MarketPrice price = new MarketPrice(d("520.25"), Currency.USD, "yahoo", T1, "fechamento", false);
        FxRate fx = new FxRate(d("5.4321"), LocalDate.parse("2026-11-20"), "PTAX (venda)", false);
        PositionValuation.Result v = PositionValuation.value(legacy, price, fx);
        assertThat(v.costBasisBrl()).isNull();
        assertThat(v.unrealizedPnlBrl()).isNull();
        assertThat(v.currentValueBrl()).as("o valor de hoje em reais continua informado").isEqualByComparingTo("28260.50");
    }

    @Test void reaisAreRoundedOnceFromTheExactAmountNotTwice() {
        // 1 a US$ 1.234,565 com câmbio 5,0: custo exato em reais 6.172,825 -> 6.172,82 (HALF_EVEN, uma só vez); arredondar o custo em dólar antes daria 6.172,80
        Position p = Position.open(USER, new AssetRef(null, AssetType.CRIPTO_ATIVO, "Bitcoin"), d("1"), d("1234.565"), Currency.USD, d("5.0"), T0);
        FxRate fx = new FxRate(d("5.0"), LocalDate.parse("2026-11-20"), "PTAX (venda)", false);
        PositionValuation.Result v = PositionValuation.value(p, null, fx);
        assertThat(v.costBasis()).as("na moeda da posição: 2 casas").isEqualByComparingTo("1234.56");
        assertThat(v.costBasisBrl()).isEqualByComparingTo("6172.82");
        assertThat(v.currentValueBrl()).isEqualByComparingTo("6172.82");
        MarketPrice price = new MarketPrice(d("1234.565"), Currency.USD, "yahoo", T1, "fechamento", false);
        assertThat(PositionValuation.value(p, price, fx).currentValueBrl()).as("com cotação").isEqualByComparingTo("6172.82");
    }

    @Test void reaisOfABrlPositionAlwaysComeWithTwoDecimals() {
        Position p = Position.open(USER, new AssetRef("PETR4", AssetType.ACAO, "PETR4"), d("3"), d("0.333333"), Currency.BRL, null, T0);
        PositionValuation.Result v = PositionValuation.value(p, new MarketPrice(d("0.3333335"), Currency.BRL, "brapi", T1, "fechamento", false), null);
        assertThat(v.costBasisBrl()).isEqualTo(d("1.00"));
        assertThat(v.currentValueBrl()).as("3 x 0,3333335 = 1,0000005 -> 1,00").isEqualTo(d("1.00"));
        assertThat(v.currentValueBrl().scale()).isEqualTo(2);
    }
}
