package dev.meridianwealth.domain.montecarlo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.meridianwealth.domain.montecarlo.MonteCarlo.BootstrapModel;
import dev.meridianwealth.domain.montecarlo.MonteCarlo.Params;
import dev.meridianwealth.domain.montecarlo.MonteCarlo.ParametricModel;
import dev.meridianwealth.domain.montecarlo.MonteCarlo.Result;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class MonteCarloTest {
    private static double closedForm(double v0, double c, double annual, int months) {
        double g = Math.pow(1 + annual, 1.0 / 12) - 1, v = v0;
        for (int m = 0; m < months; m++) v = v * (1 + g) + c;
        return v;
    }

    @Test void theSameSeedGivesExactlyTheSameProjectionAndAnotherSeedDoesNot() {
        Params p = new Params(100_000, 1_000, 120, 500_000.0, 42);
        Result a = MonteCarlo.run(p, new ParametricModel(0.10, 0.18));
        Result b = MonteCarlo.run(p, new ParametricModel(0.10, 0.18));
        assertThat(a.percentiles()).isDeepEqualTo(b.percentiles());
        assertThat(a.histogramCounts()).isEqualTo(b.histogramCounts());
        assertThat(a.goalProbabilityFinal()).isEqualTo(b.goalProbabilityFinal());
        Result c = MonteCarlo.run(new Params(100_000, 1_000, 120, 500_000.0, 43), new ParametricModel(0.10, 0.18));
        assertThat(c.percentiles()[2][120]).isNotEqualTo(a.percentiles()[2][120]);
    }

    @Test void percentilesNeverCrossAndStartAtTheInitialValue() {
        Result r = MonteCarlo.run(new Params(50_000, 500, 240, null, 7), new ParametricModel(0.08, 0.25));
        for (int m = 0; m <= 240; m++) {
            for (int l = 1; l < MonteCarlo.LEVELS.length; l++) assertThat(r.percentiles()[l][m]).isGreaterThanOrEqualTo(r.percentiles()[l - 1][m]);
        }
        for (double[] level : r.percentiles()) assertThat(level[0]).isEqualTo(50_000);
        assertThat(r.percentiles()[4][240]).as("o cone abre com o tempo").isGreaterThan(r.percentiles()[4][12] - r.percentiles()[0][12]);
        assertThat(r.percentiles()[4][240] - r.percentiles()[0][240]).isGreaterThan(r.percentiles()[4][12] - r.percentiles()[0][12]);
    }

    @Test void withZeroVolatilityEveryPercentileEqualsTheClosedFormula() {
        Result r = MonteCarlo.run(new Params(1_000, 100, 24, null, 1), new ParametricModel(0.12, 0));
        double expected = closedForm(1_000, 100, 0.12, 24);
        for (double[] level : r.percentiles()) assertThat(level[24]).isCloseTo(expected, within(1e-6));
        assertThat(r.totalInvested()).isEqualTo(1_000 + 100 * 24);
        assertThat(r.probabilityBelowInvested()).as("rende 12% ao ano sem risco: nunca termina abaixo do investido").isZero();
    }

    @Test void contributionsAloneWithNoReturnAddUpExactly() {
        Result r = MonteCarlo.run(new Params(0, 250, 36, null, 1), new ParametricModel(0.0, 0));
        assertThat(r.percentiles()[2][36]).isCloseTo(250 * 36, within(1e-6));
        assertThat(r.mean()).isCloseTo(9000, within(1e-6));
    }

    @Test void theGoalIsCertainWhenZeroAndImpossibleWhenHugeAndPerYearFollowsTheCurve() {
        Params base = new Params(10_000, 0, 60, 0.0, 3);
        assertThat(MonteCarlo.run(base, new ParametricModel(0.05, 0.1)).goalProbabilityFinal()).isEqualTo(1.0);
        Result huge = MonteCarlo.run(new Params(10_000, 0, 60, 1e15, 3), new ParametricModel(0.05, 0.1));
        assertThat(huge.goalProbabilityFinal()).isZero();
        Result mid = MonteCarlo.run(new Params(10_000, 0, 60, closedForm(10_000, 0, 0.10, 36), 3), new ParametricModel(0.10, 0));
        assertThat(mid.goalProbabilityByYear()).hasSize(6);
        assertThat(mid.goalProbabilityByYear()[0]).isZero();
        assertThat(mid.goalProbabilityByYear()[2]).isZero();
        assertThat(mid.goalProbabilityByYear()[3]).as("sem risco, a meta é batida exatamente no mês 36").isEqualTo(1.0);
        assertThat(mid.goalProbabilityByYear()[5]).isEqualTo(1.0);
        assertThat(MonteCarlo.run(new Params(10_000, 0, 12, null, 3), new ParametricModel(0.05, 0.1)).goalProbabilityFinal()).isNull();
    }

    @Test void theSimulatedMeanFollowsTheExpectedReturnInTheLongRun() {
        Result r = MonteCarlo.run(new Params(100_000, 0, 120, null, 11), new ParametricModel(0.09, 0.20));
        assertThat(r.mean()).as("média simulada de 5.000 trajetórias contra a esperada").isCloseTo(100_000 * Math.pow(1.09, 10), within(100_000 * Math.pow(1.09, 10) * 0.05));
        assertThat(r.percentiles()[2][120]).as("a mediana fica abaixo da média numa log-normal").isLessThan(r.mean());
    }

    @Test void theHistogramCountsEveryPathAndItsEdgesAreIncreasing() {
        Result r = MonteCarlo.run(new Params(10_000, 200, 60, null, 5), new ParametricModel(0.07, 0.15));
        assertThat(Arrays.stream(r.histogramCounts()).sum()).isEqualTo(MonteCarlo.PATHS);
        assertThat(r.histogramEdges()).hasSize(MonteCarlo.BINS + 1).isSorted();
    }

    @Test void aLossMakingMarketEndsBelowWhatWasInvested() {
        Result r = MonteCarlo.run(new Params(10_000, 100, 60, null, 5), new ParametricModel(-0.05, 0));
        assertThat(r.probabilityBelowInvested()).isEqualTo(1.0);
    }

    @Test void bootstrapOfAConstantDailyReturnCompoundsTheBlockAndLeavesTheUncoveredShareStill() {
        double[][] daily = {new double[300]};
        Arrays.fill(daily[0], 0.001);
        double month = Math.pow(1.001, 21) - 1;
        Result full = MonteCarlo.run(new Params(1_000, 0, 12, null, 9), new BootstrapModel(new double[] {1.0}, daily, 21));
        assertThat(full.percentiles()[2][12]).isCloseTo(1_000 * Math.pow(1 + month, 12), within(1e-6));
        Result half = MonteCarlo.run(new Params(1_000, 0, 12, null, 9), new BootstrapModel(new double[] {0.5}, daily, 21));
        assertThat(half.percentiles()[2][12]).as("metade da carteira sem histórico fica parada: só a outra metade rende").isCloseTo(1_000 * Math.pow(1 + 0.5 * month, 12), within(1e-6));
    }

    @Test void bootstrapKeepsTheCorrelationBecauseAllAssetsShareTheSameWindow() {
        // dois ativos perfeitamente opostos: quando um sobe o outro cai no mesmo dia, então a carteira 50/50 quase não se mexe
        int n = 400;
        double[][] daily = new double[2][n];
        for (int d = 0; d < n; d++) {
            double r = d % 2 == 0 ? 0.02 : -0.018;
            daily[0][d] = r;
            daily[1][d] = -r;
        }
        Result together = MonteCarlo.run(new Params(1_000, 0, 24, null, 4), new BootstrapModel(new double[] {0.5, 0.5}, daily, 21));
        double spread = together.percentiles()[4][24] - together.percentiles()[0][24];
        Result alone = MonteCarlo.run(new Params(1_000, 0, 24, null, 4), new BootstrapModel(new double[] {1.0}, new double[][] {daily[0]}, 21));
        double spreadAlone = alone.percentiles()[4][24] - alone.percentiles()[0][24];
        assertThat(spread).isLessThan(spreadAlone * 0.2 + 1e-9);
    }

    @Test void premisesThatBlowTheValueBeyondWhatIsRepresentableAreRefusedNotTurnedIntoInfinity() {
        assertThatThrownBy(() -> MonteCarlo.run(new Params(9e14, 1e9, 480, null, 1), new ParametricModel(0.99, 1.99))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("além do que o cálculo representa");
        Result ok = MonteCarlo.run(new Params(9e14, 1e9, 480, null, 1), new ParametricModel(0.05, 0.1));
        assertThat(ok.percentiles()[4][480]).isFinite().isLessThan(MonteCarlo.MAX_VALUE);
    }

    @Test void invalidInputsAreRefused() {
        assertThatThrownBy(() -> new Params(-1, 0, 12, null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Params(0, 0, 12, null, 1)).hasMessageContaining("patrimônio inicial ou um aporte");
        assertThatThrownBy(() -> new Params(1, 0, 0, null, 1)).hasMessageContaining("horizonte");
        assertThatThrownBy(() -> new Params(1, 0, MonteCarlo.MAX_MONTHS + 1, null, 1)).hasMessageContaining("horizonte");
        assertThatThrownBy(() -> new Params(1, 0, 12, -5.0, 1)).hasMessageContaining("Meta");
        assertThatThrownBy(() -> new ParametricModel(-1.0, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParametricModel(0.1, -0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BootstrapModel(new double[] {1}, new double[][] {new double[10]}, 21)).hasMessageContaining("curto demais");
        assertThatThrownBy(() -> new BootstrapModel(new double[] {1, 1}, new double[][] {new double[30]}, 21)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void alignedReturnsKeepOnlyTheDatesEveryAssetHasAndComposeAcrossGaps() {
        TreeMap<Long, Double> a = new TreeMap<>(Map.of(1L, 100.0, 2L, 110.0, 3L, 121.0, 5L, 133.1));
        TreeMap<Long, Double> b = new TreeMap<>(Map.of(1L, 50.0, 3L, 45.0, 4L, 46.0, 5L, 54.0));
        AlignedReturns r = AlignedReturns.of(new java.util.LinkedHashMap<>(Map.of("A", a, "B", b)));
        assertThat(r.dates()).containsExactly(3L, 5L);
        double ia = r.symbols().indexOf("A"), ib = r.symbols().indexOf("B");
        assertThat(r.returns()[(int) ia][0]).isCloseTo(121.0 / 100 - 1, within(1e-12));
        assertThat(r.returns()[(int) ib][0]).isCloseTo(45.0 / 50 - 1, within(1e-12));
        assertThat(r.returns()[(int) ia][1]).as("o dia 4 não existe em A: o retorno cobre de 3 a 5").isCloseTo(133.1 / 121 - 1, within(1e-12));
        assertThat(r.portfolioDaily(new double[] {0.5, 0.5})[0]).isCloseTo(0.5 * 0.21 + 0.5 * -0.1, within(1e-12));
        assertThat(AlignedReturns.of(Map.of("A", new TreeMap<>(Map.of(1L, 1.0)), "B", new TreeMap<>(Map.of(2L, 1.0))))).as("sem datas em comum").isNull();
        assertThatThrownBy(() -> AlignedReturns.of(Map.of("A", new TreeMap<>(Map.of(1L, 1.0, 2L, 0.0))))).isInstanceOf(IllegalArgumentException.class);
    }
}
