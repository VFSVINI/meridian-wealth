package dev.meridianwealth.domain.thesis;

import dev.meridianwealth.domain.thesis.Thesis.Sensitivity;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lê uma carteira pela lente de cada tese: quanto dela cai em cada grupo sensível e quanto fica fora de qualquer um. Não dá
 * nota nem diz se a carteira "está certa": mostra a exposição. Percentuais com 2 casas (HALF_EVEN).
 */
public final class ThesisReader {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ThesisReader() {}

    public record SensitivityReading(String id, String label, String effect, String rationale, BigDecimal percent, List<String> holdings) {
        public SensitivityReading {
            holdings = List.copyOf(holdings);
        }
    }

    public record Reading(Thesis thesis, List<SensitivityReading> sensitivities, BigDecimal favoredPercent, BigDecimal hurtPercent,
                          BigDecimal outsideThesisPercent) {
        public Reading {
            sensitivities = List.copyOf(sensitivities);
        }
    }

    public static List<Reading> readAll(List<Holding> holdings) { return ThesisCatalog.ALL.stream().map(t -> read(t, holdings)).toList(); }

    public static Reading read(Thesis thesis, List<Holding> holdings) {
        if (holdings == null || holdings.isEmpty()) throw new IllegalArgumentException("Informe ao menos uma posição");
        if (holdings.size() > 50) throw new IllegalArgumentException("No máximo 50 posições");
        BigDecimal total = holdings.stream().map(Holding::valueBrl).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SensitivityReading> readings = new ArrayList<>();
        Set<Integer> favored = new HashSet<>();
        Set<Integer> hurt = new HashSet<>();
        Set<Integer> any = new HashSet<>();
        for (Sensitivity s : thesis.sensitivities()) {
            BigDecimal sum = BigDecimal.ZERO;
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < holdings.size(); i++) {
                Holding h = holdings.get(i);
                if (!s.matches(h)) continue;
                sum = sum.add(h.valueBrl());
                labels.add(h.label());
                any.add(i);
                (s.effect() == Thesis.Effect.FAVORECIDO_SE_CONFIRMAR ? favored : hurt).add(i);
            }
            readings.add(new SensitivityReading(s.id(), s.label(), s.effect().label(), s.rationale(), pct(sum, total), labels));
        }
        return new Reading(thesis, readings, pct(sum(holdings, favored), total), pct(sum(holdings, hurt), total),
                pct(sum(holdings, complement(holdings.size(), any)), total));
    }

    private static Set<Integer> complement(int size, Set<Integer> in) {
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i < size; i++) if (!in.contains(i)) out.add(i);
        return out;
    }

    private static BigDecimal sum(List<Holding> holdings, Set<Integer> idx) {
        BigDecimal s = BigDecimal.ZERO;
        for (int i : idx) s = s.add(holdings.get(i).valueBrl());
        return s;
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal total) {
        return part.multiply(HUNDRED, MathContext.DECIMAL128).divide(total, 2, RoundingMode.HALF_EVEN);
    }
}
