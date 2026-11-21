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
}
