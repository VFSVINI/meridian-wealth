package dev.meridianwealth.application;

import dev.meridianwealth.application.port.ReportsPort;
import dev.meridianwealth.domain.DataAge;
import dev.meridianwealth.domain.reports.DeliveryCalendar;
import dev.meridianwealth.domain.reports.IssuerReports;
import dev.meridianwealth.domain.reports.Report;
import dev.meridianwealth.domain.reports.ReportAnalyzer;
import dev.meridianwealth.domain.reports.ReportAssessment;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Caso de uso: radar de relatórios (calendário de prazos e veredito de cada relatório entregue). */
public class ReportsService {
    /** Relatório entregue junto do veredito e dos números que o geraram. */
    public record ReportView(Report report, ReportAssessment assessment) {}

    /** Radar de um emissor na data {@code today}: relatórios com veredito e calendário de prazos. */
    public record Radar(IssuerReports issuer, List<ReportView> reports, List<DeliveryCalendar.Entry> calendar,
                        DeliveryCalendar.Entry nextDeadline, LocalDate today, DataAge age) {}

    private final ReportsPort port;
    private final Clock clock;

    public ReportsService(ReportsPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public List<Radar> all() { return port.all().stream().map(this::radar).toList(); }

    public Radar radar(String symbol) {
        return port.find(symbol).map(this::radar).orElseThrow(() -> new ReportsUnavailableException(symbol));
    }

    private Radar radar(IssuerReports issuer) {
        LocalDate today = LocalDate.now(clock);
        List<ReportView> views = issuer.reports().stream().map(r -> new ReportView(r, ReportAnalyzer.assess(r))).toList();
        List<DeliveryCalendar.Entry> calendar = DeliveryCalendar.of(issuer, today);
        return new Radar(issuer, views, calendar, DeliveryCalendar.nextDeadline(calendar, today).orElse(null), today, issuer.age(today));
    }
}
