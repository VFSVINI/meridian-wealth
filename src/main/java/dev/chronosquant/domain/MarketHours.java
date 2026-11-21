package dev.chronosquant.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pregão aberto ou fechado por região (sem feriados). Câmbio e commodities: 24h de segunda a sexta. */
public final class MarketHours {
    private MarketHours() {}

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    public static boolean isOpen(String region, String assetClass, Instant now) {
        if ("Câmbio".equals(assetClass) || "Commodity".equals(assetClass)) return globalOpen(now);
        return switch (region) {
            case "EUA" -> within(now.atZone(NY), LocalTime.of(9, 30), LocalTime.of(16, 0));
            case "Europa" -> within(now.atZone(BERLIN), LocalTime.of(9, 0), LocalTime.of(17, 30));
            default -> globalOpen(now);
        };
    }

    private static boolean within(ZonedDateTime t, LocalTime open, LocalTime close) {
        DayOfWeek d = t.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return false;
        LocalTime lt = t.toLocalTime();
        return !lt.isBefore(open) && lt.isBefore(close);
    }

    private static boolean globalOpen(Instant now) {
        ZonedDateTime u = now.atZone(ZoneId.of("UTC"));
        return switch (u.getDayOfWeek()) {
            case SATURDAY -> false;
            case SUNDAY -> u.toLocalTime().isAfter(LocalTime.of(22, 0));
            case FRIDAY -> u.toLocalTime().isBefore(LocalTime.of(22, 0));
            default -> true;
        };
    }
}
