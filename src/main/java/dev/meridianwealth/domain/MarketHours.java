package dev.meridianwealth.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/** Estado do pregão por bolsa: fuso, sessões, dias úteis, feriados, abertura atrasada e fechamento antecipado. */
public final class MarketHours {
    private MarketHours() {}

    /** Situação do pregão com o motivo em português, para exibir ao usuário. */
    public record SessionStatus(Exchange exchange, boolean open, String reason, String holiday,
                                ZonedDateTime localTime, boolean holidaysVerified) {}

    public static boolean isOpen(Exchange exchange, Instant now) {
        return status(exchange, now).open();
    }

    public static SessionStatus status(Exchange ex, Instant now) {
        ZonedDateTime local = now.atZone(ex.zone());
        if (ex == Exchange.CRYPTO) {
            return new SessionStatus(ex, true, "mercado aberto 24/7", null, local, true);
        }
        if (ex == Exchange.GLOBAL_24H) {
            boolean open = globalOpen(now);
            return new SessionStatus(ex, open, open ? "mercado aberto 24h" : "fim de semana", null, local, true);
        }
        LocalDate day = local.toLocalDate();
        if (!ex.tradingDays().contains(local.getDayOfWeek())) {
            return new SessionStatus(ex, false, "fim de semana", null, local, ex.holidaysVerified());
        }
        Optional<String> holiday = ex.holiday(day);
        if (holiday.isPresent()) {
            return new SessionStatus(ex, false, "feriado: " + holiday.get(), holiday.get(), local, ex.holidaysVerified());
        }
        LocalTime t = local.toLocalTime();
        LocalTime lateOpen = ex.lateOpen(day).orElse(null);
        LocalTime earlyClose = ex.earlyClose(day).orElse(null);
        for (Exchange.Session s : ex.sessions()) {
            LocalTime open = lateOpen != null && lateOpen.isAfter(s.open()) ? lateOpen : s.open();
            LocalTime close = earlyClose != null && earlyClose.isBefore(s.close()) ? earlyClose : s.close();
            if (!t.isBefore(open) && t.isBefore(close)) {
                String why = earlyClose != null ? "pregão aberto (fecha mais cedo, às " + earlyClose + ")" : "pregão aberto";
                return new SessionStatus(ex, true, why, null, local, ex.holidaysVerified());
            }
        }
        return new SessionStatus(ex, false, "fora do horário de pregão", null, local, ex.holidaysVerified());
    }

    private static boolean globalOpen(Instant now) {
        ZonedDateTime u = now.atZone(ZoneOffset.UTC);
        DayOfWeek d = u.getDayOfWeek();
        return switch (d) {
            case SATURDAY -> false;
            case SUNDAY -> !u.toLocalTime().isBefore(LocalTime.of(22, 0));
            case FRIDAY -> u.toLocalTime().isBefore(LocalTime.of(22, 0));
            default -> true;
        };
    }
}
