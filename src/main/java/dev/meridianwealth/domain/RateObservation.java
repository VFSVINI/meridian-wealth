package dev.meridianwealth.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Um valor publicado de um indicador oficial.
 *
 * <p>{@code date} é a data de referência da publicação (ou, nas séries preenchidas para a frente como a
 * meta Selic, a data em que o valor está em vigor). {@code confirmedAt} é quando o sistema confirmou esse
 * valor pela última vez numa consulta bem-sucedida, mesmo que o valor não tenha mudado: é ela que revela
 * uma falha na nossa coleta.
 */
public record RateObservation(RateSeries series, BigDecimal value, LocalDate date, Instant confirmedAt) {
    /** Sem confirmação nova por mais tempo do que isto (seis ciclos de 30 min), o dado deixa de ser garantido. */
    public static final Duration CONFIRMATION_TOLERANCE = Duration.ofHours(3);

    public RateObservation {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
    }

    /** Dias entre a data de referência e {@code today} (zero se a data for de hoje ou futura). */
    public long ageDays(LocalDate today) { return Math.max(0, ChronoUnit.DAYS.between(date, today)); }

    /** O sistema não conseguiu confirmar este valor no prazo: a coleta está falhando. */
    public boolean confirmationOverdue(Instant now) {
        return Duration.between(confirmedAt, now).compareTo(CONFIRMATION_TOLERANCE) > 0;
    }

    /**
     * Desatualizado quando a coleta atrasou ({@link #confirmationOverdue}) ou, nas séries cuja data é de
     * publicação, quando o BCB não publicou nada novo no prazo da periodicidade. Séries preenchidas para a
     * frente não usam a data como idade, porque ela é sempre a de hoje.
     */
    public boolean isStale(LocalDate today, Instant now) {
        if (confirmationOverdue(now)) return true;
        return !series.forwardFilled() && ageDays(today) > series.periodicity().toleranceDays();
    }
}
