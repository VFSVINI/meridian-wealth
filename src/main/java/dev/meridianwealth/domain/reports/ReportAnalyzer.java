package dev.meridianwealth.domain.reports;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Regras do veredito, todas explícitas e testadas. Cada métrica compara o número publicado com a base de comparação
 * do próprio relatório e vira um sinal:
 *
 * <ul>
 *   <li>variação = (atual - base) / |base| em %, com 2 casas (HALF_EVEN); base zero ou ausente não gera sinal;</li>
 *   <li>variação maior ou igual ao limiar: +1; menor ou igual a menos o limiar: -1; no meio: 0;</li>
 *   <li>só há positivo: POSITIVO; só negativo: NEGATIVO; positivo e negativo juntos, ou tudo no meio: NEUTRO;</li>
 *   <li>resultado líquido negativo nunca resulta em POSITIVO (no máximo NEUTRO);</li>
 *   <li>sem nenhuma métrica com base de comparação: sem veredito.</li>
 * </ul>
 *
 * É uma leitura mecânica do que foi publicado. Não separa resultado não recorrente e não prevê preço.
 */
public final class ReportAnalyzer {
    public static final String NET_INCOME = "RESULTADO_LIQUIDO";
    /** Limiares em % por métrica: receita e resultado ±5%; dividend yield ±5% sobre a média; valor patrimonial ±1%. */
    static final Map<String, BigDecimal> THRESHOLDS = Map.of(
            "RECEITA", new BigDecimal("5"), NET_INCOME, new BigDecimal("5"),
            "DIVIDEND_YIELD", new BigDecimal("5"), "VALOR_PATRIMONIAL_COTA", new BigDecimal("1"));
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private ReportAnalyzer() {}

    public static String ruleText() {
        return "Variação = (atual - base) / |base|. Sinal positivo se a variação for maior ou igual ao limiar, negativo se for "
                + "menor ou igual a menos o limiar, neutro no meio. Limiares: receita ±5%, resultado líquido ±5%, dividend yield ±5% "
                + "sobre a média dos 3 meses anteriores, valor patrimonial da cota ±1% sobre o mês anterior. Só positivos: POSITIVO; "
                + "só negativos: NEGATIVO; positivos e negativos juntos ou nenhum: NEUTRO; resultado líquido negativo nunca é POSITIVO.";
    }

    public static ReportAssessment assess(Report report) {
        List<MetricSignal> signals = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        for (ReportMetric m : report.metrics()) signals.add(signal(m));
        boolean any = signals.stream().anyMatch(MetricSignal::computed);
        if (!any) {
            caveats.add("Sem base de comparação publicada para este relatório: não há veredito.");
            return new ReportAssessment(null, signals, ruleText(), caveats);
        }
        long positives = signals.stream().filter(s -> s.signal() > 0).count();
        long negatives = signals.stream().filter(s -> s.signal() < 0).count();
        Verdict verdict = positives > 0 && negatives > 0 ? Verdict.NEUTRAL
                : positives > 0 ? Verdict.POSITIVE : negatives > 0 ? Verdict.NEGATIVE : Verdict.NEUTRAL;
        boolean loss = report.metrics().stream().anyMatch(m -> NET_INCOME.equals(m.code()) && m.current().signum() < 0);
        if (loss && verdict == Verdict.POSITIVE) {
            verdict = Verdict.NEUTRAL;
            caveats.add("O resultado líquido do período é negativo (prejuízo): o veredito foi limitado a NEUTRO.");
        } else if (loss) {
            caveats.add("O resultado líquido do período é negativo (prejuízo).");
        }
        if (signals.stream().anyMatch(s -> !s.computed())) caveats.add("Alguma métrica ficou sem sinal por falta de base de comparação.");
        caveats.add("Leitura mecânica dos números publicados: não separa resultado não recorrente e não prevê o preço do ativo.");
        return new ReportAssessment(verdict, signals, ruleText(), caveats);
    }

    static MetricSignal signal(ReportMetric m) {
        BigDecimal threshold = THRESHOLDS.get(m.code());
        if (threshold == null) throw new IllegalArgumentException("Métrica sem limiar definido: " + m.code());
        if (m.base() == null || m.base().signum() == 0) {
            return new MetricSignal(m, null, 0, threshold, m.label() + ": sem base de comparação (" + m.baseLabel() + "); sem sinal.");
        }
        BigDecimal variation = variationPercent(m.current(), m.base());
        int signal = variation.compareTo(threshold) >= 0 ? 1 : variation.compareTo(threshold.negate()) <= 0 ? -1 : 0;
        String word = signal > 0 ? "positivo" : signal < 0 ? "negativo" : "neutro";
        String text = m.label() + ": " + show(m, m.current()) + " contra " + show(m, m.base()) + " (" + m.baseLabel()
                + "; variação " + signed(variation) + "%). Limiar de ±" + threshold.toPlainString() + "%: sinal " + word + ".";
        return new MetricSignal(m, variation, signal, threshold, text);
    }

    /** (atual - base) / |base| em %, 2 casas HALF_EVEN; com base negativa, melhorar o resultado dá variação positiva. */
    public static BigDecimal variationPercent(BigDecimal current, BigDecimal base) {
        return current.subtract(base).multiply(BigDecimal.valueOf(100), MathContext.DECIMAL128)
                .divide(base.abs(), 2, RoundingMode.HALF_EVEN);
    }

    private static String signed(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        String s = f.format(v);
        return v.signum() > 0 ? "+" + s : s;
    }

    private static String show(ReportMetric m, BigDecimal value) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        if ("BRL".equals(m.unit())) {
            f.setMinimumFractionDigits(2);
            f.setMaximumFractionDigits(2);
            return "R$ " + f.format(value);
        }
        f.setMaximumFractionDigits(6);
        return f.format(value);
    }
}
