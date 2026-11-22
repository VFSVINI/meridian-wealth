package dev.meridianwealth.application.risk;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.risk.MonteCarloService.Holding;
import dev.meridianwealth.application.risk.MonteCarloService.HistorySource;
import dev.meridianwealth.application.risk.MonteCarloService.PortfolioSource;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.correlation.Correlation;
import dev.meridianwealth.domain.montecarlo.AlignedReturns;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Correlação, risco com diversificação e concentração da carteira salva (item #19), tudo sobre <b>retornos reais</b>: a correlação de
 * Pearson entre os ativos e entre as classes (par a par, últimos 252 pregões), a volatilidade da carteira com a correlação contra a média
 * ponderada das volatilidades individuais, e o mapa de concentração (peso de cada classe em cada faixa de risco). Ativo sem histórico
 * suficiente fica fora da correlação com o motivo e entra na concentração pelo valor; célula sem dado fica "não informado", nunca zero.
 */
public final class CorrelationService {
    public static final String UNRATED = "UNRATED";
    public static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "EXTREME", UNRATED);

    public record Excluded(String label, String assetKey, String reason) {}

    /** @param values correlação por par, {@code null} = não informado; @param days pregões em comum de cada par */
    public record MatrixView(List<String> keys, List<String> labels, List<List<Double>> values, List<List<Integer>> days) {}

    public record RiskView(int days, double portfolioVolatilityPercent, double weightedAverageVolatilityPercent, double diversificationBenefitPercent, double coveredPercent,
                           List<String> assets) {}

    public record Position(String label, String assetKey, double weightPercent, String category) {}

    /** @param cells {@code cells[linha][faixa]}: peso em % do patrimônio total da classe naquela faixa de risco */
    public record Concentration(List<String> categories, List<String> categoryLabels, List<String> riskLevels, List<List<Double>> cells, double assetHhi, double classHhi,
                                double effectivePositions, double largestPositionPercent, List<Position> topPositions) {}

    public record Output(MatrixView assets, MatrixView classes, RiskView risk, Concentration concentration, List<Excluded> excluded, int window, int minDays, double totalBrl,
                         List<String> notes) {}

    private final PortfolioSource portfolio;
    private final HistorySource history;

    public CorrelationService(PortfolioSource portfolio, HistorySource history) {
        this.portfolio = portfolio;
        this.history = history;
    }

    public Output analyze(AuthPrincipal who) {
        List<Holding> all = portfolio.holdings(who).stream().filter(h -> h.valueBrl() != null && h.valueBrl().signum() > 0).toList();
        if (all.isEmpty()) throw new MonteCarloException("A carteira não tem posição com valor em reais para analisar. Registre posições na Minha Carteira.");
        double total = all.stream().map(Holding::valueBrl).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();

        Map<String, TreeMap<Long, Double>> closes = new LinkedHashMap<>();
        Map<String, String> why = new LinkedHashMap<>();
        for (Holding h : all) {
            if (h.symbol() == null) {
                why.put(h.assetKey(), "Sem cotação nem histórico: o tipo não é negociado em mercado.");
                continue;
            }
            List<Candle> bars;
            try {
                bars = history.candles(h.symbol(), MonteCarloService.HISTORY_BARS);
            } catch (RuntimeException e) {
                why.put(h.assetKey(), "Sem dado real de mercado agora.");
                continue;
            }
            TreeMap<Long, Double> byDay = new TreeMap<>();
            for (Candle c : bars) {
                if (c.close() > 0) byDay.put(Math.floorDiv(c.time(), 86_400_000L), c.close());
            }
            if (byDay.size() <= Correlation.MIN_DAYS) why.put(h.assetKey(), "Histórico curto: " + byDay.size() + " pregões (mínimo " + (Correlation.MIN_DAYS + 1) + ").");
            else closes.put(h.assetKey(), byDay);
        }
        Map<String, Holding> byKey = new LinkedHashMap<>();
        all.forEach(h -> byKey.put(h.assetKey(), h));
        List<String> notes = new ArrayList<>(List.of(
                "Correlação de Pearson sobre os últimos " + Correlation.WINDOW + " retornos diários reais, par a par: cada par usa as datas que os dois têm.",
                "Par com menos de " + Correlation.MIN_DAYS + " pregões em comum fica \"não informado\": nunca zero.",
                "Correlação mede movimento conjunto no passado e pode mudar em crises: ativos de classes diferentes tendem a cair juntos no estresse.",
                "Ativos em dólar entram pelo retorno na moeda do ativo (o câmbio não entra); renda fixa e fundos sem cota ficam fora da correlação e entram na concentração pelo valor."));

        MatrixView assets = null;
        MatrixView classes = null;
        RiskView risk = null;
        if (!closes.isEmpty()) {
            Correlation.Matrix m = Correlation.pairwise(closes);
            assets = view(m, key -> byKey.get(key).label());
            Map<String, String> riskWhy = new LinkedHashMap<>();
            AlignedReturns aligned = MonteCarloService.align(new LinkedHashMap<>(closes), riskWhy);
            if (aligned != null) {
                double covered = 0;
                for (String k : aligned.symbols()) covered += byKey.get(k).valueBrl().doubleValue();
                double[] w = new double[aligned.symbols().size()];
                for (int i = 0; i < w.length; i++) w[i] = byKey.get(aligned.symbols().get(i)).valueBrl().doubleValue() / covered;
                Correlation.PortfolioRisk r = Correlation.portfolioRisk(aligned, w);
                risk = new RiskView(r.days(), r.portfolioVolatility() * 100, r.weightedAverageVolatility() * 100, r.diversificationBenefit() * 100, covered / total * 100,
                        aligned.symbols().stream().map(k -> byKey.get(k).label()).toList());
            } else notes.add("Sem histórico em comum suficiente entre os ativos para medir o risco da carteira com a correlação.");
            classes = classMatrix(closes, byKey, notes);
        }
        List<Excluded> excluded = new ArrayList<>();
        for (Holding h : all) {
            if (!closes.containsKey(h.assetKey())) excluded.add(new Excluded(h.label(), h.assetKey(), why.getOrDefault(h.assetKey(), "Sem histórico real suficiente.")));
        }
        return new Output(assets, classes, risk, concentration(all, total), excluded, Correlation.WINDOW, Correlation.MIN_DAYS, total, notes);
    }

    private static MatrixView view(Correlation.Matrix m, java.util.function.Function<String, String> label) {
        List<List<Double>> values = new ArrayList<>();
        List<List<Integer>> days = new ArrayList<>();
        for (int i = 0; i < m.keys().size(); i++) {
            List<Double> row = new ArrayList<>();
            List<Integer> d = new ArrayList<>();
            for (int j = 0; j < m.keys().size(); j++) {
                row.add(m.values()[i][j]);
                d.add(m.days()[i][j]);
            }
            values.add(row);
            days.add(d);
        }
        return new MatrixView(m.keys(), m.keys().stream().map(label).toList(), values, days);
    }

    /** Uma série por classe: o retorno diário da classe é a média, ponderada pelo valor, dos ativos da classe que têm histórico em comum. */
    private static MatrixView classMatrix(Map<String, TreeMap<Long, Double>> closes, Map<String, Holding> byKey, List<String> notes) {
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (String k : closes.keySet()) members.computeIfAbsent(byKey.get(k).category() == null ? "OUTROS" : byKey.get(k).category(), c -> new ArrayList<>()).add(k);
        Map<String, TreeMap<Long, Double>> series = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : members.entrySet()) {
            Map<String, TreeMap<Long, Double>> sub = new LinkedHashMap<>();
            e.getValue().forEach(k -> sub.put(k, closes.get(k)));
            Map<String, String> ignored = new LinkedHashMap<>();
            AlignedReturns a = sub.size() == 1 ? AlignedReturns.of(sub) : alignedOrBest(sub, ignored);
            String label = byKey.get(e.getValue().get(0)).categoryLabel() == null ? e.getKey() : byKey.get(e.getValue().get(0)).categoryLabel();
            if (a == null || a.days() < Correlation.MIN_DAYS) {
                notes.add("A classe \"" + label + "\" não tem histórico em comum suficiente entre seus ativos e ficou fora da matriz de classes.");
                continue;
            }
            double sum = 0;
            for (String k : a.symbols()) sum += byKey.get(k).valueBrl().doubleValue();
            TreeMap<Long, Double> idx = new TreeMap<>();
            double level = 100;
            idx.put(a.dates()[0] - 1, level);
            for (int d = 0; d < a.days(); d++) {
                double r = 0;
                for (int i = 0; i < a.symbols().size(); i++) r += byKey.get(a.symbols().get(i)).valueBrl().doubleValue() / sum * a.returns()[i][d];
                level *= 1 + r;
                idx.put(a.dates()[d], level);
            }
            series.put(e.getKey(), idx);
            labels.put(e.getKey(), label);
        }
        if (series.size() < 2) {
            if (series.size() == 1) notes.add("Só uma classe tem histórico: a matriz de classes precisa de pelo menos duas.");
            return null;
        }
        return view(Correlation.pairwise(series), labels::get);
    }

    /** Alinha os ativos da classe; se a janela em comum ficar curta, tira o de menor histórico até caber. */
    private static AlignedReturns alignedOrBest(Map<String, TreeMap<Long, Double>> sub, Map<String, String> why) {
        Map<String, TreeMap<Long, Double>> work = new LinkedHashMap<>(sub);
        while (!work.isEmpty()) {
            AlignedReturns a = AlignedReturns.of(work);
            if (a != null && a.days() >= Correlation.MIN_DAYS) return a;
            String shortest = work.entrySet().stream().min(Map.Entry.comparingByValue(Comparator.comparingInt(TreeMap::size))).orElseThrow().getKey();
            why.put(shortest, "curto");
            work.remove(shortest);
        }
        return null;
    }

    private static Concentration concentration(List<Holding> all, double total) {
        Map<String, String> catLabels = new LinkedHashMap<>();
        Map<String, double[]> byCat = new LinkedHashMap<>();
        Map<String, Double> classWeight = new LinkedHashMap<>();
        List<Position> positions = new ArrayList<>();
        double hhiAsset = 0;
        for (Holding h : all) {
            double w = h.valueBrl().doubleValue() / total;
            hhiAsset += w * w;
            String cat = h.category() == null ? "OUTROS" : h.category();
            catLabels.putIfAbsent(cat, h.categoryLabel() == null ? "Outros" : h.categoryLabel());
            int col = RISK_LEVELS.indexOf(h.risk() == null ? UNRATED : h.risk());
            if (col < 0) col = RISK_LEVELS.indexOf(UNRATED);
            byCat.computeIfAbsent(cat, c -> new double[RISK_LEVELS.size()])[col] += w * 100;
            classWeight.merge(cat, w, Double::sum);
            positions.add(new Position(h.label(), h.assetKey(), w * 100, cat));
        }
        double hhiClass = classWeight.values().stream().mapToDouble(w -> w * w).sum();
        positions.sort(Comparator.comparingDouble(Position::weightPercent).reversed());
        List<List<Double>> cells = new ArrayList<>();
        for (double[] row : byCat.values()) {
            List<Double> r = new ArrayList<>();
            for (double v : row) r.add(v);
            cells.add(r);
        }
        return new Concentration(List.copyOf(byCat.keySet()), byCat.keySet().stream().map(catLabels::get).toList(), RISK_LEVELS, cells, hhiAsset, hhiClass, 1 / hhiAsset,
                positions.get(0).weightPercent(), positions.stream().limit(5).toList());
    }
}
