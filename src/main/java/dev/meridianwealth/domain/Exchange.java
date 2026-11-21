package dev.meridianwealth.domain;

import static dev.meridianwealth.domain.HolidayRules.easterSunday;
import static dev.meridianwealth.domain.HolidayRules.lastWeekday;
import static dev.meridianwealth.domain.HolidayRules.nthWeekday;
import static dev.meridianwealth.domain.HolidayRules.ukSubstitute;
import static dev.meridianwealth.domain.HolidayRules.usObserved;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bolsas acompanhadas, com fuso, sessões regulares, dias úteis e calendário de feriados.
 *
 * <p>Os calendários de NYSE, B3 e Xetra foram conferidos contra as páginas oficiais de 2026 (ver
 * {@code docs/mercados.md}); o da LSE segue as regras das bank holidays inglesas e foi conferido nas
 * datas que a página oficial publica. Bolsas com {@code holidaysVerified == false} não têm calendário
 * de feriados implementado: o sistema avisa isso em vez de adivinhar datas.
 */
public enum Exchange {
    NYSE("NYSE / Nasdaq", "EUA", "America/New_York", true, sessions(9, 30, 16, 0)),
    B3("B3", "Brasil", "America/Sao_Paulo", true, sessions(10, 0, 17, 0)),
    XETRA("Xetra", "Europa", "Europe/Berlin", true, sessions(9, 0, 17, 30)),
    LSE("London Stock Exchange", "Europa", "Europe/London", true, sessions(8, 0, 16, 30)),
    EURONEXT_AMS("Euronext Amsterdam", "Europa", "Europe/Amsterdam", false, sessions(9, 0, 17, 30)),
    TSE("Tóquio (JPX)", "Ásia", "Asia/Tokyo", false, sessions(9, 0, 11, 30, 12, 30, 15, 30)),
    HKEX("Hong Kong (HKEX)", "Ásia", "Asia/Hong_Kong", false, sessions(9, 30, 12, 0, 13, 0, 16, 0)),
    ASX("ASX", "Oceania", "Australia/Sydney", false, sessions(10, 0, 16, 0)),
    TADAWUL("Tadawul", "Oriente Médio/África", "Asia/Riyadh", false, sessions(10, 0, 15, 0)),
    JSE("Johannesburgo (JSE)", "Oriente Médio/África", "Africa/Johannesburg", false, sessions(9, 0, 17, 0)),
    /** Câmbio e futuros de commodities: domingo 22:00 UTC a sexta 22:00 UTC, sem feriados. */
    GLOBAL_24H("Mercado global 24h", "Global", "UTC", true, List.of()),
    /** Criptoativos: negociam 24 horas, 7 dias por semana, sem feriados; a barra diária fecha à meia-noite UTC. */
    CRYPTO("Cripto 24/7", "Cripto", "UTC", true, List.of());

    /** Sessão contínua [open, close) no fuso da bolsa. */
    public record Session(LocalTime open, LocalTime close) {}

    private final String label;
    private final String region;
    private final ZoneId zone;
    private final boolean holidaysVerified;
    private final List<Session> sessions;

    Exchange(String label, String region, String zone, boolean holidaysVerified, List<Session> sessions) {
        this.label = label;
        this.region = region;
        this.zone = ZoneId.of(zone);
        this.holidaysVerified = holidaysVerified;
        this.sessions = sessions;
    }

    public String label() { return label; }

    public String region() { return region; }

    public ZoneId zone() { return zone; }

    public boolean holidaysVerified() { return holidaysVerified; }

    public List<Session> sessions() { return sessions; }

    /** Dias de negociação regulares: Tadawul negocia de domingo a quinta; as demais, de segunda a sexta. */
    public Set<DayOfWeek> tradingDays() {
        return this == TADAWUL
                ? EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
                : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    /** Feriados do ano (data local → nome), já com as regras de observância de cada bolsa. */
    public Map<LocalDate, String> holidays(int year) {
        Map<LocalDate, String> h = new LinkedHashMap<>();
        LocalDate easter = easterSunday(year);
        switch (this) {
            case NYSE -> {
                LocalDate newYear = LocalDate.of(year, 1, 1);
                // A NYSE não fecha na sexta 31/12 quando o Ano-Novo cai no sábado.
                if (newYear.getDayOfWeek() != DayOfWeek.SATURDAY) h.put(usObserved(newYear), "Ano-Novo");
                h.put(nthWeekday(year, Month.JANUARY, 3, DayOfWeek.MONDAY), "Martin Luther King Jr. Day");
                h.put(nthWeekday(year, Month.FEBRUARY, 3, DayOfWeek.MONDAY), "Washington's Birthday");
                h.put(easter.minusDays(2), "Sexta-feira Santa");
                h.put(lastWeekday(year, Month.MAY, DayOfWeek.MONDAY), "Memorial Day");
                if (year >= 2022) h.put(usObserved(LocalDate.of(year, 6, 19)), "Juneteenth");
                h.put(usObserved(LocalDate.of(year, 7, 4)), "Independence Day");
                h.put(nthWeekday(year, Month.SEPTEMBER, 1, DayOfWeek.MONDAY), "Labor Day");
                h.put(nthWeekday(year, Month.NOVEMBER, 4, DayOfWeek.THURSDAY), "Thanksgiving");
                h.put(usObserved(LocalDate.of(year, 12, 25)), "Natal");
            }
            case B3 -> {
                h.put(LocalDate.of(year, 1, 1), "Confraternização Universal");
                h.put(easter.minusDays(48), "Carnaval");
                h.put(easter.minusDays(47), "Carnaval");
                h.put(easter.minusDays(2), "Sexta-feira Santa");
                h.put(LocalDate.of(year, 4, 21), "Tiradentes");
                h.put(LocalDate.of(year, 5, 1), "Dia do Trabalho");
                h.put(easter.plusDays(60), "Corpus Christi");
                h.put(LocalDate.of(year, 9, 7), "Independência do Brasil");
                h.put(LocalDate.of(year, 10, 12), "Nossa Senhora Aparecida");
                h.put(LocalDate.of(year, 11, 2), "Finados");
                h.put(LocalDate.of(year, 11, 15), "Proclamação da República");
                // Feriado nacional desde 2024 (Lei 14.759/2023).
                if (year >= 2024) h.put(LocalDate.of(year, 11, 20), "Dia Nacional de Zumbi e da Consciência Negra");
                h.put(LocalDate.of(year, 12, 24), "Véspera de Natal");
                h.put(LocalDate.of(year, 12, 25), "Natal");
                h.put(LocalDate.of(year, 12, 31), "Último dia do ano (sem pregão)");
            }
            case XETRA -> {
                h.put(LocalDate.of(year, 1, 1), "Ano-Novo");
                h.put(easter.minusDays(2), "Sexta-feira Santa");
                h.put(easter.plusDays(1), "Segunda-feira de Páscoa");
                h.put(LocalDate.of(year, 5, 1), "Dia do Trabalho");
                h.put(LocalDate.of(year, 12, 24), "Véspera de Natal");
                h.put(LocalDate.of(year, 12, 25), "Natal");
                h.put(LocalDate.of(year, 12, 26), "Segundo dia de Natal");
                h.put(LocalDate.of(year, 12, 31), "Véspera de Ano-Novo");
            }
            case LSE -> {
                h.put(ukSubstitute(LocalDate.of(year, 1, 1)), "Ano-Novo");
                h.put(easter.minusDays(2), "Sexta-feira Santa");
                h.put(easter.plusDays(1), "Segunda-feira de Páscoa");
                h.put(nthWeekday(year, Month.MAY, 1, DayOfWeek.MONDAY), "Early May Bank Holiday");
                h.put(lastWeekday(year, Month.MAY, DayOfWeek.MONDAY), "Spring Bank Holiday");
                h.put(lastWeekday(year, Month.AUGUST, DayOfWeek.MONDAY), "Summer Bank Holiday");
                LocalDate christmas = ukSubstitute(LocalDate.of(year, 12, 25));
                h.put(christmas, "Natal");
                h.put(ukSubstitute(christmas.plusDays(1)), "Boxing Day");
            }
            default -> { }
        }
        return h;
    }

    public Optional<String> holiday(LocalDate date) {
        return Optional.ofNullable(holidays(date.getYear()).get(date));
    }

    /** Fechamento antecipado no horário local, quando a fonte oficial publica a hora. */
    public Optional<LocalTime> earlyClose(LocalDate date) {
        if (this == NYSE) {
            LocalDate dayAfterThanksgiving = nthWeekday(date.getYear(), Month.NOVEMBER, 4, DayOfWeek.THURSDAY).plusDays(1);
            LocalDate christmasEve = LocalDate.of(date.getYear(), 12, 24);
            boolean weekday = date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
            if (date.equals(dayAfterThanksgiving) || (date.equals(christmasEve) && weekday && holiday(date).isEmpty())) {
                return Optional.of(LocalTime.of(13, 0));
            }
        }
        if (this == LSE && (date.getMonthValue() == 12 && (date.getDayOfMonth() == 24 || date.getDayOfMonth() == 31))) {
            return Optional.of(LocalTime.of(12, 30));
        }
        return Optional.empty();
    }

    /** Abertura atrasada (B3 na Quarta-feira de Cinzas abre às 13h). */
    public Optional<LocalTime> lateOpen(LocalDate date) {
        if (this == B3 && date.equals(easterSunday(date.getYear()).minusDays(46))) return Optional.of(LocalTime.of(13, 0));
        return Optional.empty();
    }

    private static List<Session> sessions(int... hm) {
        Session[] out = new Session[hm.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Session(LocalTime.of(hm[i * 4], hm[i * 4 + 1]), LocalTime.of(hm[i * 4 + 2], hm[i * 4 + 3]));
        }
        return List.of(out);
    }
}
