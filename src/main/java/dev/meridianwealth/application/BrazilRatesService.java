package dev.meridianwealth.application;

import dev.meridianwealth.application.port.BrazilRatesPort;
import dev.meridianwealth.domain.BrazilRates;
import dev.meridianwealth.domain.RateObservation;
import dev.meridianwealth.domain.RateSeries;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Caso de uso: juros, inflação e câmbio oficial do Brasil com a situação de cada indicador. Todo indicador
 * do catálogo aparece, mesmo sem dado ({@link Status#UNAVAILABLE}), para a tela nunca esconder uma lacuna.
 */
public class BrazilRatesService {
    public enum Status { OK, STALE, UNAVAILABLE }

    /** Um indicador: a observação (nula se ainda não chegou), a idade em dias e a situação. */
    public record Item(RateSeries series, RateObservation observation, Status status, Long ageDays) {}

    private final BrazilRatesPort port;
    private final Clock clock;

    public BrazilRatesService(BrazilRatesPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public LocalDate today() { return LocalDate.now(clock); }

    /** Quando algum indicador foi atualizado pela última vez (nulo se nunca). */
    public Instant fetchedAt() { return port.current().fetchedAt(); }

    public List<Item> items() {
        BrazilRates rates = port.current();
        LocalDate today = today();
        Instant now = clock.instant();
        return Arrays.stream(RateSeries.values()).map(series -> rates.get(series)
                .map(o -> new Item(series, o, o.isStale(today, now) ? Status.STALE : Status.OK, o.ageDays(today)))
                .orElseGet(() -> new Item(series, null, Status.UNAVAILABLE, null))).toList();
    }
}
