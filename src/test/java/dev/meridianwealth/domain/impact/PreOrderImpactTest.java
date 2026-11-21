package dev.meridianwealth.domain.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.impact.PreOrderImpact.Finding;
import dev.meridianwealth.domain.impact.PreOrderImpact.Level;
import dev.meridianwealth.domain.impact.PreOrderImpact.Order;
import dev.meridianwealth.domain.impact.PreOrderImpact.Position;
import dev.meridianwealth.domain.impact.PreOrderImpact.Result;
import dev.meridianwealth.domain.impact.PreOrderImpact.Side;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreOrderImpactTest {
    private static Position stock(String symbol, String value, String vol) {
        return new Position(symbol, symbol, AssetType.ACAO, new BigDecimal(value), vol == null ? null : new BigDecimal(vol));
    }

    private static Position bond(String value) { return new Position(null, "Tesouro Selic", AssetType.TESOURO_SELIC, new BigDecimal(value), null); }

    private static Order buy(Position p) { return new Order(Side.BUY, p); }

    private static Order sell(Position p) { return new Order(Side.SELL, p); }

    @Test void concentrationMetricsMatchTheHandComputedValues() {
        // 600 e 400: pesos 0,6 e 0,4 -> HHI = (0,36 + 0,16) x 10.000 = 5.200 e posições efetivas = 1 / 0,52 = 1,92
        Result r = PreOrderImpact.analyze(List.of(stock("AAA", "600", null), bond("400")), buy(bond("400")), null);
        assertThat(r.before().totalBrl()).isEqualByComparingTo("1000");
        assertThat(r.before().largestLabel()).isEqualTo("AAA");
        assertThat(r.before().largestPercent()).isEqualByComparingTo("60.00");
        assertThat(r.before().hhi()).isEqualByComparingTo("5200.00");
        assertThat(r.before().effectivePositions()).isEqualByComparingTo("1.92");
        // depois: 600 e 800 de 1.400 -> 57,14%; HHI 5.102,04; efetivas 1,96
        assertThat(r.after().totalBrl()).isEqualByComparingTo("1400");
        assertThat(r.after().largestLabel()).isEqualTo("Tesouro Selic");
        assertThat(r.after().largestPercent()).isEqualByComparingTo("57.14");
        assertThat(r.after().hhi()).isEqualByComparingTo("5102.04");
        assertThat(r.after().effectivePositions()).isEqualByComparingTo("1.96");
        assertThat(r.findings()).extracting(Finding::text).anyMatch(t -> t.contains("mais diversificada: posições efetivas de 1,92 para 1,96"));
    }

    @Test void weightedVolatilityCoversOnlyPositionsWithRealData() {
        Result partial = PreOrderImpact.analyze(List.of(stock("AAA", "600", "0.30"), bond("400")), buy(bond("400")), null);
        assertThat(partial.before().weightedVolatility()).isEqualByComparingTo("0.3000");
        assertThat(partial.before().volatilityCoverage()).isEqualByComparingTo("60.00");
        assertThat(partial.after().volatilityCoverage()).isEqualByComparingTo("42.86");
        assertThat(partial.findings()).extracting(Finding::text).anyMatch(t -> t.contains("só 42,86% da carteira"));
        // com volatilidade nas duas: 0,6 x 0,30 + 0,4 x 0,10 = 0,22
        Position b = new Position("BBB", "BBB", AssetType.ACAO, new BigDecimal("400"), new BigDecimal("0.10"));
        Result full = PreOrderImpact.analyze(List.of(stock("AAA", "600", "0.30"), b), buy(stock("AAA", "400", "0.30")), null);
        assertThat(full.before().weightedVolatility()).isEqualByComparingTo("0.2200");
        assertThat(full.before().volatilityCoverage()).isEqualByComparingTo("100.00");
        // depois: AAA 1.000 e BBB 400 de 1.400 -> (1000 x 0,30 + 400 x 0,10) / 1400 = 0,2429
        assertThat(full.after().weightedVolatility()).isEqualByComparingTo("0.2429");
        assertThat(full.findings()).extracting(Finding::text).anyMatch(t -> t.contains("volatilidade média ponderada") && t.contains("22,00%"));
    }

    @Test void aSaleCannotExceedOrInventAPosition() {
        assertThatThrownBy(() -> PreOrderImpact.analyze(List.of(stock("AAA", "600", null)), sell(stock("AAA", "600.01", null)), null))
                .hasMessageContaining("passa do valor da posição");
        assertThatThrownBy(() -> PreOrderImpact.analyze(List.of(stock("AAA", "600", null)), sell(stock("ZZZ", "1", null)), null))
                .hasMessageContaining("não tem posição");
        assertThatThrownBy(() -> PreOrderImpact.analyze(List.of(), sell(stock("AAA", "1", null)), null)).hasMessageContaining("não tem posição");
    }

    @Test void sellingTheWholePositionRemovesItAndAnEmptyPortfolioHasNoMetrics() {
        Result r = PreOrderImpact.analyze(List.of(stock("AAA", "600", null), bond("400")), sell(stock("AAA", "600", null)), null);
        assertThat(r.after().positions()).isEqualTo(1);
        assertThat(r.after().largestLabel()).isEqualTo("Tesouro Selic");
        assertThat(r.after().largestPercent()).isEqualByComparingTo("100.00");
        Result empty = PreOrderImpact.analyze(List.of(stock("AAA", "600", null)), sell(stock("AAA", "600", null)), null);
        assertThat(empty.after().empty()).isTrue();
        assertThat(empty.after().hhi()).isNull();
        assertThat(empty.findings()).extracting(Finding::text).containsExactly("Depois da venda a carteira ficaria vazia.");
    }

    @Test void partialSaleReducesThePositionExactly() {
        Result r = PreOrderImpact.analyze(List.of(stock("AAA", "600.50", null), bond("399.50")), sell(stock("AAA", "100.25", null)), null);
        assertThat(r.after().totalBrl()).isEqualByComparingTo("899.75");
        assertThat(r.after().largestPercent()).isEqualByComparingTo("55.60");  // 500,25 / 899,75 = 55,5988...
    }

    @Test void buyingIntoAnEmptyPortfolioHasAnEmptyBeforeAndNoAdherenceBefore() {
        Result r = PreOrderImpact.analyze(List.of(), buy(stock("AAA", "1000", null)), InvestorProfile.MODERATE);
        assertThat(r.before().empty()).isTrue();
        assertThat(r.adherenceBefore()).isNull();
        assertThat(r.adherenceAfter()).isNotNull();
        assertThat(r.after().largestPercent()).isEqualByComparingTo("100.00");
        assertThat(r.findings()).extracting(Finding::level).contains(Level.ATTENTION);
    }

    @Test void samePositionsAreMergedAndInconsistentTypesRejected() {
        Result r = PreOrderImpact.analyze(List.of(stock("AAA", "300", null), stock("AAA", "200", null), bond("500")), buy(bond("1")), null);
        assertThat(r.before().positions()).isEqualTo(2);
        assertThat(r.before().largestPercent()).isEqualByComparingTo("50.00");
        Position wrongType = new Position("AAA", "AAA", AssetType.FII, new BigDecimal("1"), null);
        assertThatThrownBy(() -> PreOrderImpact.analyze(List.of(stock("AAA", "300", null), wrongType), buy(bond("1")), null))
                .hasMessageContaining("Tipos diferentes");
    }

    @Test void adherenceDropIsAnAttentionFindingWithBothScores() {
        // moderado: 30/25/15/25/0/5. Carteira perto da meta; comprar muita ação a afasta.
        List<Position> near = List.of(bond("300"), new Position(null, "LCI", AssetType.LCI, new BigDecimal("250"), null),
                new Position(null, "Fundo multimercado", AssetType.FUNDO_MULTIMERCADO, new BigDecimal("150"), null), stock("AAA", "250", null),
                new Position(null, "Commodity", AssetType.COMMODITY, new BigDecimal("50"), null));
        Result r = PreOrderImpact.analyze(near, buy(stock("AAA", "1000", null)), InvestorProfile.MODERATE);
        assertThat(r.adherenceBefore().score()).isEqualByComparingTo("100");
        assertThat(r.adherenceAfter().score()).isLessThan(new BigDecimal("100"));
        assertThat(r.findings()).filteredOn(f -> f.level() == Level.ATTENTION).extracting(Finding::text)
                .anyMatch(t -> t.contains("aderência ao perfil MODERADO cai de 100,00"));
    }

    @Test void buyingAClassWhoseTargetIsZeroIsFlagged() {
        List<Position> start = List.of(bond("900"), new Position(null, "LCI", AssetType.LCI, new BigDecimal("100"), null));
        Position crypto = new Position("BTC", "BTC", AssetType.CRIPTO_ATIVO, new BigDecimal("100"), null);
        Result r = PreOrderImpact.analyze(start, buy(crypto), InvestorProfile.CONSERVATIVE);
        assertThat(r.findings()).extracting(Finding::text).anyMatch(t -> t.contains("Criptoativos é 0%"));
        // quem já tinha cripto antes não recebe o aviso de "passaria a ter"
        Result again = PreOrderImpact.analyze(List.of(bond("900"), crypto), buy(crypto), InvestorProfile.CONSERVATIVE);
        assertThat(again.findings()).extracting(Finding::text).noneMatch(t -> t.contains("passaria a ter"));
    }

    @Test void largestPositionWarningUsesTheProductLimitInclusively() {
        // 25% exato não avisa; acima, sim
        Result at = PreOrderImpact.analyze(List.of(stock("AAA", "250", null), bond("250"), new Position(null, "LCI", AssetType.LCI, new BigDecimal("250"), null)),
                buy(new Position(null, "LCA", AssetType.LCA, new BigDecimal("250"), null)), null);
        assertThat(at.after().largestPercent()).isEqualByComparingTo("25.00");
        assertThat(at.findings()).extracting(Finding::level).doesNotContain(Level.ATTENTION);
        Result above = PreOrderImpact.analyze(List.of(stock("AAA", "251", null), bond("249"), new Position(null, "LCI", AssetType.LCI, new BigDecimal("250"), null)),
                buy(new Position(null, "LCA", AssetType.LCA, new BigDecimal("250"), null)), null);
        assertThat(above.findings()).extracting(Finding::level).contains(Level.ATTENTION);
    }

    @Test void astronomicalValuesAndTooManyPositionsAreRejectedFast() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> stock("AAA", "1E5000000", null)).hasMessageContaining("fora do intervalo");
        assertThatThrownBy(() -> stock("AAA", "1E999999999", null)).hasMessageContaining("fora do intervalo");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500);
        List<Position> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) many.add(stock("S" + i, "1", null));
        assertThatThrownBy(() -> PreOrderImpact.analyze(many, buy(bond("1")), null)).hasMessageContaining("No máximo 50");
    }

    @Test void invalidInputsAreErrors() {
        assertThatThrownBy(() -> new Position("IBOV", "IBOV", AssetType.INDICE, BigDecimal.ONE, null)).hasMessageContaining("referência");
        assertThatThrownBy(() -> stock("AAA", "0", null)).hasMessageContaining("positivo");
        assertThatThrownBy(() -> stock("AAA", "-1", null)).hasMessageContaining("positivo");
        assertThatThrownBy(() -> new Position("AAA", "AAA", AssetType.ACAO, BigDecimal.ONE, new BigDecimal("-0.1"))).hasMessageContaining("negativa");
        assertThatThrownBy(() -> PreOrderImpact.analyze(null, buy(bond("1")), null)).hasMessageContaining("ausente");
    }

    @Test void classPercentsCoverEverySimulatedClass() {
        Result r = PreOrderImpact.analyze(List.of(stock("AAA", "600", null), bond("400")), buy(bond("100")), null);
        assertThat(r.after().classPercents()).containsKeys(AssetCategory.RENDA_VARIAVEL, AssetCategory.RENDA_FIXA_PUBLICA, AssetCategory.CRIPTO);
        assertThat(r.after().classPercents().get(AssetCategory.RENDA_VARIAVEL)).isEqualByComparingTo("54.55");
    }
}
