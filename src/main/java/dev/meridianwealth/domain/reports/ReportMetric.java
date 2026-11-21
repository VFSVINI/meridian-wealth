package dev.meridianwealth.domain.reports;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Um número do relatório e sua base de comparação, como publicados. {@code base} é {@code null} quando a fonte não
 * traz (ou a série não tem histórico para calcular); nada é estimado.
 */
public record ReportMetric(String code, String label, String sourceLabel, String unit, BigDecimal current,
                           String currentPeriod, BigDecimal base, String basePeriod, String baseLabel) {
    public ReportMetric {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(current, "current");
    }
}
