package dev.meridianwealth.domain.reports;

import java.util.List;

/** Veredito de um relatório, com cada sinal, a regra aplicada e as ressalvas. {@code verdict} é nulo sem base de comparação. */
public record ReportAssessment(Verdict verdict, List<MetricSignal> signals, String rule, List<String> caveats) {
    public ReportAssessment {
        signals = List.copyOf(signals);
        caveats = List.copyOf(caveats);
    }
}
