package dev.meridianwealth.testsupport;

import dev.meridianwealth.domain.Candle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gerador determinístico de barras para testes (movimento browniano geométrico com semente fixa).
 *
 * <p>Herdeiro do antigo simulador do produto: pela regra de dados 100% reais (ADR 004), ele só
 * existe em {@code src/test} e nunca chega a uma cotação exibida ao usuário.
 */
public final class SyntheticCandles {
    private static final long DAY = 86_400_000L;
    private static final long START = 1_700_000_000_000L;

    private SyntheticCandles() {}

    public static List<Candle> series(long seed, int bars, double startPrice, double annualVol) {
        Random rnd = new Random(seed);
        double dailyVol = annualVol / Math.sqrt(252);
        double price = startPrice;
        List<Candle> out = new ArrayList<>(bars);
        for (int i = 0; i < bars; i++) {
            double g = rnd.nextGaussian();
            double open = price;
            double close = open * Math.exp(dailyVol * g);
            double hi = Math.max(open, close) * (1 + Math.abs(rnd.nextGaussian()) * dailyVol * 0.35);
            double lo = Math.min(open, close) * (1 - Math.abs(rnd.nextGaussian()) * dailyVol * 0.35);
            out.add(new Candle(START + i * DAY, open, hi, lo, close, 1_000_000 * (0.8 + Math.abs(g) * 0.8)));
            price = close;
        }
        return out;
    }
}
