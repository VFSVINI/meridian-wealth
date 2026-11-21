package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimingEngineTest {
    private final TimingEngine engine = new TimingEngine();

    /** Ruído determinístico em torno de {@code base}. */
    private static double noisy(double base, int i) {
        return base * (1 + 0.004 * Math.sin(i * 0.9) + 0.003 * Math.cos(i * 2.3));
    }

    private static List<Candle> candles(double[] closes) {
        List<Candle> out = new ArrayList<>();
        double prev = closes[0];
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            out.add(new Candle(i * 86_400_000L, prev, Math.max(prev, c) * 1.003, Math.min(prev, c) * 0.997, c, 1000));
            prev = c;
        }
        return out;
    }

    @Test void stabilizingDipIsAnAccumulationWindow() {
        double[] c = new double[209];
        for (int i = 0; i < 200; i++) c[i] = noisy(100, i);
        for (int i = 200; i < 207; i++) c[i] = c[i - 1] * 0.991;   // recuo controlado
        for (int i = 207; i < 209; i++) c[i] = c[i - 1] * 1.002;   // perda de força vendedora
        Assessment a = engine.assess("T", candles(c), 0.3);
        assertThat(a.score()).isGreaterThanOrEqualTo(20);
        assertThat(a.signal()).isIn(Signal.BUY_DIP_WINDOW, Signal.ACCUMULATE);
        assertThat(a.suggestedExposure()).isBetween(0.0, 0.10);
    }

    @Test void parabolicSpikeSignalsRealization() {
        double[] c = new double[210];
        for (int i = 0; i < 200; i++) c[i] = noisy(100, i);
        for (int i = 200; i < 210; i++) c[i] = c[i - 1] * 1.02;
        Assessment a = engine.assess("T", candles(c), 0.3);
        assertThat(a.score()).isLessThan(-20);
        assertThat(a.signal()).isIn(Signal.TAKE_PROFIT_WINDOW, Signal.SELL_EXIT_SETUP);
        assertThat(a.suggestedExposure()).isZero();
    }

    @Test void freeFallIsAFallingKnifeNotABuy() {
        double[] c = new double[206];
        for (int i = 0; i < 200; i++) c[i] = noisy(100, i);
        double[] drops = {-0.03, -0.06, -0.02, -0.07, -0.03, -0.05};
        for (int i = 0; i < 6; i++) c[200 + i] = c[199 + i] * (1 + drops[i]);
        Assessment a = engine.assess("T", candles(c), 0.3);
        assertThat(a.signal()).isEqualTo(Signal.HIGH_RISK_WAIT);
        assertThat(a.phase()).isEqualTo(Phase.CAUTELA_EVITAR);
        assertThat(a.suggestedExposure()).isZero();
    }

    @Test void rejectsShortHistory() {
        double[] c = new double[50];
        java.util.Arrays.fill(c, 100);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> engine.assess("T", candles(c), 0));
    }
}
