package dev.meridianwealth.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Idade de um dado curado (dias desde a consulta à fonte) e se passou do limite em que ele pode estar defasado.
 * Dado curado é um retrato: o produto avisa quando o retrato envelhece em vez de fingir que está em dia.
 */
public record DataAge(long days, int staleAfterDays, boolean stale) {
    public static DataAge of(LocalDate consultedOn, LocalDate today, int staleAfterDays) {
        long days = Math.max(0, ChronoUnit.DAYS.between(consultedOn, today));
        return new DataAge(days, staleAfterDays, days > staleAfterDays);
    }
}
