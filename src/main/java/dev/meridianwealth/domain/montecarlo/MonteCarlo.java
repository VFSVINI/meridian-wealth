package dev.meridianwealth.domain.montecarlo;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Motor de Monte Carlo do patrimônio: uma <b>projeção</b>, nunca uma cotação. Simula {@link #PATHS} trajetórias mês a mês,
 * {@code valor = valor x (1 + retorno do mês) + aporte}, e resume cada mês em percentis (o cone patrimonial), o patrimônio final em
 * histograma e a probabilidade de atingir uma meta.
 *
 * <p>O retorno do mês vem de um {@link ReturnModel}: log-normal a partir de premissas informadas ({@link ParametricModel}) ou
 * reamostragem de retornos reais ({@link BootstrapModel}). A aleatoriedade é um {@link SplittableRandom} com <b>semente explícita</b>:
 * a mesma semente e as mesmas entradas dão exatamente o mesmo resultado (reprodutível e testável). É a única exceção à regra de
 * "sem aleatoriedade no produto" do ADR 004, justificada no ADR 020: aqui o sorteio não gera preço nenhum, gera cenários rotulados.
 *
 * <p>É estatística, não contabilidade: o cálculo usa {@code double}; o dinheiro exato (BigDecimal, ADR 002) continua sendo o da
 * carteira e do livro.
 */
public final class MonteCarlo {
    public static final int PATHS = 5000;
    public static final int MAX_MONTHS = 40 * 12;
    public static final int BINS = 30;
    /** Teto do que o cálculo representa (R$ 100 quintilhões): premissas que o ultrapassam são recusadas em vez de virarem infinito ou NaN. */
    public static final double MAX_VALUE = 1e20;
    public static final double[] LEVELS = {0.05, 0.25, 0.50, 0.75, 0.95};

    /** Retorno de um mês (por exemplo 0,012 = +1,2%), sorteado com o gerador recebido. */
    public interface ReturnModel {
        double nextMonthlyReturn(SplittableRandom random);
    }

    /**
     * @param initial patrimônio inicial
     * @param monthlyContribution aporte no fim de cada mês (rende a partir do mês seguinte)
     * @param months horizonte em meses (1 a {@link #MAX_MONTHS})
     * @param goal meta de patrimônio, ou {@code null}
     * @param seed semente do gerador
     */
    public record Params(double initial, double monthlyContribution, int months, Double goal, long seed) {
        public Params {
            if (!(initial >= 0) || !Double.isFinite(initial)) throw new IllegalArgumentException("Patrimônio inicial inválido");
            if (!(monthlyContribution >= 0) || !Double.isFinite(monthlyContribution)) throw new IllegalArgumentException("Aporte mensal inválido");
            if (months < 1 || months > MAX_MONTHS) throw new IllegalArgumentException("O horizonte vai de 1 mês a " + MAX_MONTHS / 12 + " anos");
            if (goal != null && (!(goal >= 0) || !Double.isFinite(goal))) throw new IllegalArgumentException("Meta inválida");
            if (initial == 0 && monthlyContribution == 0) throw new IllegalArgumentException("Informe um patrimônio inicial ou um aporte mensal");
        }
    }

    /**
     * @param percentiles {@code percentiles[l][m]}: percentil {@link #LEVELS}{@code [l]} do patrimônio no mês {@code m} (0 = hoje)
     * @param histogramEdges {@code BINS + 1} limites das faixas do patrimônio final
     * @param histogramCounts quantas trajetórias terminaram em cada faixa
     * @param goalProbabilityFinal probabilidade de terminar na meta ou acima, ou {@code null} sem meta
     * @param goalProbabilityByYear probabilidade de estar na meta ou acima no fim de cada ano ({@code [0]} é hoje), ou {@code null}
     * @param probabilityBelowInvested probabilidade de terminar abaixo de tudo que foi investido (inicial mais aportes)
     * @param totalInvested inicial mais todos os aportes
     */
    public record Result(int months, double[][] percentiles, double[] histogramEdges, int[] histogramCounts, Double goalProbabilityFinal,
                         double[] goalProbabilityByYear, double probabilityBelowInvested, double totalInvested, double mean) {}

    private MonteCarlo() {}

    public static Result run(Params p, ReturnModel model) {
        SplittableRandom random = new SplittableRandom(p.seed());
        double[] value = new double[PATHS];
        Arrays.fill(value, p.initial());
        double[][] pct = new double[LEVELS.length][p.months() + 1];
        double[] goalByYear = p.goal() == null ? null : new double[p.months() / 12 + 1];
        record0(value, pct, 0, p.goal(), goalByYear, 0);
        for (int m = 1; m <= p.months(); m++) {
            for (int i = 0; i < PATHS; i++) {
                double r = model.nextMonthlyReturn(random);
                if (r < -1) r = -1; // um mês não perde mais que tudo
                value[i] = Math.max(0, value[i] * (1 + r)) + p.monthlyContribution();
                if (!(value[i] <= MAX_VALUE)) throw new IllegalArgumentException("As premissas levam o patrimônio além do que o cálculo representa: reduza o retorno, a volatilidade ou o horizonte");
            }
            record0(value, pct, m, p.goal(), goalByYear, m % 12 == 0 ? m / 12 : -1);
        }
        return summarize(p, value, pct, goalByYear);
    }

    private static void record0(double[] value, double[][] pct, int month, Double goal, double[] goalByYear, int year) {
        double[] s = value.clone();
        Arrays.sort(s);
        for (int l = 0; l < LEVELS.length; l++) pct[l][month] = quantile(s, LEVELS[l]);
        if (goalByYear != null && year >= 0) goalByYear[year] = shareAtOrAbove(s, goal);
    }

    private static Result summarize(Params p, double[] value, double[][] pct, double[] goalByYear) {
        double[] s = value.clone();
        Arrays.sort(s);
        if (!(s[0] >= 0 && s[s.length - 1] <= MAX_VALUE)) throw new IllegalArgumentException("Resultado fora do intervalo que o cálculo representa");
        double invested = p.initial() + p.monthlyContribution() * p.months();
        double lo = s[0], hi = quantile(s, 0.99);
        if (hi <= lo) hi = lo + 1;
        double[] edges = new double[BINS + 1];
        for (int b = 0; b <= BINS; b++) edges[b] = lo + (hi - lo) * b / BINS;
        int[] counts = new int[BINS];
        double sum = 0;
        int below = 0;
        for (double v : value) {
            sum += v;
            int b = (int) ((v - lo) / (hi - lo) * BINS);
            counts[Math.min(BINS - 1, Math.max(0, b))]++;
            if (v < invested) below++;
        }
        Double goalFinal = p.goal() == null ? null : shareAtOrAbove(s, p.goal());
        return new Result(p.months(), pct, edges, counts, goalFinal, goalByYear, (double) below / PATHS, invested, sum / PATHS);
    }

    /** Quantil por interpolação linear entre as duas ordens vizinhas (vetor já ordenado). */
    static double quantile(double[] sorted, double q) {
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    private static double shareAtOrAbove(double[] sorted, double goal) {
        int idx = Arrays.binarySearch(sorted, goal);
        int first;
        if (idx >= 0) {
            first = idx;
            while (first > 0 && sorted[first - 1] >= goal) first--;
        } else {
            first = -idx - 1;
        }
        return (double) (sorted.length - first) / sorted.length;
    }

    /**
     * Retorno log-normal a partir de premissas anuais: o retorno esperado ({@code expectedAnnual}, por exemplo 0,105) e a volatilidade
     * ({@code volatilityAnnual}, por exemplo 0,14). Por mês, {@code ln(1 + r) = (mu - sigma²/2) dt + sigma sqrt(dt) Z}, com {@code dt = 1/12}
     * e {@code Z} normal padrão (Box-Muller), de modo que o retorno esperado composto bate com {@code mu}.
     */
    public static final class ParametricModel implements ReturnModel {
        private final double drift;
        private final double diffusion;

        public ParametricModel(double expectedAnnual, double volatilityAnnual) {
            if (!(expectedAnnual > -1) || !Double.isFinite(expectedAnnual)) throw new IllegalArgumentException("Retorno esperado inválido");
            if (!(volatilityAnnual >= 0) || !Double.isFinite(volatilityAnnual)) throw new IllegalArgumentException("Volatilidade inválida");
            double mu = Math.log1p(expectedAnnual); // log-retorno esperado composto; o termo de Itô é compensado abaixo
            this.drift = (mu - volatilityAnnual * volatilityAnnual / 2) / 12;
            this.diffusion = volatilityAnnual / Math.sqrt(12);
        }

        @Override
        public double nextMonthlyReturn(SplittableRandom random) {
            double u1 = 1 - random.nextDouble(), u2 = random.nextDouble();
            double z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
            return Math.expm1(drift + diffusion * z);
        }
    }

    /**
     * Reamostragem de retornos <b>reais</b> por blocos: sorteia uma janela de {@code blockDays} pregões consecutivos do histórico
     * <b>alinhado por data</b> e compõe o retorno de cada ativo na janela. Como todos os ativos usam a mesma janela, a correlação
     * entre eles no passado é preservada. Os pesos são fixos (a carteira é rebalanceada todo mês); a fração dos pesos que não entra
     * na matriz (posição sem histórico) fica parada, sem render.
     */
    public static final class BootstrapModel implements ReturnModel {
        private final double[] weights;
        private final double[][] dailyReturns;
        private final int blockDays;
        private final int starts;

        /** @param weights peso de cada ativo sobre o patrimônio TOTAL (a soma pode ser menor que 1) */
        public BootstrapModel(double[] weights, double[][] dailyReturns, int blockDays) {
            if (weights.length == 0 || weights.length != dailyReturns.length) throw new IllegalArgumentException("Pesos e retornos precisam ter o mesmo tamanho");
            int n = dailyReturns[0].length;
            for (double[] r : dailyReturns) {
                if (r.length != n) throw new IllegalArgumentException("Os retornos precisam estar alinhados por data");
            }
            if (n < blockDays + 1) throw new IllegalArgumentException("Histórico em comum curto demais para blocos de " + blockDays + " pregões");
            this.weights = weights.clone();
            this.dailyReturns = dailyReturns;
            this.blockDays = blockDays;
            this.starts = n - blockDays + 1;
        }

        @Override
        public double nextMonthlyReturn(SplittableRandom random) {
            int start = random.nextInt(starts);
            double total = 0;
            for (int a = 0; a < weights.length; a++) {
                double growth = 1;
                double[] r = dailyReturns[a];
                for (int d = start; d < start + blockDays; d++) growth *= 1 + r[d];
                total += weights[a] * (growth - 1);
            }
            return total;
        }
    }
}
