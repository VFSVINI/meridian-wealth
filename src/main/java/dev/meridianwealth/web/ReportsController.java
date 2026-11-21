package dev.meridianwealth.web;

import dev.meridianwealth.application.ReportsService;
import dev.meridianwealth.application.ReportsService.Radar;
import dev.meridianwealth.application.ReportsUnavailableException;
import dev.meridianwealth.domain.reports.DeliveryCalendar;
import dev.meridianwealth.domain.reports.MetricSignal;
import dev.meridianwealth.domain.reports.ReportAnalyzer;
import dev.meridianwealth.domain.reports.ReportAssessment;
import dev.meridianwealth.domain.reports.ReportSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Radar de relatórios: prazos de divulgação (regra da CVM) e veredito de cada relatório entregue, sempre com os números
 * e a regra que o geraram. Dados abertos da CVM, curados e versionados; o que a fonte não traz sai como {@code null}.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportsController {
    static final String DISCLAIMER = "Ferramenta analítica e educacional. O veredito é uma leitura mecânica dos números "
            + "publicados, não prevê preço e não é recomendação de investimento.";

    public record SourceView(String name, String url, String kind, String kindLabel, LocalDate consultedOn,
                             Map<String, String> fileSha256) {
        static SourceView of(ReportSource s) {
            return new SourceView(s.name(), s.url(), s.kind().name(), s.kind().label(), s.consultedOn(), s.fileSha256());
        }
    }

    public record CalendarView(String periodicity, String periodicityLabel, String label, LocalDate periodEnd,
                               LocalDate deadline, String rule, String status, String statusLabel, LocalDate deliveredOn) {
        static CalendarView of(DeliveryCalendar.Entry e) {
            if (e == null) return null;
            return new CalendarView(e.periodicity().name(), e.periodicity().label(), e.label(), e.periodEnd(), e.deadline(),
                    e.rule(), e.status().name(), e.status().label(), e.deliveredOn());
        }
    }

    public record SignalView(String code, String label, String sourceLabel, String unit, BigDecimal current,
                             String currentPeriod, BigDecimal base, String basePeriod, String baseLabel,
                             BigDecimal variationPercent, int signal, BigDecimal thresholdPercent, String explanation) {
        static SignalView of(MetricSignal s) {
            var m = s.metric();
            return new SignalView(m.code(), m.label(), m.sourceLabel(), m.unit(), m.current(), m.currentPeriod(), m.base(),
                    m.basePeriod(), m.baseLabel(), s.variationPercent(), s.signal(), s.thresholdPercent(), s.explanation());
        }
    }

    public record ReportView(String id, String periodicity, String periodicityLabel, String label, LocalDate periodEnd,
                             LocalDate firstDeliveredOn, LocalDate versionDeliveredOn, int version, String link, String verdict,
                             String verdictLabel, List<SignalView> signals, List<String> caveats) {
        static ReportView of(ReportsService.ReportView v) {
            var r = v.report();
            ReportAssessment a = v.assessment();
            return new ReportView(r.id(), r.periodicity().name(), r.periodicity().label(), r.label(), r.periodEnd(),
                    r.deliveredOn(), r.versionDeliveredOn(), r.version(), r.link(), a.verdict() == null ? null : a.verdict().name(),
                    a.verdict() == null ? "SEM VEREDITO" : a.verdict().label(), a.signals().stream().map(SignalView::of).toList(),
                    a.caveats());
        }
    }

    public record RadarView(String symbol, String name, String cnpj, String kind, String kindLabel, List<SourceView> sources,
                            LocalDate today, CalendarView nextDeadline, List<CalendarView> calendar, List<ReportView> reports,
                            List<String> notices, String rule, String disclaimer, long dataAgeDays, int staleAfterDays,
                            boolean stale) {
        static RadarView of(Radar r) {
            var i = r.issuer();
            return new RadarView(i.symbol(), i.name(), i.cnpj(), i.kind().name(), i.kind().label(),
                    i.sources().stream().map(SourceView::of).toList(), r.today(), CalendarView.of(r.nextDeadline()),
                    r.calendar().stream().map(CalendarView::of).toList(), r.reports().stream().map(ReportView::of).toList(),
                    i.notices(), ReportAnalyzer.ruleText(), DISCLAIMER, r.age().days(), r.age().staleAfterDays(),
                    r.age().stale());
        }
    }

    /** Resumo por ativo: o relatório mais novo com seu veredito e o próximo prazo. */
    public record SummaryView(String symbol, String name, String kindLabel, String latestLabel, LocalDate latestPeriodEnd,
                              String latestVerdict, String latestVerdictLabel, CalendarView nextDeadline,
                              boolean stale) {
        static SummaryView of(Radar r) {
            var latest = r.reports().stream().findFirst().map(ReportView::of).orElse(null);
            var i = r.issuer();
            return new SummaryView(i.symbol(), i.name(), i.kind().label(), latest == null ? null : latest.label(),
                    latest == null ? null : latest.periodEnd(), latest == null ? null : latest.verdict(),
                    latest == null ? null : latest.verdictLabel(), CalendarView.of(r.nextDeadline()), r.age().stale());
        }
    }

    private final ReportsService service;

    public ReportsController(ReportsService service) { this.service = service; }

    @GetMapping
    public List<SummaryView> all() { return service.all().stream().map(SummaryView::of).toList(); }

    @GetMapping("/{symbol}")
    public RadarView one(@PathVariable String symbol) { return RadarView.of(service.radar(symbol.toUpperCase(Locale.ROOT))); }

    @RestControllerAdvice(assignableTypes = ReportsController.class)
    static class Errors {
        @ExceptionHandler(ReportsUnavailableException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, String> unavailable(ReportsUnavailableException e) { return Map.of("error", e.getMessage(), "symbol", e.symbol()); }
    }
}
