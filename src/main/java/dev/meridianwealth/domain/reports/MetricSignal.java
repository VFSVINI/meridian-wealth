package dev.meridianwealth.domain.reports;

import java.math.BigDecimal;

/** O que uma métrica contribuiu para o veredito: a variação calculada, o limiar usado e a frase que explica. */
public record MetricSignal(ReportMetric metric, BigDecimal variationPercent, int signal, BigDecimal thresholdPercent,
                           String explanation) {
    public boolean computed() { return variationPercent != null; }
}
