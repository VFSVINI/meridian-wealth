package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IndicatorsTest {
    private static double[] range(int from, int to) {
        return IntStream.rangeClosed(from, to).asDoubleStream().toArray();
    }

    @Test void smaOfLastWindow() { assertThat(Indicators.sma(range(1, 10), 5)).isEqualTo(8.0); }

    @Test void zScoreOfLastPoint() {
        assertThat(Indicators.zScore(range(1, 5), 5)).isCloseTo(1.2649, within(1e-3));
    }

    @Test void zScoreOfConstantSeriesIsZero() {
        assertThat(Indicators.zScore(new double[] {5, 5, 5, 5, 5}, 5)).isZero();
    }

    @Test void percentRankOfMaximumIs100() { assertThat(Indicators.percentRank(range(1, 10), 10)).isEqualTo(100.0); }

    @Test void rsiExtremes() {
        double[] down = range(1, 30);
        for (int i = 0; i < down.length; i++) down[i] = 100 - down[i];
        assertThat(Indicators.rsi(range(1, 30), 14)).isEqualTo(100.0);
        assertThat(Indicators.rsi(down, 14)).isCloseTo(0.0, within(1e-9));
    }

    @Test void maxDrawdownKnownCase() {
        assertThat(Indicators.maxDrawdown(new double[] {100, 120, 60, 90})).isCloseTo(0.5, within(1e-12));
    }

    @Test void valueAtRiskIsPositiveLoss() {
        double[] r = new double[100];
        for (int i = 0; i < 100; i++) r[i] = (i - 50) / 1000.0;
        assertThat(Indicators.valueAtRisk(r, 0.95)).isCloseTo(0.045, within(1e-9));
    }

    @Test void kellyIsFractionalAndCapped() {
        assertThat(Indicators.kelly(0.6, 2, 0.25, 0.20)).isCloseTo(0.10, within(1e-12));
        assertThat(Indicators.kelly(0.6, 2, 0.25, 0.05)).isEqualTo(0.05);
        assertThat(Indicators.kelly(0.3, 1, 0.25, 0.20)).isZero();
    }

    @Test void betaIsTheSlopeOfTheAssetOnTheBenchmarkAndFlatBenchmarksHaveNone() {
        double[] bench = new double[130], asset = new double[130], flat = new double[130];
        for (int i = 0; i < 130; i++) {
            bench[i] = 100 * Math.exp(0.01 * Math.sin(i * 0.9) + 0.0002 * i);
            asset[i] = Math.pow(bench[i] / 100, 2) * 50; // retorno logarítmico em dobro: beta 2
            flat[i] = 100 * Math.exp(0.0005 * i); // retorno constante: sem variância
        }
        assertThat(Indicators.beta(asset, bench, 90)).isCloseTo(2.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(Indicators.beta(asset, flat, 90)).as("benchmark parado: não há beta, nunca um número gigante").isNaN();
        assertThat(Indicators.beta(asset, null, 90)).isNaN();
        assertThat(Indicators.beta(asset, bench, 1)).isNaN();
        assertThat(Indicators.beta(asset, java.util.Arrays.copyOf(bench, 90), 90)).as("benchmark curto").isNaN();
    }

    @Test void relativeVolumeIsTheLastBarOverTheAverageOfThePreviousN() {
        java.util.List<Candle> cs = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) cs.add(new Candle(i, 1, 1, 1, 1, 10));
        cs.add(new Candle(4, 1, 1, 1, 1, 30));
        assertThat(Indicators.relativeVolume(cs, 4)).isEqualTo(3.0);
        assertThat(Indicators.relativeVolume(cs, 5)).as("sem barras anteriores suficientes").isEqualTo(1.0);
        assertThat(Indicators.relativeVolume(cs, 6)).isEqualTo(1.0);
        java.util.List<Candle> noVolume = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) noVolume.add(new Candle(i, 1, 1, 1, 1, 0));
        assertThat(Indicators.relativeVolume(noVolume, 4)).as("sem volume, neutro").isEqualTo(1.0);
    }

    @Test void atrOfAConstantRangeIsTheRangeAndGapsCountFromThePreviousClose() {
        java.util.List<Candle> flat = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) flat.add(new Candle(i, 10, 11, 9, 10, 1));
        assertThat(Indicators.atr(flat, 14)).isCloseTo(2.0, org.assertj.core.api.Assertions.within(1e-12));
        java.util.List<Candle> gap = new java.util.ArrayList<>(flat);
        gap.add(new Candle(30, 20, 21, 19, 20, 1));
        assertThat(Indicators.atr(gap, 14)).as("o gap entra no ATR").isGreaterThan(2.5);
    }
}
