package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Calendários conferidos contra as páginas oficiais de 2026 (lidas em 2026-11-20):
 * nyse.com/trade/hours-calendars, b3.com.br (calendário de negociação, "Listado B3"),
 * cashmarket.deutsche-boerse.com (trading calendar) e londonstockexchange.com/equities-trading/business-days.
 */
class MarketHoursTest {
    private static Set<LocalDate> weekdayHolidays(Exchange ex, int year) {
        Set<LocalDate> out = new TreeSet<>();
        for (LocalDate d : ex.holidays(year).keySet()) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) out.add(d);
        }
        return out;
    }

    private static Set<LocalDate> dates(String... iso) {
        Set<LocalDate> out = new TreeSet<>();
        for (String s : iso) out.add(LocalDate.parse(s));
        return out;
    }

    @Test void nyse2026MatchesTheOfficialCalendar() {
        assertThat(weekdayHolidays(Exchange.NYSE, 2026)).isEqualTo(dates(
                "2026-01-01", "2026-01-19", "2026-02-16", "2026-04-03", "2026-05-25", "2026-06-19",
                "2026-07-03", "2026-09-07", "2026-11-26", "2026-12-25"));
        assertThat(Exchange.NYSE.earlyClose(LocalDate.parse("2026-11-27"))).contains(LocalTime.of(13, 0));
        assertThat(Exchange.NYSE.earlyClose(LocalDate.parse("2026-12-24"))).contains(LocalTime.of(13, 0));
        assertThat(Exchange.NYSE.earlyClose(LocalDate.parse("2026-12-23"))).isEmpty();
    }

    @Test void b3_2026MatchesTheOfficialCalendar() {
        assertThat(weekdayHolidays(Exchange.B3, 2026)).isEqualTo(dates(
                "2026-01-01", "2026-02-16", "2026-02-17", "2026-04-03", "2026-04-21", "2026-05-01",
                "2026-06-04", "2026-09-07", "2026-10-12", "2026-11-02", "2026-11-20", "2026-12-24",
                "2026-12-25", "2026-12-31"));
        assertThat(Exchange.B3.lateOpen(LocalDate.parse("2026-02-18"))).contains(LocalTime.of(13, 0));
    }

    @Test void xetra2026MatchesTheOfficialCalendar() {
        assertThat(weekdayHolidays(Exchange.XETRA, 2026)).isEqualTo(dates(
                "2026-01-01", "2026-04-03", "2026-04-06", "2026-05-01", "2026-12-24", "2026-12-25", "2026-12-31"));
        assertThat(Exchange.XETRA.holiday(LocalDate.parse("2026-05-14"))).as("Ascensão é dia de pregão").isEmpty();
        assertThat(Exchange.XETRA.holiday(LocalDate.parse("2026-05-25"))).as("Segunda de Pentecostes é pregão").isEmpty();
    }

    @Test void lse2026AgreesWithTheDatesTheOfficialPagePublishes() {
        Set<LocalDate> lse = weekdayHolidays(Exchange.LSE, 2026);
        // A página oficial lida lista 2026 apenas a partir de 31/08: estas datas foram conferidas.
        assertThat(lse).contains(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-12-25"), LocalDate.parse("2026-12-28"));
        assertThat(lse).doesNotContain(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-31"));
        assertThat(Exchange.LSE.earlyClose(LocalDate.parse("2026-12-24"))).contains(LocalTime.of(12, 30));
        // As demais seguem as regras das bank holidays inglesas (não confirmadas na página lida).
        assertThat(lse).contains(LocalDate.parse("2026-04-03"), LocalDate.parse("2026-04-06"),
                LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-25"), LocalDate.parse("2026-01-01"));
    }

    @Test void easterIsComputedCorrectlyAcrossYears() {
        assertThat(List.of(2024, 2025, 2026, 2027, 2038).stream().map(HolidayRules::easterSunday).toList())
                .containsExactly(LocalDate.parse("2024-03-31"), LocalDate.parse("2025-04-20"),
                        LocalDate.parse("2026-04-05"), LocalDate.parse("2027-03-28"), LocalDate.parse("2038-04-25"));
    }

    @Test void observanceRulesShiftWeekendHolidays() {
        // 2027: Juneteenth e 4 de julho caem no sábado/domingo; 2022: Ano-Novo no sábado não fecha a NYSE em 31/12/2021.
        assertThat(Exchange.NYSE.holidays(2027)).containsKeys(LocalDate.parse("2027-06-18"), LocalDate.parse("2027-07-05"));
        assertThat(Exchange.NYSE.holidays(2022)).doesNotContainKey(LocalDate.parse("2021-12-31"));
        assertThat(Exchange.B3.holidays(2023)).doesNotContainKey(LocalDate.parse("2023-11-20"));
    }

    @Test void statusExplainsWhyTheSessionIsClosed() {
        // 20/11/2026 (sexta), 14:00 em São Paulo: feriado da Consciência Negra.
        MarketHours.SessionStatus b3 = MarketHours.status(Exchange.B3, Instant.parse("2026-11-20T17:00:00Z"));
        assertThat(b3.open()).isFalse();
        assertThat(b3.reason()).startsWith("feriado").contains("Consciência Negra");
        // Mesmo instante: NYSE aberta (12:00 em Nova York).
        assertThat(MarketHours.isOpen(Exchange.NYSE, Instant.parse("2026-11-20T17:00:00Z"))).isTrue();
        assertThat(MarketHours.status(Exchange.NYSE, Instant.parse("2026-11-21T17:00:00Z")).reason()).isEqualTo("fim de semana");
        assertThat(MarketHours.status(Exchange.NYSE, Instant.parse("2026-11-20T23:00:00Z")).reason()).isEqualTo("fora do horário de pregão");
    }

    @Test void earlyCloseAndLateOpenChangeTheSession() {
        // 27/11/2026: NYSE fecha às 13:00 (18:00 UTC).
        assertThat(MarketHours.isOpen(Exchange.NYSE, Instant.parse("2026-11-27T17:30:00Z"))).isTrue();
        assertThat(MarketHours.isOpen(Exchange.NYSE, Instant.parse("2026-11-27T18:30:00Z"))).isFalse();
        // Quarta-feira de Cinzas 18/02/2026: B3 só abre às 13:00 (16:00 UTC).
        assertThat(MarketHours.isOpen(Exchange.B3, Instant.parse("2026-02-18T14:00:00Z"))).isFalse();
        assertThat(MarketHours.isOpen(Exchange.B3, Instant.parse("2026-02-18T16:30:00Z"))).isTrue();
    }

    @Test void lunchBreaksWeekendsAndUnverifiedCalendars() {
        // Tóquio: pausa de almoço 11:30–12:30 (02:30–03:30 UTC).
        assertThat(MarketHours.isOpen(Exchange.TSE, Instant.parse("2026-11-18T01:00:00Z"))).isTrue();
        assertThat(MarketHours.isOpen(Exchange.TSE, Instant.parse("2026-11-18T03:00:00Z"))).isFalse();
        // Tadawul negocia no domingo e fecha na sexta.
        assertThat(MarketHours.isOpen(Exchange.TADAWUL, Instant.parse("2026-11-22T08:00:00Z"))).isTrue();
        assertThat(MarketHours.isOpen(Exchange.TADAWUL, Instant.parse("2026-11-20T08:00:00Z"))).isFalse();
        // Bolsa sem calendário de feriados verificado avisa isso.
        assertThat(MarketHours.status(Exchange.ASX, Instant.parse("2026-11-18T01:00:00Z")).holidaysVerified()).isFalse();
        assertThat(MarketHours.status(Exchange.NYSE, Instant.parse("2026-11-18T15:00:00Z")).holidaysVerified()).isTrue();
    }

    @Test void globalMarketRunsFromSundayEveningToFridayEvening() {
        assertThat(MarketHours.isOpen(Exchange.GLOBAL_24H, Instant.parse("2026-11-17T17:00:00Z"))).isTrue();
        assertThat(MarketHours.isOpen(Exchange.GLOBAL_24H, Instant.parse("2026-11-21T17:00:00Z"))).isFalse();
        assertThat(MarketHours.isOpen(Exchange.GLOBAL_24H, Instant.parse("2026-11-22T22:30:00Z"))).isTrue();
    }
}
