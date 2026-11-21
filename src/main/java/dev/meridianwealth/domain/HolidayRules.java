package dev.meridianwealth.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/** Blocos de regras de calendário usados pelos calendários de bolsa (Páscoa, n-ésimo dia da semana, observância). */
final class HolidayRules {
    private HolidayRules() {}

    /** Domingo de Páscoa (algoritmo anônimo gregoriano de Meeus/Jones/Butcher). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    static LocalDate nthWeekday(int year, Month month, int n, DayOfWeek dow) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow));
    }

    static LocalDate lastWeekday(int year, Month month, DayOfWeek dow) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(dow));
    }

    /** Regra dos EUA: feriado no sábado é observado na sexta; no domingo, na segunda. */
    static LocalDate usObserved(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case SATURDAY -> d.minusDays(1);
            case SUNDAY -> d.plusDays(1);
            default -> d;
        };
    }

    /** Regra do Reino Unido: feriado no fim de semana passa para o próximo dia útil. */
    static LocalDate ukSubstitute(LocalDate d) {
        LocalDate x = d;
        while (x.getDayOfWeek() == DayOfWeek.SATURDAY || x.getDayOfWeek() == DayOfWeek.SUNDAY) x = x.plusDays(1);
        return x;
    }
}
