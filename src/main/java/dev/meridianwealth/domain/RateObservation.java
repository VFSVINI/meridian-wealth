package dev.meridianwealth.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Um valor publicado de um indicador oficial, com a data de referência da publicação. */
public record RateObservation(RateSeries series, BigDecimal value, LocalDate date) {
    public RateObservation {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(date, "date");
    }

    /** Dias entre a data de referência e {@code today} (zero se a data for de hoje ou futura). */
    public long ageDays(LocalDate today) { return Math.max(0, ChronoUnit.DAYS.between(date, today)); }

    /** Mais velha do que a periodicidade da série admite: o BCB não publicou nada novo no prazo esperado. */
    public boolean isStale(LocalDate today) { return ageDays(today) > series.periodicity().toleranceDays(); }
}
