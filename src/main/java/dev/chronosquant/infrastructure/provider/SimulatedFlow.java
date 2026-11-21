package dev.chronosquant.infrastructure.provider;

import dev.chronosquant.domain.Candle;
import dev.chronosquant.domain.Indicators;
import dev.chronosquant.domain.Instrument;
import dev.chronosquant.domain.MacroSnapshot;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Simulador de fluxo de mercado (fallback e modo offline): movimento browniano geométrico com troca de
 * regimes. Cada barra simulada equivale a um dia de pregão.
 */
@Component
public class SimulatedFlow {
    private static final String[] SHOCKS = {
        "Escalada tarifária entre grandes economias", "Tensão geopolítica afeta rotas de energia",
        "Banco central sinaliza juros altos por mais tempo", "Choque de oferta pressiona commodities"};
    private static final String CALM = "Cenário calmo (simulado)";

    private static final class State {
        final Random rnd;
        final Instrument ins;
        double price;
        double anchor;
        double drift;
        double volMul = 1;
        String regime = "RANGE";
        int ttl;
        long time;

        State(Instrument ins, long startTime) {
            this.ins = ins;
            this.rnd = new Random(ins.symbol().hashCode());
            this.price = ins.startPrice();
            this.anchor = price;
            this.time = startTime;
        }
    }

    private final Map<String, State> states = new HashMap<>();
    private final Random macroRnd = new Random(7);
    private double vix = 16;
    private double usd = 104;
    private double y10 = 4.2;
    private double geo = 0.25;
    private String headline = CALM;
    private int shockTtl;

    public SimulatedFlow() {
        long start = LocalDate.now(ZoneOffset.UTC).minusDays(800).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        Universe.instruments().forEach(i -> states.put(i.symbol(), new State(i, start)));
    }

    public synchronized List<Candle> history(String symbol, int bars) {
        State s = states.get(symbol);
        List<Candle> out = new ArrayList<>(bars);
        for (int i = 0; i < bars; i++) out.add(step(s));
        return out;
    }

    public synchronized Candle next(String symbol) { return step(states.get(symbol)); }

    public synchronized MacroSnapshot nextMacro() {
        vix += 0.12 * (16 - vix) + macroRnd.nextGaussian() * 0.5;
        geo += 0.04 * (0.25 - geo) + macroRnd.nextGaussian() * 0.012;
        usd += macroRnd.nextGaussian() * 0.06;
        y10 += macroRnd.nextGaussian() * 0.01;
        if (shockTtl > 0 && --shockTtl == 0) headline = CALM;
        if (macroRnd.nextDouble() < 0.01) {
            geo += 0.25;
            vix += 6;
            shockTtl = 25;
            headline = SHOCKS[macroRnd.nextInt(SHOCKS.length)] + " (simulado)";
        }
        vix = Indicators.clamp(vix, 10, 55);
        geo = Indicators.clamp(geo, 0, 1);
        return MacroSnapshot.of(vix, usd, y10, geo, headline, "SIMULADO", List.of(), Map.of());
    }

    private void pickRegime(State s) {
        double u = s.rnd.nextDouble();
        if (u < 0.35) { s.regime = "BULL"; s.drift = 0.30; s.volMul = 0.8; s.ttl = 40 + s.rnd.nextInt(80); }
        else if (u < 0.65) { s.regime = "RANGE"; s.drift = 0; s.volMul = 0.7; s.ttl = 40 + s.rnd.nextInt(60); s.anchor = s.price; }
        else if (u < 0.88) { s.regime = "BEAR"; s.drift = -0.35; s.volMul = 1.2; s.ttl = 30 + s.rnd.nextInt(60); }
        else { s.regime = "STRESS"; s.drift = -1.2; s.volMul = 2.8; s.ttl = 6 + s.rnd.nextInt(10); }
    }

    private Candle step(State s) {
        if (s.ttl-- <= 0) pickRegime(s);
        double dv = s.ins.annVol() * s.volMul / Math.sqrt(252);
        double g = s.rnd.nextGaussian();
        double ret = s.drift / 252 + dv * g;
        if (s.regime.equals("RANGE")) ret += -0.06 * Math.log(s.price / s.anchor);
        double open = s.price;
        double close = open * Math.exp(ret);
        double hi = Math.max(open, close) * (1 + Math.abs(s.rnd.nextGaussian()) * dv * 0.35);
        double lo = Math.min(open, close) * (1 - Math.abs(s.rnd.nextGaussian()) * dv * 0.35);
        double vol = 1_000_000 * (0.8 + Math.abs(g) * 0.8) * (s.regime.equals("STRESS") ? 2 : 1);
        s.price = close;
        s.time += 86_400_000L;
        return new Candle(s.time, open, hi, lo, close, vol);
    }
}
