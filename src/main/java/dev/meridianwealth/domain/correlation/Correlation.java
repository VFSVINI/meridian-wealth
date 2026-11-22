package dev.meridianwealth.domain.correlation;

import dev.meridianwealth.domain.montecarlo.AlignedReturns;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Correlação de Pearson entre retornos diários <b>reais</b>, par a par, e o risco da carteira com a correlação. Cada par usa as datas que
 * os <b>dois</b> ativos têm (um ativo curto não derruba os outros pares), nos últimos {@link #WINDOW} retornos. Um par com menos de
 * {@link #MIN_DAYS} pregões em comum fica <b>sem valor</b> ({@code null}): nunca zero e nunca estimado.
 */
public final class Correlation {
    public static final int WINDOW = 252;
    public static final int MIN_DAYS = 60;
    public static final double TRADING_DAYS = 252;

    /** @param values {@code values[i][j]}: correlação entre as chaves i e j, ou {@code null} sem dado suficiente; @param days pregões usados em cada par */
    public record Matrix(List<String> keys, Double[][] values, int[][] days) {}

    /** @param portfolioVolatility volatilidade anualizada da carteira com a correlação; @param weightedAverageVolatility média ponderada das volatilidades individuais */
    public record PortfolioRisk(int days, double portfolioVolatility, double weightedAverageVolatility, double diversificationBenefit) {}

    private Correlation() {}

    /** Matriz de correlação entre as séries de fechamento (dia da época, UTC, para fechamento). */
    public static Matrix pairwise(Map<String, TreeMap<Long, Double>> closes) {
        List<String> keys = new ArrayList<>(closes.keySet());
        int n = keys.size();
        Double[][] values = new Double[n][n];
        int[][] days = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double[][] pair = returnsOnCommonDates(closes.get(keys.get(i)), closes.get(keys.get(j)));
                int d = pair[0].length;
                Double r = d >= MIN_DAYS ? (i == j ? Double.valueOf(1.0) : pearson(pair[0], pair[1])) : null;
                values[i][j] = r;
                values[j][i] = r;
                days[i][j] = d;
                days[j][i] = d;
            }
        }
        return new Matrix(List.copyOf(keys), values, days);
    }

    /** Retornos das duas séries entre datas consecutivas em comum, só os últimos {@link #WINDOW}. */
    static double[][] returnsOnCommonDates(TreeMap<Long, Double> a, TreeMap<Long, Double> b) {
        TreeSet<Long> common = new TreeSet<>(a.keySet());
        common.retainAll(b.keySet());
        Long[] dates = common.toArray(Long[]::new);
        int from = Math.max(1, dates.length - WINDOW);
        int n = Math.max(0, dates.length - from);
        double[] ra = new double[n], rb = new double[n];
        for (int d = from; d < dates.length; d++) {
            ra[d - from] = a.get(dates[d]) / a.get(dates[d - 1]) - 1;
            rb[d - from] = b.get(dates[d]) / b.get(dates[d - 1]) - 1;
        }
        return new double[][] {ra, rb};
    }

    /** Correlação de Pearson; {@code null} se uma das séries não varia (a correlação não é definida), nunca zero. */
    public static Double pearson(double[] a, double[] b) {
        int n = a.length;
        if (n < 2 || b.length != n) return null;
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) {
            ma += a[i];
            mb += b[i];
        }
        ma /= n;
        mb /= n;
        double sab = 0, saa = 0, sbb = 0;
        for (int i = 0; i < n; i++) {
            double da = a[i] - ma, db = b[i] - mb;
            sab += da * db;
            saa += da * da;
            sbb += db * db;
        }
        if (saa <= 1e-24 || sbb <= 1e-24) return null;
        return Math.max(-1, Math.min(1, sab / Math.sqrt(saa * sbb)));
    }

    /**
     * Risco da carteira de pesos fixos sobre os retornos alinhados de todos os ativos (os últimos {@link #WINDOW} dias): a volatilidade
     * da carteira (que carrega a correlação) contra a média ponderada das volatilidades individuais (o limite superior que ignora a
     * correlação). A diferença é o benefício de diversificação, nunca negativa.
     *
     * @param weights pesos dos ativos entre si (somam 1)
     */
    public static PortfolioRisk portfolioRisk(AlignedReturns aligned, double[] weights) {
        int total = aligned.days(), from = Math.max(0, total - WINDOW), n = total - from;
        if (n < 2) throw new IllegalArgumentException("Histórico em comum curto demais para medir o risco da carteira");
        double[] port = new double[n];
        double weighted = 0;
        for (int a = 0; a < weights.length; a++) {
            double[] r = new double[n];
            for (int d = 0; d < n; d++) {
                r[d] = aligned.returns()[a][from + d];
                port[d] += weights[a] * r[d];
            }
            weighted += weights[a] * annualVol(r);
        }
        double vol = annualVol(port);
        return new PortfolioRisk(n, vol, weighted, Math.max(0, weighted - vol));
    }

    static double annualVol(double[] daily) {
        double mean = 0;
        for (double d : daily) mean += d;
        mean /= daily.length;
        double var = 0;
        for (double d : daily) var += (d - mean) * (d - mean);
        return Math.sqrt(var / (daily.length - 1)) * Math.sqrt(TRADING_DAYS);
    }
}
