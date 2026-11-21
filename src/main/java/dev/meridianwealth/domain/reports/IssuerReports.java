package dev.meridianwealth.domain.reports;

import dev.meridianwealth.domain.DataAge;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Relatórios entregues por um emissor, os mais novos primeiro. {@code fiscalYearEnd} (dia/mês) só existe para FII e
 * define o prazo do informe anual.
 */
public record IssuerReports(String symbol, String name, String cnpj, IssuerKind kind, List<ReportSource> sources,
                            List<Report> reports, String fiscalYearEnd, List<String> notices) {
    public IssuerReports {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(kind, "kind");
        sources = List.copyOf(sources);
        reports = reports.stream().sorted(Comparator.comparing(Report::periodEnd).reversed()).toList();
        notices = List.copyOf(notices);
    }

    /** Informe mensal (30 dias) mais prazo de 15 dias: passado disso, já pode haver relatório novo que o retrato não tem. */
    public static final int STALE_AFTER_DAYS = 45;

    public DataAge age(LocalDate today) {
        LocalDate oldest = sources.stream().map(ReportSource::consultedOn).min(Comparator.naturalOrder()).orElse(today);
        return DataAge.of(oldest, today, STALE_AFTER_DAYS);
    }

    public Optional<Report> latest() { return reports.stream().findFirst(); }

    public Optional<Report> deliveredFor(ReportPeriodicity periodicity, LocalDate periodEnd) {
        return reports.stream().filter(r -> r.periodicity() == periodicity && r.periodEnd().equals(periodEnd)).findFirst();
    }
}
