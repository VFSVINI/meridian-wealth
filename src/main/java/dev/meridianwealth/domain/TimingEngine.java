package dev.meridianwealth.domain;

import static dev.meridianwealth.domain.Indicators.clamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Motor de decisão e timing explicável. Barras são tratadas como diárias (252/ano).
 * Convenção do score: positivo = assimetria favorável à compra; negativo = esticamento / realizar.
 */
public final class TimingEngine {
    public static final int MIN_BARS = 120;
    public static final int DEFAULT_PERIODS_PER_YEAR = 252;
    public static final int BETA_WINDOW = 90;

    public Assessment assess(String symbol, List<Candle> candles, double macroRisk) {
        return assess(symbol, candles, macroRisk, null);
    }

    public Assessment assess(String symbol, List<Candle> candles, double macroRisk, double[] benchmark) {
        return assess(symbol, candles, macroRisk, benchmark, DEFAULT_PERIODS_PER_YEAR);
    }

    /**
     * {@code benchmark}: fechamentos do índice de referência alinhados às barras do ativo, para força
     * relativa e beta (pode ser nulo). {@code perYear}: barras por ano para anualizar volatilidade e
     * Sharpe (252 para ações; 365 para cripto, que negocia todos os dias).
     */
    public Assessment assess(String symbol, List<Candle> candles, double macroRisk, double[] benchmark, int perYear) {
        if (perYear <= 0) throw new IllegalArgumentException("perYear deve ser positivo");
        int n = candles.size();
        if (n < MIN_BARS) throw new IllegalArgumentException("Mínimo de " + MIN_BARS + " barras");
        double[] c = new double[n];
        for (int i = 0; i < n; i++) c[i] = candles.get(i).close();
        double last = c[n - 1];

        double z = Indicators.zScore(c, 50);
        double pct = Indicators.percentRank(c, Math.min(250, n));
        double rsi = Indicators.rsi(c, 14);
        Indicators.Macd macd = Indicators.macd(c);
        Indicators.Bands bb = Indicators.bollinger(c, 20, 2.0);
        double sma50 = Indicators.sma(c, 50), sma100 = Indicators.sma(c, 100);
        double atr = Indicators.atr(candles, 14);
        double[] r = Indicators.logReturns(c);
        double[] r60 = Indicators.tail(r, 60), r120 = Indicators.tail(r, 120);
        double rmsLong = Indicators.rmsVol(r60, perYear);
        double ratio = rmsLong == 0 ? 1 : Indicators.rmsVol(Indicators.tail(r, 5), perYear) / rmsLong;
        double vol = Indicators.annualizedVol(r60, perYear);
        double mdd = Indicators.maxDrawdown(Indicators.tail(c, Math.min(250, n)));
        double sharpe = Indicators.sharpe(r120, perYear);
        double var95 = Indicators.valueAtRisk(r120, 0.95);
        double ret5 = last / c[n - 6] - 1;
        double rs = Indicators.relativeStrength(c, benchmark, 60);
        double relVol = Indicators.relativeVolume(candles, 20);
        double betaValue = Indicators.beta(c, benchmark, BETA_WINDOW);

        // --- Regime ---
        Regime regime;
        if (ratio > 1.8 || macroRisk > 0.8) regime = Regime.STRESS;
        else if (last > sma50 && sma50 > sma100) regime = Regime.UPTREND;
        else if (last < sma50 && sma50 < sma100) regime = Regime.DOWNTREND;
        else regime = Regime.RANGE;

        // --- Faca caindo: queda livre com volatilidade explosiva ou choque macro agudo ---
        boolean knife = (z < -1.5 && ratio > 1.8 && ret5 < -0.03)
                || (regime == Regime.DOWNTREND && z < -2.0 && macd.hist() < macd.prevHist())
                || (macroRisk > 0.85 && z < -1.0);

        // --- Score (componentes explícitos e somados) ---
        double sZ = -clamp(z, -3, 3) / 3 * 35;
        double sR = (50 - rsi) / 50 * 20;
        double sP = (50 - pct) / 50 * 20;
        double sM = macd.hist() < 0 && macd.hist() > macd.prevHist() ? 10
                : macd.hist() > 0 && macd.hist() < macd.prevHist() ? -10 : 0;
        double sT = regime == Regime.UPTREND ? 5 : regime == Regime.DOWNTREND ? -5 : 0;
        double sRS = benchmark == null ? 0 : clamp(rs / 0.10, -1, 1) * 5;
        double raw = sZ + sR + sP + sM + sT + sRS - macroRisk * 15;
        if (regime == Regime.UPTREND && raw < 0) raw *= 0.7;     // não vender cedo demais em tendência
        if (regime == Regime.DOWNTREND && raw > 0) raw *= 0.7;   // desconto de compra em tendência ruim
        int score = (int) Math.round(clamp(raw, -100, 100));

        Signal signal;
        Phase phase;
        if (knife) { signal = Signal.HIGH_RISK_WAIT; phase = Phase.CAUTELA_EVITAR; }
        else if (score >= 45) { signal = Signal.BUY_DIP_WINDOW; phase = Phase.ACUMULACAO; }
        else if (score >= 20) { signal = Signal.ACCUMULATE; phase = Phase.ACUMULACAO; }
        else if (score > -20) {
            if (regime == Regime.UPTREND) { signal = Signal.HOLD_RIDE_TREND; phase = Phase.TENDENCIA_ATIVA; }
            else { signal = Signal.HIGH_RISK_WAIT; phase = Phase.CAUTELA_EVITAR; }
        }
        else if (score > -45) { signal = Signal.TAKE_PROFIT_WINDOW; phase = Phase.ESTICADO_REALIZAR; }
        else { signal = Signal.SELL_EXIT_SETUP; phase = Phase.ESTICADO_REALIZAR; }

        // --- Confiança: concordância entre componentes + penalidades ---
        int dir = Integer.signum(score);
        int agree = 0;
        for (double s : new double[] {sZ, sR, sP, sM}) if (dir != 0 && Math.signum(s) == dir) agree++;
        double conf = 40 + 15 * agree - macroRisk * 10 - (regime == Regime.STRESS ? 20 : 0);
        int confidence = (int) Math.round(clamp(conf, 0, 100));

        // --- Risco ---
        double riskIdx = Math.min(1, vol / 0.6) * 0.5 + macroRisk * 0.3 + Math.min(1, var95 / 0.04) * 0.2;
        RiskClass risk = riskIdx < 0.25 ? RiskClass.LOW : riskIdx < 0.5 ? RiskClass.MEDIUM
                : riskIdx < 0.75 ? RiskClass.HIGH : RiskClass.EXTREME;

        // --- Zonas, assimetria e expectativa matemática ---
        double entryHigh = last + 0.25 * atr;
        double entryLow = Math.max(last - atr, Math.min(bb.lower(), last));
        double invalidation = last - 2.0 * atr;
        double target1 = Math.max(bb.mid(), last + atr);
        double target2 = Math.max(bb.upper(), target1 + atr);
        double upside = (target2 - last) / last, downside = (last - invalidation) / last;
        double rr = downside > 0 ? upside / downside : 0;
        double pUp = clamp(0.5 + score / 300.0, 0.25, 0.75);
        double ev = pUp * upside - (1 - pUp) * downside;
        double exposure = score > 0 && !knife ? Indicators.kelly(pUp, rr, 0.25, 0.10) : 0;

        return new Assessment(symbol, last, score, signal, phase, regime, confidence, risk,
                z, pct, rsi, macd.hist(), vol, mdd, sharpe, var95, atr,
                sma50, entryLow, entryHigh, invalidation, target1, target2,
                upside, downside, rr, ev, exposure, rs, relVol,
                factors(z, pct, rsi, macd, bb, last, regime, knife, ratio, macroRisk, score, rs, relVol, benchmark != null),
                Double.isNaN(betaValue) ? null : betaValue, perYear);
    }

    private static List<Factor> factors(double z, double pct, double rsi, Indicators.Macd m,
                                        Indicators.Bands bb, double last, Regime regime,
                                        boolean knife, double ratio, double macroRisk, int score,
                                        double rs, double relVol, boolean hasBench) {
        List<Factor> f = new ArrayList<>();
        Locale l = Locale.ROOT;
        if (z < -1.5) f.add(new Factor("COMPRA", String.format(l, "Z-Score %.2f: preço %.1fσ abaixo da média de 50 barras (desconto estatístico)", z, -z)));
        if (z > 1.5) f.add(new Factor("VENDA", String.format(l, "Z-Score %.2f: preço esticado %.1fσ acima da média de 50 barras", z, z)));
        if (rsi < 30) f.add(new Factor("COMPRA", String.format(l, "RSI(14) em %.0f: região de sobrevenda", rsi)));
        if (rsi > 70) f.add(new Factor("VENDA", String.format(l, "RSI(14) em %.0f: momentum esticado", rsi)));
        if (pct < 20) f.add(new Factor("COMPRA", String.format(l, "Percentil %.0f%% na janela: perto das mínimas do ciclo", pct)));
        if (pct > 80) f.add(new Factor("VENDA", String.format(l, "Percentil %.0f%% na janela: perto das máximas do ciclo", pct)));
        if (m.hist() < 0 && m.hist() > m.prevHist()) f.add(new Factor("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"));
        if (m.hist() > 0 && m.hist() < m.prevHist()) f.add(new Factor("VENDA", "MACD: força compradora diminuindo (histograma caindo)"));
        if (last <= bb.lower()) f.add(new Factor("COMPRA", "Preço tocou a banda inferior de Bollinger (20, 2σ)"));
        if (last >= bb.upper()) f.add(new Factor("VENDA", "Preço tocou a banda superior de Bollinger (20, 2σ)"));
        if (regime == Regime.UPTREND && score < 0) f.add(new Factor("ALERTA", "Tendência de alta ativa: realizar cedo demais pode cortar lucro; score de venda foi atenuado"));
        if (regime == Regime.DOWNTREND && score > 0) f.add(new Factor("ALERTA", "Tendência de baixa: score de compra foi atenuado (queda pode continuar)"));
        if (knife) f.add(new Factor("ALERTA", String.format(l, "Faca caindo: queda acelerada com volatilidade %.1fx a normal ou choque macro; aguardar estabilização", ratio)));
        else if (regime == Regime.STRESS) f.add(new Factor("ALERTA", "Regime de estresse: volatilidade de curto prazo muito acima da normal"));
        if (macroRisk > 0.5) f.add(new Factor("ALERTA", String.format(l, "Risco macro/geopolítico elevado (%.0f/100) reduz o score e a confiança", macroRisk * 100)));
        if (hasBench && rs > 0.05) f.add(new Factor("COMPRA", String.format(l, "Força relativa: %+.1f pp acima do benchmark em 60 barras", rs * 100)));
        if (hasBench && rs < -0.05) f.add(new Factor("ALERTA", String.format(l, "Força relativa fraca: %+.1f pp frente ao benchmark em 60 barras", rs * 100)));
        if (relVol > 1.8 && z < -1) f.add(new Factor("ALERTA", String.format(l, "Volume %.1fx a média em queda: capitulação ou distribuição, confirmar estabilização", relVol)));
        if (f.isEmpty()) f.add(new Factor("ALERTA", "Sem gatilhos relevantes: preço próximo do valor justo estatístico"));
        return f;
    }
}
