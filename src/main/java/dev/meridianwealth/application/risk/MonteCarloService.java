package dev.meridianwealth.application.risk;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.montecarlo.AlignedReturns;
import dev.meridianwealth.domain.montecarlo.MonteCarlo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Projeção patrimonial por Monte Carlo (item #18), sempre rotulada <b>projeção</b>: não é cotação nem promessa de retorno.
 *
 * <p>Dois caminhos: {@link #parametric} usa as premissas que a pessoa informa (retorno e volatilidade anuais, log-normal) e não toca em
 * dado pessoal; {@link #fromPortfolio} usa a carteira salva e <b>reamostra retornos reais</b> dos ativos alinhados por data, o que
 * preserva a correlação entre eles. Posição sem histórico real (renda fixa, fundos, ativo sem cotação) fica <b>parada</b> no valor atual,
 * sem render, e a saída diz qual e por quê: nada é estimado nem preenchido.
 */
public final class MonteCarloService {
    public static final int HISTORY_BARS = 500;
    public static final int MIN_BARS = 121;
    public static final int MIN_COMMON_DAYS = 120;
    public static final int BLOCK_DAYS = 21;

    /** Uma posição da carteira valorizada em reais, como a fonte de carteira a entrega. */
    public record Holding(String assetKey, String label, String symbol, String currency, BigDecimal valueBrl) {}

    /** Porta de leitura da carteira do usuário (a ligação com o {@code PortfolioService} fica na configuração). */
    public interface PortfolioSource {
        List<Holding> holdings(AuthPrincipal who);
    }

    /** Porta de leitura do histórico real de um ativo do painel; devolve vazio quando não há dado real agora. */
    public interface HistorySource {
        List<Candle> candles(String symbol, int limit);
    }

    public record Request(BigDecimal initial, BigDecimal monthlyContribution, int horizonYears, BigDecimal expectedReturnPercent, BigDecimal volatilityPercent,
                          BigDecimal goal, Long seed) {}

    /** O que cada posição virou na projeção. {@code treatment}: HISTORICO (reamostrada) ou CONSTANTE (parada no valor atual). */
    public record Coverage(String label, String assetKey, BigDecimal valueBrl, double weightPercent, String treatment, String reason, String currency) {}

    /** @param statistics dados do histórico usado, só no modo carteira */
    public record Output(boolean parametric, String label, MonteCarlo.Result result, double initial, double monthlyContribution, int months, Double goal, long seed,
                         double expectedReturnAnnual, double volatilityAnnual, List<Coverage> coverage, Double coveredPercent, Statistics statistics, List<String> assumptions) {}

    /** Estatísticas dos retornos reais usados: janela, dias em comum e retorno e volatilidade anualizados da parte com histórico. */
    public record Statistics(int commonDays, LocalDate from, LocalDate to, double annualReturn, double annualVolatility) {}

    private final PortfolioSource portfolio;
    private final HistorySource history;

    public MonteCarloService(PortfolioSource portfolio, HistorySource history) {
        this.portfolio = portfolio;
        this.history = history;
    }

    public Output parametric(Request r) {
        BigDecimal initial = nz(r.initial());
        BigDecimal monthly = nz(r.monthlyContribution());
        double mu = percent(r.expectedReturnPercent(), "Retorno esperado", -50, 100);
        double sigma = percent(r.volatilityPercent(), "Volatilidade", 0, 200);
        int months = months(r.horizonYears());
        MonteCarlo.Params p = new MonteCarlo.Params(initial.doubleValue(), monthly.doubleValue(), months, goal(r.goal()), seed(r, "P", initial.doubleValue(), monthly.doubleValue(), months, mu, sigma));
        MonteCarlo.Result res = MonteCarlo.run(p, new MonteCarlo.ParametricModel(mu, sigma));
        return new Output(true, LABEL, res, p.initial(), p.monthlyContribution(), months, p.goal(), p.seed(), mu, sigma, List.of(), null, null,
                List.of("Premissas informadas por você: retorno esperado de " + pct(mu) + " ao ano e volatilidade de " + pct(sigma) + " ao ano, constantes no horizonte.",
                        "Retornos log-normais mensais; o aporte entra no fim de cada mês e rende a partir do seguinte.", COMMON_1, COMMON_2, COMMON_3));
    }

    public Output fromPortfolio(AuthPrincipal who, Request r) {
        List<Holding> all = portfolio.holdings(who).stream().filter(h -> h.valueBrl() != null && h.valueBrl().signum() > 0).toList();
        if (all.isEmpty()) throw new MonteCarloException("A carteira não tem posição com valor em reais para projetar. Registre posições na Minha Carteira ou use as premissas manuais.");
        BigDecimal totalBd = all.stream().map(Holding::valueBrl).reduce(BigDecimal.ZERO, BigDecimal::add);
        double total = totalBd.doubleValue();
        BigDecimal monthly = nz(r.monthlyContribution());
        int months = months(r.horizonYears());

        Map<String, TreeMap<Long, Double>> closes = new LinkedHashMap<>();
        Map<String, String> why = new LinkedHashMap<>();
        for (Holding h : all) {
            if (h.symbol() == null) {
                why.put(h.assetKey(), "Sem cotação nem histórico: o tipo não é negociado em mercado.");
                continue;
            }
            List<Candle> bars;
            try {
                bars = history.candles(h.symbol(), HISTORY_BARS);
            } catch (RuntimeException e) {
                why.put(h.assetKey(), "Sem dado real de mercado agora.");
                continue;
            }
            TreeMap<Long, Double> byDay = new TreeMap<>();
            for (Candle c : bars) {
                if (c.close() > 0) byDay.put(Math.floorDiv(c.time(), 86_400_000L), c.close());
            }
            if (byDay.size() < MIN_BARS) why.put(h.assetKey(), "Histórico curto: " + byDay.size() + " pregões (mínimo " + MIN_BARS + ").");
            else closes.put(h.assetKey(), byDay);
        }
        AlignedReturns aligned = align(closes, why);
        if (aligned == null) {
            throw new MonteCarloException("Nenhuma posição tem histórico real em comum suficiente (mínimo " + MIN_COMMON_DAYS + " pregões) para reamostrar. Use as premissas manuais.");
        }
        Map<String, Holding> byKey = new LinkedHashMap<>();
        all.forEach(h -> byKey.put(h.assetKey(), h));
        double[] weights = new double[aligned.symbols().size()];
        double coveredValue = 0;
        for (int i = 0; i < weights.length; i++) {
            double v = byKey.get(aligned.symbols().get(i)).valueBrl().doubleValue();
            weights[i] = v / total;
            coveredValue += v;
        }
        double[] coveredWeights = new double[weights.length];
        for (int i = 0; i < weights.length; i++) coveredWeights[i] = weights[i] * total / coveredValue;
        double[] daily = aligned.portfolioDaily(coveredWeights);
        Statistics stats = statistics(daily, aligned);

        List<Coverage> coverage = new ArrayList<>();
        for (Holding h : all) {
            boolean used = aligned.symbols().contains(h.assetKey());
            coverage.add(new Coverage(h.label(), h.assetKey(), h.valueBrl(), h.valueBrl().doubleValue() / total * 100, used ? "HISTORICO" : "CONSTANTE",
                    used ? "Retornos reais reamostrados (" + aligned.days() + " pregões em comum)." : why.getOrDefault(h.assetKey(), "Fora do histórico em comum."), h.currency()));
        }
        List<String> assumptions = new ArrayList<>(List.of(
                "Reamostragem de blocos de " + BLOCK_DAYS + " pregões de retornos reais de " + aligned.days() + " dias em comum (" + stats.from() + " a " + stats.to()
                        + "), alinhados por data: a correlação entre os ativos no passado é preservada.",
                "Pesos fixos pelo valor atual em reais, rebalanceados todo mês; o aporte segue a mesma alocação.",
                "Posição sem histórico real fica parada no valor atual, sem render: " + String.format("%.1f%%", 100 - coveredValue / total * 100) + " do patrimônio."));
        if (all.stream().anyMatch(h -> "USD".equals(h.currency()) && aligned.symbols().contains(h.assetKey()))) {
            assumptions.add("Ativos em dólar entram pelo retorno na moeda do ativo: a variação do câmbio não entra na projeção.");
        }
        assumptions.addAll(List.of(COMMON_1, COMMON_2, COMMON_3));
        MonteCarlo.Params p = new MonteCarlo.Params(total, monthly.doubleValue(), months, goal(r.goal()),
                r.seed() != null ? r.seed() : Objects.hash(who.userId(), months, monthly.doubleValue(), aligned.days(), Math.round(total)));
        MonteCarlo.Result res = MonteCarlo.run(p, new MonteCarlo.BootstrapModel(weights, aligned.returns(), BLOCK_DAYS));
        return new Output(false, LABEL, res, total, p.monthlyContribution(), months, p.goal(), p.seed(), stats.annualReturn(), stats.annualVolatility(), coverage,
                coveredValue / total * 100, stats, assumptions);
    }

    /** Alinha os ativos com histórico; enquanto a janela em comum for curta demais, tira o ativo de menor histórico e anota o motivo. */
    private static AlignedReturns align(Map<String, TreeMap<Long, Double>> closes, Map<String, String> why) {
        Map<String, TreeMap<Long, Double>> work = new LinkedHashMap<>(closes);
        while (!work.isEmpty()) {
            AlignedReturns a = AlignedReturns.of(work);
            if (a != null && a.days() >= MIN_COMMON_DAYS) return a;
            String shortest = work.entrySet().stream().min(java.util.Map.Entry.comparingByValue(java.util.Comparator.comparingInt(TreeMap::size))).orElseThrow().getKey();
            why.put(shortest, "Histórico em comum com os outros ativos curto demais (mínimo " + MIN_COMMON_DAYS + " pregões): ficou de fora da reamostragem.");
            work.remove(shortest);
        }
        return null;
    }

    private static Statistics statistics(double[] daily, AlignedReturns aligned) {
        double growth = 1, mean = 0;
        for (double d : daily) {
            growth *= 1 + d;
            mean += d;
        }
        mean /= daily.length;
        double var = 0;
        for (double d : daily) var += (d - mean) * (d - mean);
        double sd = Math.sqrt(var / (daily.length - 1));
        long[] dates = aligned.dates();
        return new Statistics(daily.length, LocalDate.ofEpochDay(dates[0]), LocalDate.ofEpochDay(dates[dates.length - 1]), Math.pow(growth, 252.0 / daily.length) - 1, sd * Math.sqrt(252));
    }

    static final String LABEL = "PROJEÇÃO: não é cotação nem promessa de retorno. A plataforma não executa ordens.";
    private static final String COMMON_1 = "Sem impostos, taxas, custos nem inflação: os valores são nominais, em reais.";
    private static final String COMMON_2 = "5.000 trajetórias; os percentis resumem as trajetórias simuladas, não o que vai acontecer.";
    private static final String COMMON_3 = "O resultado é reprodutível: a mesma entrada e a mesma semente dão a mesma projeção.";

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static Double goal(BigDecimal g) { return g == null ? null : g.doubleValue(); }

    private static int months(int years) {
        if (years < 1 || years > 40) throw new IllegalArgumentException("O horizonte vai de 1 a 40 anos");
        return years * 12;
    }

    private static double percent(BigDecimal v, String name, double min, double max) {
        if (v == null) throw new IllegalArgumentException(name + ": informe o valor em percentual ao ano");
        double d = v.doubleValue();
        if (d < min || d > max) throw new IllegalArgumentException(name + " precisa estar entre " + (int) min + "% e " + (int) max + "% ao ano");
        return d / 100;
    }

    private static long seed(Request r, Object... parts) { return r.seed() != null ? r.seed() : Objects.hash(parts); }

    private static String pct(double v) { return String.format("%.1f%%", v * 100).replace('.', ','); }
}
