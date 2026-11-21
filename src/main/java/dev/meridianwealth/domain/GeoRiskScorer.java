package dev.meridianwealth.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Pontua manchetes por palavras-chave de risco geopolítico/macro e mapeia o impacto por setor. */
public final class GeoRiskScorer {
    private GeoRiskScorer() {}

    private record Rule(String term, double weight, List<String> sectors, Pattern pattern) {
        Rule(String term, double weight, List<String> sectors) {
            this(term, weight, sectors, Pattern.compile(term.length() <= 4
                    ? "\\b" + Pattern.quote(term) + "s?\\b" : "\\b" + Pattern.quote(term)));
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("war", 0.8, List.of("energia", "defesa")),
            new Rule("invasion", 0.9, List.of("energia", "defesa")),
            new Rule("missile", 0.8, List.of("energia", "defesa")),
            new Rule("airstrike", 0.8, List.of("energia", "defesa")),
            new Rule("conflict", 0.5, List.of("energia")),
            new Rule("escalat", 0.6, List.of("energia", "defesa")),
            new Rule("sanction", 0.7, List.of("energia", "financeiro")),
            new Rule("embargo", 0.7, List.of("energia")),
            new Rule("blockade", 0.7, List.of("energia", "industria")),
            new Rule("tariff", 0.6, List.of("tecnologia", "industria")),
            new Rule("trade war", 0.7, List.of("tecnologia", "industria")),
            new Rule("export control", 0.6, List.of("tecnologia")),
            new Rule("rate hike", 0.5, List.of("financeiro", "tecnologia")),
            new Rule("inflation", 0.4, List.of("financeiro")),
            new Rule("central bank", 0.3, List.of("financeiro")),
            new Rule("recession", 0.6, List.of("financeiro", "industria")),
            new Rule("default", 0.6, List.of("financeiro")),
            new Rule("bank run", 0.8, List.of("financeiro")),
            new Rule("opec", 0.5, List.of("energia")),
            new Rule("oil", 0.3, List.of("energia")),
            new Rule("cyberattack", 0.5, List.of("tecnologia")),
            new Rule("ransomware", 0.4, List.of("tecnologia")),
            new Rule("coup", 0.7, List.of("energia")),
            new Rule("terror", 0.7, List.of("defesa")));

    private static final List<String> DEESCALATION = List.of("ceasefire", "peace deal", "peace declaration", "peace agreement", "peace accord", "truce", "de-escalat",
            "agreement reached");

    public static NewsItem score(String title, String source) {
        String t = title.toLowerCase(Locale.ROOT);
        double risk = 0;
        List<String> sectors = new ArrayList<>();
        for (Rule r : RULES) {
            if (r.pattern().matcher(t).find()) {
                risk += r.weight();
                r.sectors().stream().filter(s -> !sectors.contains(s)).forEach(sectors::add);
            }
        }
        boolean calm = DEESCALATION.stream().anyMatch(t::contains);
        if (calm) risk = Math.max(0, risk - 0.6);
        risk = Indicators.clamp(risk, 0, 1);
        double sentiment = calm ? 0.5 : -risk;
        return new NewsItem(title, source, risk, sentiment, sectors);
    }

    /** Índice agregado 0..1: média de risco das manchetes e fração de manchetes de risco alto. */
    public static double aggregate(List<NewsItem> items) {
        if (items.isEmpty()) return 0;
        double avg = items.stream().mapToDouble(NewsItem::risk).average().orElse(0);
        double share = items.stream().filter(n -> n.risk() >= 0.5).count() / (double) items.size();
        return Indicators.clamp(1.6 * avg + 0.4 * share, 0, 1);
    }

    /** Impacto por setor (0..1): soma do risco das manchetes do setor, normalizada por 3 manchetes fortes. */
    public static Map<String, Double> sectorImpact(List<NewsItem> items) {
        Map<String, Double> sum = new HashMap<>();
        for (NewsItem n : items) {
            for (String s : n.sectors()) sum.merge(s, n.risk(), Double::sum);
        }
        sum.replaceAll((k, v) -> Indicators.clamp(v / 3.0, 0, 1));
        return sum;
    }
}
