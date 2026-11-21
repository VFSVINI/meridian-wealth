package dev.meridianwealth.domain.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Relatório periódico entregue. {@code deliveredOn} é a data da <b>primeira</b> entrega (a que conta para o prazo);
 * {@code versionDeliveredOn} é a da versão cujos números foram lidos (pode ser uma reentrega posterior).
 */
public record Report(String id, ReportPeriodicity periodicity, String label, LocalDate periodEnd, LocalDate deliveredOn,
                     LocalDate versionDeliveredOn, int version, String link, List<ReportMetric> metrics) {
    public Report {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(periodicity, "periodicity");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(deliveredOn, "deliveredOn");
        Objects.requireNonNull(versionDeliveredOn, "versionDeliveredOn");
        if (link != null && !link.startsWith("https://")) throw new IllegalArgumentException("O link do documento deve ser https: " + id);
        metrics = List.copyOf(metrics);
    }
}
