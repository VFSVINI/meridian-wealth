package dev.meridianwealth.domain.thesis;

import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.domain.NewsItem;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tradutor geopolítico de carteira: cruza a pressão que as manchetes reais exercem sobre cada setor (a mesma contagem de
 * palavras-chave do radar macro) com o peso de cada setor na carteira do usuário. É descritivo: mostra onde a carteira está
 * exposta ao que saiu no noticiário, não prevê preço nem diz o que fazer.
 *
 * <p>Limites assumidos e exibidos: a pressão é contagem de palavras-chave em manchetes (não é análise de conteúdo); só os setores
 * que o radar mapeia (energia, defesa, financeiro, tecnologia, indústria) têm pressão; posições sem setor conhecido ficam em
 * "não informado" e fora da pressão média, cuja cobertura é declarada. Percentuais e pressão saem com 2 casas (HALF_EVEN).
 */
public final class GeoTranslator {
    public static final int MAX_HEADLINES_PER_SECTOR = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GeoTranslator() {}

    public record Headline(String title, String source, BigDecimal risk) {}

    /** Um setor: peso na carteira, pressão das manchetes (0 a 1) e a contribuição em pontos percentuais (peso x pressão). */
    public record SectorLine(String sector, BigDecimal exposurePercent, BigDecimal pressure, BigDecimal contributionPoints,
                             List<Headline> headlines) {
        public SectorLine {
            headlines = List.copyOf(headlines);
        }
    }

    public record Translation(boolean macroAvailable, BigDecimal totalBrl, BigDecimal classifiedPercent, BigDecimal unclassifiedPercent,
                              BigDecimal weightedPressure, List<SectorLine> sectors, List<String> pressuredWithoutExposure,
                              List<String> findings) {
        public Translation {
            sectors = List.copyOf(sectors);
            pressuredWithoutExposure = List.copyOf(pressuredWithoutExposure);
            findings = List.copyOf(findings);
        }
    }

    public static Translation translate(List<Holding> holdings, MacroSnapshot macro) {
        if (holdings == null || holdings.isEmpty()) throw new IllegalArgumentException("Informe ao menos uma posição");
        if (holdings.size() > 50) throw new IllegalArgumentException("No máximo 50 posições");
        BigDecimal total = holdings.stream().map(Holding::valueBrl).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> bySector = new TreeMap<>();
        BigDecimal unclassified = BigDecimal.ZERO;
        for (Holding h : holdings) {
            if (h.sector() == null) unclassified = unclassified.add(h.valueBrl());
            else bySector.merge(h.sector(), h.valueBrl(), BigDecimal::add);
        }
        BigDecimal unclassifiedPct = pct(unclassified, total);
        BigDecimal classifiedPct = HUNDRED.setScale(2).subtract(unclassifiedPct);

        boolean available = macro != null && macro.available();
        Map<String, Double> pressure = available ? macro.sectorImpact() : Map.of();
        List<SectorLine> lines = new ArrayList<>();
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal classifiedValue = total.subtract(unclassified);
        for (var e : bySector.entrySet()) {
            BigDecimal p = pressureOf(pressure.get(e.getKey()));
            BigDecimal weight = e.getValue().divide(total, MathContext.DECIMAL128);
            BigDecimal contribution = weight.multiply(p, MathContext.DECIMAL128).multiply(HUNDRED, MathContext.DECIMAL128)
                    .setScale(2, RoundingMode.HALF_EVEN);
            if (classifiedValue.signum() > 0) {
                weighted = weighted.add(e.getValue().divide(classifiedValue, MathContext.DECIMAL128).multiply(p, MathContext.DECIMAL128));
            }
            lines.add(new SectorLine(e.getKey(), pct(e.getValue(), total), p, contribution, headlinesFor(macro, available, e.getKey())));
        }
        lines.sort(Comparator.comparing(SectorLine::contributionPoints).reversed().thenComparing(SectorLine::sector));
        List<String> uncovered = pressure.entrySet().stream().filter(e -> e.getValue() > 0 && !bySector.containsKey(e.getKey()))
                .map(Map.Entry::getKey).sorted().toList();
        BigDecimal weightedPressure = available && classifiedValue.signum() > 0 ? weighted.setScale(2, RoundingMode.HALF_EVEN) : null;
        return new Translation(available, total, classifiedPct, unclassifiedPct, weightedPressure, lines, uncovered,
                findings(available, lines, uncovered, unclassifiedPct, pressure));
    }

    private static List<String> findings(boolean available, List<SectorLine> lines, List<String> uncovered, BigDecimal unclassifiedPct,
                                         Map<String, Double> pressure) {
        List<String> out = new ArrayList<>();
        if (!available) {
            out.add("Sem dado macro agora: nenhuma pressão é calculada e nada é estimado no lugar.");
        } else if (pressure.values().stream().noneMatch(v -> v > 0)) {
            out.add("Nenhuma manchete atual atinge os setores que o radar mapeia.");
        }
        for (SectorLine l : lines) {
            if (l.pressure().signum() > 0) {
                out.add(label(l.sector()) + ": " + num(l.exposurePercent()) + "% da carteira, com pressão " + num(l.pressure())
                        + " das manchetes (contribuição de " + num(l.contributionPoints()) + " p.p.).");
            }
        }
        if (!uncovered.isEmpty()) {
            out.add("Setores pressionados sem exposição na carteira: " + String.join(", ", uncovered.stream().map(GeoTranslator::label).toList()) + ".");
        }
        if (unclassifiedPct.signum() > 0) {
            out.add(num(unclassifiedPct) + "% da carteira está em posições sem setor conhecido (não informado) e fica fora da pressão média.");
        }
        return out;
    }

    private static List<Headline> headlinesFor(MacroSnapshot macro, boolean available, String sector) {
        if (!available) return List.of();
        return macro.news().stream().filter(n -> n.sectors().contains(sector))
                .sorted(Comparator.comparingDouble(NewsItem::risk).reversed().thenComparing(NewsItem::title))
                .limit(MAX_HEADLINES_PER_SECTOR)
                .map(n -> new Headline(n.title(), n.source(), pressureOf(n.risk()))).toList();
    }

    private static BigDecimal pressureOf(Double v) {
        double x = v == null || !Double.isFinite(v) ? 0 : Math.max(0, Math.min(1, v));
        return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal total) {
        return part.multiply(HUNDRED, MathContext.DECIMAL128).divide(total, 2, RoundingMode.HALF_EVEN);
    }

    private static String num(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ','); }

    static String label(String sector) {
        return switch (sector) {
            case "energia" -> "Energia";
            case "defesa" -> "Defesa";
            case "financeiro" -> "Financeiro";
            case "tecnologia" -> "Tecnologia";
            case "industria" -> "Indústria";
            case "mineracao" -> "Mineração";
            case "imobiliario" -> "Imobiliário";
            case "consumo" -> "Consumo";
            case "cripto" -> "Cripto";
            case "cambio" -> "Câmbio";
            case "refugio" -> "Refúgio (ouro)";
            case "amplo" -> "Amplo (índices)";
            default -> sector;
        };
    }
}
