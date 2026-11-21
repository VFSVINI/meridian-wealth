package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.meridianwealth.testsupport.SyntheticCandles;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Anualização por √365, beta contra o benchmark e calendário 24/7 dos criptoativos. */
class CryptoDomainTest {
    private static double[] closes(List<Candle> c) {
        return c.stream().mapToDouble(Candle::close).toArray();
    }

    private static double[] scaledReturns(double[] bench, double factor) {
        double[] out = new double[bench.length];
        out[0] = bench[0];
        for (int i = 1; i < bench.length; i++) out[i] = out[i - 1] * Math.exp(factor * Math.log(bench[i] / bench[i - 1]));
        return out;
    }

    @Test void cryptoInstrumentsAnnualizeBy365AndTheRestBy252() {
        Instrument btc = Instrument.of("BTC", "Bitcoin", Exchange.CRYPTO, "Cripto", "cripto", "USD", null);
        Instrument aapl = Instrument.of("AAPL", "Apple", Exchange.NYSE, "Ação", "tecnologia", "USD", "SPY");
        Instrument fx = Instrument.of("EURUSD", "EUR/USD", Exchange.GLOBAL_24H, "Câmbio", "cambio", "USD", null);
        assertThat(btc.periodsPerYear()).isEqualTo(365);
        assertThat(aapl.periodsPerYear()).isEqualTo(252);
        assertThat(fx.periodsPerYear()).isEqualTo(252);
        assertThat(btc.region()).isEqualTo("Cripto");
    }

    @Test void annualizedVolatilityScalesWithTheSquareRootOfThePeriods() {
        List<Candle> c = SyntheticCandles.series(11, 300, 100, 0.8);
        TimingEngine engine = new TimingEngine();
        Assessment stock = engine.assess("X", c, 0, null, 252);
        Assessment crypto = engine.assess("X", c, 0, null, 365);
        assertThat(crypto.annVol() / stock.annVol()).isCloseTo(Math.sqrt(365.0 / 252.0), within(1e-9));
        assertThat(crypto.periodsPerYear()).isEqualTo(365);
        assertThat(engine.assess("X", c, 0).periodsPerYear()).isEqualTo(252);
        assertThat(crypto.sharpe() / stock.sharpe()).isCloseTo(Math.sqrt(365.0 / 252.0), within(1e-9));
    }

    @Test void betaMatchesTheLeverageOfTheReturns() {
        double[] btc = closes(SyntheticCandles.series(3, 300, 60_000, 0.6));
        assertThat(Indicators.beta(scaledReturns(btc, 2.0), btc, 90)).isCloseTo(2.0, within(1e-9));
        assertThat(Indicators.beta(scaledReturns(btc, 0.5), btc, 90)).isCloseTo(0.5, within(1e-9));
        assertThat(Indicators.beta(btc, btc, 90)).isCloseTo(1.0, within(1e-9));
    }

    @Test void betaOfIndependentSeriesIsNearZeroAndUnavailableInputsAreNaN() {
        double[] a = closes(SyntheticCandles.series(1, 600, 100, 0.5));
        double[] b = closes(SyntheticCandles.series(2, 600, 100, 0.5));
        assertThat(Math.abs(Indicators.beta(a, b, 500))).isLessThan(0.25);
        assertThat(Indicators.beta(a, null, 90)).isNaN();
        assertThat(Indicators.beta(new double[50], b, 90)).isNaN();
        double[] flat = new double[200];
        java.util.Arrays.fill(flat, 10.0);
        assertThat(Indicators.beta(a, flat, 90)).as("benchmark sem variação").isNaN();
    }

    @Test void assessmentExposesBetaOnlyWhenThereIsABenchmark() {
        List<Candle> btc = SyntheticCandles.series(5, 300, 60_000, 0.6);
        double[] bench = closes(btc);
        List<Candle> alt = SyntheticCandles.series(6, 300, 70, 0.9);
        TimingEngine engine = new TimingEngine();
        assertThat(engine.assess("ALT", alt, 0, bench, 365).beta()).isNotNull();
        assertThat(engine.assess("ALT", alt, 0, null, 365).beta()).isNull();
    }

    @Test void cryptoNeverClosesNotOnWeekendsNorOnChristmas() {
        for (String when : List.of("2026-11-21T17:00:00Z", "2026-11-22T03:00:00Z", "2026-12-25T12:00:00Z",
                "2026-01-01T00:00:00Z", "2026-11-20T23:59:59Z")) {
            MarketHours.SessionStatus s = MarketHours.status(Exchange.CRYPTO, Instant.parse(when));
            assertThat(s.open()).as(when).isTrue();
            assertThat(s.reason()).isEqualTo("mercado aberto 24/7");
            assertThat(s.holidaysVerified()).isTrue();
        }
    }
}
