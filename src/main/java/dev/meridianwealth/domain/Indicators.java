package dev.meridianwealth.domain;

import java.util.Arrays;
import java.util.List;

/** Funções matemáticas puras, sem dependência de framework. */
public final class Indicators {
    private Indicators() {}

    public record Bands(double mid, double upper, double lower) {}
    public record Macd(double line, double signal, double hist, double prevHist) {}

    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public static double[] tail(double[] a, int k) {
        int n = Math.min(k, a.length);
        return Arrays.copyOfRange(a, a.length - n, a.length);
    }

    public static double sma(double[] x, int n) {
        double s = 0;
        for (int i = x.length - n; i < x.length; i++) s += x[i];
        return s / n;
    }

    public static double[] emaSeries(double[] x, int n) {
        double[] out = new double[x.length];
        double k = 2.0 / (n + 1);
        out[0] = x[0];
        for (int i = 1; i < x.length; i++) out[i] = x[i] * k + out[i - 1] * (1 - k);
        return out;
    }

    /** Desvio padrão amostral das últimas n observações. */
    public static double stdev(double[] x, int n) {
        double m = sma(x, n), s = 0;
        for (int i = x.length - n; i < x.length; i++) s += (x[i] - m) * (x[i] - m);
        return Math.sqrt(s / (n - 1));
    }

    public static double zScore(double[] x, int n) {
        double sd = stdev(x, n);
        return sd == 0 ? 0 : (x[x.length - 1] - sma(x, n)) / sd;
    }

    /** Posição do último valor na janela, 0..100 (fração de valores estritamente menores). */
    public static double percentRank(double[] x, int n) {
        double last = x[x.length - 1];
        int below = 0;
        for (int i = x.length - n; i < x.length; i++) if (x[i] < last) below++;
        return 100.0 * below / (n - 1);
    }

    /** RSI de Wilder. */
    public static double rsi(double[] c, int n) {
        double gain = 0, loss = 0;
        for (int i = 1; i <= n; i++) {
            double d = c[i] - c[i - 1];
            if (d >= 0) gain += d; else loss -= d;
        }
        gain /= n;
        loss /= n;
        for (int i = n + 1; i < c.length; i++) {
            double d = c[i] - c[i - 1];
            gain = (gain * (n - 1) + Math.max(d, 0)) / n;
            loss = (loss * (n - 1) + Math.max(-d, 0)) / n;
        }
        if (loss == 0) return 100;
        return 100 - 100 / (1 + gain / loss);
    }

    public static Bands bollinger(double[] x, int n, double k) {
        double m = sma(x, n), sd = stdev(x, n);
        return new Bands(m, m + k * sd, m - k * sd);
    }

    public static Macd macd(double[] x) {
        double[] e12 = emaSeries(x, 12), e26 = emaSeries(x, 26), line = new double[x.length];
        for (int i = 0; i < x.length; i++) line[i] = e12[i] - e26[i];
        double[] sig = emaSeries(line, 9);
        int n = x.length;
        return new Macd(line[n - 1], sig[n - 1], line[n - 1] - sig[n - 1], line[n - 2] - sig[n - 2]);
    }

    /** ATR simples (média do true range das últimas n barras). */
    public static double atr(List<Candle> cs, int n) {
        double s = 0;
        int sz = cs.size();
        for (int i = sz - n; i < sz; i++) {
            Candle c = cs.get(i), p = cs.get(i - 1);
            s += Math.max(c.high() - c.low(),
                    Math.max(Math.abs(c.high() - p.close()), Math.abs(c.low() - p.close())));
        }
        return s / n;
    }

    public static double[] logReturns(double[] c) {
        double[] r = new double[c.length - 1];
        for (int i = 1; i < c.length; i++) r[i - 1] = Math.log(c[i] / c[i - 1]);
        return r;
    }

    private static double std(double[] a) {
        if (a.length < 2) return 0;
        double m = Arrays.stream(a).average().orElse(0), s = 0;
        for (double v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / (a.length - 1));
    }

    public static double annualizedVol(double[] r, int perYear) { return std(r) * Math.sqrt(perYear); }

    /** Volatilidade realizada por raiz da média dos quadrados: capta choques direcionais (média ≠ 0). */
    public static double rmsVol(double[] r, int perYear) {
        double s = 0;
        for (double v : r) s += v * v;
        return Math.sqrt(s / r.length) * Math.sqrt(perYear);
    }

    public static double sharpe(double[] r, int perYear) {
        double sd = std(r);
        if (sd == 0) return 0;
        return Arrays.stream(r).average().orElse(0) / sd * Math.sqrt(perYear);
    }

    public static double maxDrawdown(double[] c) {
        double peak = c[0], mdd = 0;
        for (double v : c) {
            peak = Math.max(peak, v);
            mdd = Math.max(mdd, (peak - v) / peak);
        }
        return mdd;
    }

    /** VaR histórico (perda positiva) no nível de confiança dado. */
    public static double valueAtRisk(double[] r, double conf) {
        double[] s = r.clone();
        Arrays.sort(s);
        int idx = (int) Math.floor((1 - conf) * s.length);
        return Math.max(0, -s[Math.min(idx, s.length - 1)]);
    }

    /** Retorno do ativo menos retorno do benchmark nas últimas n barras (alinhadas pelo fim da série). */
    public static double relativeStrength(double[] asset, double[] bench, int n) {
        if (bench == null || asset.length <= n || bench.length <= n) return 0;
        double a = asset[asset.length - 1] / asset[asset.length - 1 - n] - 1;
        double b = bench[bench.length - 1] / bench[bench.length - 1 - n] - 1;
        return a - b;
    }

    /**
     * Beta do ativo contra o benchmark: covariância dos retornos logarítmicos dividida pela variância
     * dos retornos do benchmark, nas últimas {@code window} barras (séries alinhadas pelo fim). Devolve
     * {@code NaN} quando não há barras suficientes ou o benchmark não variou.
     */
    public static double beta(double[] asset, double[] bench, int window) {
        if (bench == null || window < 2 || asset.length < window + 1 || bench.length < window + 1) return Double.NaN;
        double[] ra = logReturns(tail(asset, window + 1));
        double[] rb = logReturns(tail(bench, window + 1));
        double ma = Arrays.stream(ra).average().orElse(0), mb = Arrays.stream(rb).average().orElse(0);
        double cov = 0, var = 0;
        for (int i = 0; i < ra.length; i++) {
            cov += (ra[i] - ma) * (rb[i] - mb);
            var += (rb[i] - mb) * (rb[i] - mb);
        }
        return var == 0 ? Double.NaN : cov / var;
    }

    /** Volume da última barra dividido pela média das n barras anteriores (1 quando não há volume). */
    public static double relativeVolume(List<Candle> cs, int n) {
        int sz = cs.size();
        if (sz <= n) return 1;
        double avg = 0;
        for (int i = sz - 1 - n; i < sz - 1; i++) avg += cs.get(i).volume();
        avg /= n;
        return avg <= 0 ? 1 : cs.get(sz - 1).volume() / avg;
    }

    /** Kelly fracionado: f* = p - (1-p)/b, escalado por {@code fraction} e limitado por {@code cap}. */
    public static double kelly(double p, double b, double fraction, double cap) {
        if (b <= 0) return 0;
        return clamp((p - (1 - p) / b) * fraction, 0, cap);
    }
}
