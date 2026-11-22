package dev.meridianwealth.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.AssetType;
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

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static Position petr(String qty, String price) {
        return Position.open(USER, new AssetRef("petr4", AssetType.ACAO, "PETR4 · Petrobras PN"), d(qty), d(price), Currency.BRL, T0);
    }

    @Test void aPurchaseComputesTheWeightedAveragePriceByHand() {
        // 100 a 10,00 + 50 a 13,00: custo 1.000 + 650 = 1.650 sobre 150 cotas = 11,00
        Position p = petr("100", "10.00").purchase(d("50"), d("13.00"), T1);
        assertThat(p.quantity()).isEqualByComparingTo("150");
        assertThat(p.averagePrice()).isEqualByComparingTo("11.000000");
        assertThat(p.updatedAt()).isEqualTo(T1);
        assertThat(p.createdAt()).isEqualTo(T0);
    }

    @Test void theAverageIsRoundedToSixPlacesHalfEven() {
        // 100 a 10 + 30 a 12,5: (1.000 + 375) / 130 = 10,576923076...
        assertThat(petr("100", "10").purchase(d("30"), d("12.5"), T1).averagePrice()).isEqualByComparingTo("10.576923");
        // 1 a 1 + 2 a 1,000001: (1 + 2,000002) / 3 = 1,000000666... -> 1,000001
        assertThat(petr("1", "1").purchase(d("2"), d("1.000001"), T1).averagePrice()).isEqualByComparingTo("1.000001");
        // empate exato em 6 casas vai para o par: (0,000001 + 0,000002) / 2 = 0,0000015 -> 0,000002
        assertThat(petr("1", "0.000001").purchase(d("1"), d("0.000002"), T1).averagePrice()).isEqualByComparingTo("0.000002");
        assertThat(petr("1", "0.000003").purchase(d("1"), d("0.000004"), T1).averagePrice()).as("0,0000035 -> par: 0,000004").isEqualByComparingTo("0.000004");
    }

    @Test void successivePurchasesAccumulateWithoutDrift() {
        Position p = petr("10", "100");
        for (int i = 0; i < 5; i++) p = p.purchase(d("10"), d("110"), T1);
        // 10 a 100 + 50 a 110 = 1.000 + 5.500 = 6.500 / 60 = 108,333333
        assertThat(p.quantity()).isEqualByComparingTo("60");
        assertThat(p.averagePrice()).isEqualByComparingTo("108.333333");
    }

    @Test void aSaleReducesTheQuantityAndKeepsTheAveragePrice() {
        Position p = petr("100", "10.50").sale(d("40"), T1).orElseThrow();
        assertThat(p.quantity()).isEqualByComparingTo("60");
        assertThat(p.averagePrice()).isEqualByComparingTo("10.50");
        assertThat(p.costBasis()).isEqualByComparingTo("630.00");
    }

    @Test void sellingEverythingClosesThePositionAndSellingMoreIsRefused() {
        assertThat(petr("100", "10").sale(d("100"), T1)).isEmpty();
        assertThatThrownBy(() -> petr("100", "10").sale(d("100.00000001"), T1)).hasMessageContaining("passa da quantidade");
        assertThatThrownBy(() -> petr("100", "10").sale(d("0"), T1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> petr("100", "10").sale(d("-1"), T1)).isInstanceOf(IllegalArgumentException.class);
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
        assertThatThrownBy(() -> petr("10", "10").purchase(d("1E999999999"), d("10"), T1)).hasMessageContaining("fora do intervalo");
        assertThatThrownBy(() -> petr("999999999999999", "10").purchase(d("999999999999999"), d("10"), T1)).as("a soma estoura o limite").hasMessageContaining("quantidade total");
    }

    @Test void declareReplacesQuantityAndAveragePriceAndKeepsTheIdentity() {
        Position p = petr("100", "10");
        Position q = p.declare(d("25.5"), d("42.123456"), T1);
        assertThat(q.id()).isEqualTo(p.id());
        assertThat(q.quantity()).isEqualByComparingTo("25.5");
        assertThat(q.averagePrice()).isEqualByComparingTo("42.123456");
        assertThat(q.withVersion(7).version()).isEqualTo(7);
        assertThat(p.version()).isZero();
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
    }

    @Test void reaisAreRoundedOnceFromTheExactAmountNotTwice() {
        // 1 a US$ 1.234,565: o custo exato é 1.234,565 (HALF_EVEN em 2 casas: 1.234,56). Câmbio 5,0: o valor em reais exato é 6.172,825 -> 6.172,82.
        // Arredondar o custo em dólar e depois converter daria 1.234,56 x 5 = 6.172,80: dois centavos de erro.
        Position p = Position.open(USER, new AssetRef(null, AssetType.CRIPTO_ATIVO, "Bitcoin"), d("1"), d("1234.565"), Currency.USD, T0);
        FxRate fx = new FxRate(d("5.0"), LocalDate.parse("2026-11-20"), "PTAX (venda)", false);
        PositionValuation.Result v = PositionValuation.value(p, null, fx);
        assertThat(v.costBasis()).as("na moeda da posição: 2 casas").isEqualByComparingTo("1234.56");
        assertThat(v.costBasisBrl()).isEqualByComparingTo("6172.82");
        assertThat(v.currentValueBrl()).isEqualByComparingTo("6172.82");
        MarketPrice price = new MarketPrice(d("1234.565"), Currency.USD, "yahoo", T1, "fechamento", false);
        assertThat(PositionValuation.value(p, price, fx).currentValueBrl()).as("com cotação").isEqualByComparingTo("6172.82");
    }

    @Test void reaisOfABrlPositionAlwaysComeWithTwoDecimals() {
        Position p = Position.open(USER, new AssetRef("PETR4", AssetType.ACAO, "PETR4"), d("3"), d("0.333333"), Currency.BRL, T0);
        PositionValuation.Result v = PositionValuation.value(p, new MarketPrice(d("0.3333335"), Currency.BRL, "brapi", T1, "fechamento", false), null);
        assertThat(v.costBasisBrl()).isEqualTo(d("1.00"));
        assertThat(v.currentValueBrl()).as("3 x 0,3333335 = 1,0000005 -> 1,00").isEqualTo(d("1.00"));
        assertThat(v.currentValueBrl().scale()).isEqualTo(2);
    }

    @Test void withoutAQuoteThePositionIsValuedAtCostAndNothingIsInvented() {
        PositionValuation.Result v = PositionValuation.value(petr("100", "10.50"), null, null);
        assertThat(v.basis()).isEqualTo(Basis.COST);
        assertThat(v.marketValue()).isNull();
        assertThat(v.unrealizedPnl()).isNull();
        assertThat(v.unrealizedPnlPercent()).isNull();
        assertThat(v.currentValueBrl()).isEqualByComparingTo("1050.00");
    }

    @Test void aQuoteInAnotherCurrencyIsIgnoredInsteadOfBeingMixed() {
        MarketPrice usd = new MarketPrice(d("12.00"), Currency.USD, "yahoo", T1, "fechamento", false);
        PositionValuation.Result v = PositionValuation.value(petr("100", "10.50"), usd, null);
        assertThat(v.basis()).isEqualTo(Basis.COST);
        assertThat(v.price()).isNull();
    }

    @Test void dollarPositionsConvertToReaisWithThePtaxAndStayEmptyWithoutIt() {
        Position spy = Position.open(USER, new AssetRef("SPY", AssetType.ETF, "SPY"), d("10"), d("500"), Currency.USD, T0);
        MarketPrice price = new MarketPrice(d("520.25"), Currency.USD, "yahoo", T1, "fechamento", false);
        FxRate fx = new FxRate(d("5.4321"), LocalDate.parse("2026-11-20"), "PTAX (venda)", false);
        PositionValuation.Result v = PositionValuation.value(spy, price, fx);
        assertThat(v.marketValue()).isEqualByComparingTo("5202.50");
        assertThat(v.unrealizedPnl()).as("resultado na moeda da posição").isEqualByComparingTo("202.50");
        assertThat(v.costBasisBrl()).as("5.000 x 5,4321").isEqualByComparingTo("27160.50");
        assertThat(v.currentValueBrl()).as("5.202,50 x 5,4321 = 28.260,50025").isEqualByComparingTo("28260.50");
        PositionValuation.Result noFx = PositionValuation.value(spy, price, null);
        assertThat(noFx.currentValueBrl()).isNull();
        assertThat(noFx.costBasisBrl()).isNull();
        assertThat(noFx.marketValue()).as("o valor em dólar continua informado").isEqualByComparingTo("5202.50");
        assertThatThrownBy(() -> new FxRate(BigDecimal.ZERO, LocalDate.now(), "x", false)).isInstanceOf(IllegalArgumentException.class);
    }
}
