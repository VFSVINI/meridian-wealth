package dev.meridianwealth.web;

import dev.meridianwealth.application.BrazilHistoryService;
import dev.meridianwealth.application.BrazilRatesService;
import dev.meridianwealth.domain.HistorySeries;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Juros, inflação e câmbio oficial do Brasil. Os valores saem como texto, exatamente como o Banco Central
 * os publicou (ADR 002: nada de {@code double} no caminho de um valor financeiro).
 */
@RestController
@RequestMapping("/api/brazil")
public class BrazilController {
    public record RateView(String key, String label, String unit, String value, String date, String dateLabel,
                           String periodicity, String source, String status, Long ageDays, String confirmedAt) {}

    public record RatesView(String today, String fetchedAt, String notice, List<RateView> items) {}

    public record PointView(String date, String value) {}

    public record SeriesView(String key, String label, String unit, String source, String dateLabel, String lastDate, String lastValue, boolean stale, String fetchedAt,
                             List<PointView> points) {}

    public record DecisionView(String date, String from, String to, String direction) {}

    public record ComparisonView(String key, String label, String nominal, String real, String rule) {}

    public record HistoryView(String asOf, String notice, Map<String, SeriesView> series, List<String> unavailable, List<DecisionView> decisions, List<ComparisonView> comparison) {}

    private final BrazilRatesService rates;
    private final BrazilHistoryService history;

    public BrazilController(BrazilRatesService rates, BrazilHistoryService history) {
        this.rates = rates;
        this.history = history;
    }

    /** Histórico de 12 meses das séries oficiais, as mudanças da meta Selic em 24 meses e o juro real; o que o Banco Central não entregou vem em {@code unavailable}. */
    @GetMapping("/history")
    public HistoryView history() {
        BrazilHistoryService.View v = history.view();
        Map<String, SeriesView> series = new LinkedHashMap<>();
        v.series().forEach((k, s) -> series.put(k.key(), new SeriesView(k.key(), k.label(), k.unit(), k.source(), k.forwardFilled() ? "vigente em" : "referência", s.last().date().toString(),
                s.last().value().toPlainString(), s.stale(), s.fetchedAt().toString(), s.points().stream().map(p -> new PointView(p.date().toString(), p.value().toPlainString())).toList())));
        return new HistoryView(v.asOf().toString(), "Dados oficiais do Banco Central do Brasil (SGS), na data de referência de cada ponto. Ferramenta analítica: não é recomendação financeira.", series,
                v.unavailable().stream().map(HistorySeries::key).toList(), v.decisions().stream().map(d -> new DecisionView(d.date().toString(), d.from().toPlainString(), d.to().toPlainString(), d.direction())).toList(),
                v.comparison().stream().map(c -> new ComparisonView(c.key(), c.label(), c.nominal().toPlainString(), c.real().toPlainString(), c.rule())).toList());
    }

    @GetMapping("/rates")
    public RatesView rates() {
        List<RateView> items = rates.items().stream().map(i -> new RateView(i.series().key(), i.series().label(),
                i.series().unit(), i.observation() == null ? null : i.observation().value().toPlainString(),
                i.observation() == null ? null : i.observation().date().toString(), i.series().dateLabel(),
                i.series().periodicity().name(), i.series().source(), i.status().name(), i.ageDays(),
                i.observation() == null ? null : i.observation().confirmedAt().toString())).toList();
        Instant fetched = rates.fetchedAt();
        return new RatesView(rates.today().toString(), fetched == null ? null : fetched.toString(),
                "Dados oficiais do Banco Central do Brasil (SGS e PTAX), na data de referência indicada. "
                        + "Ferramenta analítica: não é recomendação financeira.", items);
    }
}
