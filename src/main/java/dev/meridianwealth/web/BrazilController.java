package dev.meridianwealth.web;

import dev.meridianwealth.application.BrazilRatesService;
import java.time.Instant;
import java.util.List;
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
    public record RateView(String key, String label, String unit, String value, String date, String periodicity,
                           String source, String status, Long ageDays) {}

    public record RatesView(String today, String fetchedAt, String notice, List<RateView> items) {}

    private final BrazilRatesService rates;

    public BrazilController(BrazilRatesService rates) { this.rates = rates; }

    @GetMapping("/rates")
    public RatesView rates() {
        List<RateView> items = rates.items().stream().map(i -> new RateView(i.series().key(), i.series().label(),
                i.series().unit(), i.observation() == null ? null : i.observation().value().toPlainString(),
                i.observation() == null ? null : i.observation().date().toString(), i.series().periodicity().name(),
                i.series().source(), i.status().name(), i.ageDays())).toList();
        Instant fetched = rates.fetchedAt();
        return new RatesView(rates.today().toString(), fetched == null ? null : fetched.toString(),
                "Dados oficiais do Banco Central do Brasil (SGS e PTAX), na data de referência indicada. "
                        + "Ferramenta analítica: não é recomendação financeira.", items);
    }
}
