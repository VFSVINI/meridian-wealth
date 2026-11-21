package dev.meridianwealth.application;

import dev.meridianwealth.domain.Assessment;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Indicators;
import dev.meridianwealth.domain.Signal;
import dev.meridianwealth.domain.TimingEngine;
import java.util.ArrayList;
import java.util.List;

/** Backtest analítico: entra quando score ≥ buyScore, sai no stop técnico ou quando score ≤ -sellScore. */
public class BacktestService {
    public record Trade(int entryIdx, int exitIdx, double entry, double exit, double ret, String reason) {}
    public record Result(List<Trade> trades, double strategyReturn, double buyHoldReturn,
                         double winRate, double maxDrawdown, int evaluatedBars) {}

    private final TimingEngine engine = new TimingEngine();

    public Result run(List<Candle> candles, int buyScore, int sellScore, double macroRisk) {
        return run(candles, buyScore, sellScore, macroRisk, TimingEngine.DEFAULT_PERIODS_PER_YEAR);
    }

    /** {@code perYear}: base de anualização do ativo (365 para cripto, 252 para os demais). */
    public Result run(List<Candle> candles, int buyScore, int sellScore, double macroRisk, int perYear) {
        int n = candles.size(), start = TimingEngine.MIN_BARS;
        if (n <= start + 1) return new Result(List.of(), 0, 0, 0, 0, 0);
        List<Trade> trades = new ArrayList<>();
        double[] eq = new double[n - start];
        double realized = 1, entry = 0, stop = 0;
        int entryIdx = 0;
        boolean in = false;
        for (int i = start; i < n; i++) {
            double close = candles.get(i).close();
            Assessment a = engine.assess("BT", candles.subList(0, i + 1), macroRisk, null, perYear);
            if (in) {
                String why = close <= stop ? "stop técnico" : a.score() <= -sellScore ? "realização" : null;
                if (why != null) {
                    realized *= close / entry;
                    trades.add(new Trade(entryIdx, i, entry, close, close / entry - 1, why));
                    in = false;
                }
            } else if (a.score() >= buyScore && a.signal() != Signal.HIGH_RISK_WAIT) {
                in = true;
                entry = close;
                stop = a.invalidation();
                entryIdx = i;
            }
            eq[i - start] = in ? realized * close / entry : realized;
        }
        if (in) {
            double close = candles.get(n - 1).close();
            realized *= close / entry;
            trades.add(new Trade(entryIdx, n - 1, entry, close, close / entry - 1, "fim da janela"));
            eq[eq.length - 1] = realized;
        }
        long wins = trades.stream().filter(t -> t.ret() > 0).count();
        double bh = candles.get(n - 1).close() / candles.get(start).close() - 1;
        return new Result(trades, realized - 1, bh, trades.isEmpty() ? 0 : (double) wins / trades.size(),
                Indicators.maxDrawdown(eq), n - start);
    }
}
