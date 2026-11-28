package dev.meridianwealth.domain.crypto;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Perfil do volume negociado por hora do dia (o "relógio de 24 horas" do cripto): média do volume em dólares de cada hora, no fuso informado, sobre as últimas barras
 * horárias completas. Barra sem volume (zero, negativo ou fora do normal) é dado que falta e não entra na média; hora sem nenhuma amostra fica nula, nunca zero. A barra
 * da hora corrente é parcial e fica de fora. Sem pelo menos um dia de barras válidas não há perfil.
 */
public final class HourlyVolume {
    public static final int MAX_BARS = 168;
    public static final int MIN_BARS = 24;
    private static final long HOUR_MS = 3_600_000L;

    /** Uma barra de uma hora: início em milissegundos e volume em dólares. */
    public record Bar(long timeMillis, double volumeUsd) {}

    /** Média do volume da hora do dia {@code hour} (0 a 23) e o quanto ela é do pico (0 a 1); nulos quando nenhuma barra válida caiu nessa hora. */
    public record Hour(int hour, Double averageUsd, Double relative, int samples) {}

    public record Profile(List<Hour> hours, int peakHour, int bars, long fromMillis, long toMillis) {
        public Profile {
            hours = List.copyOf(hours);
        }
    }

    private HourlyVolume() {}

    /** @return o perfil, ou {@code null} quando há menos de {@link #MIN_BARS} barras válidas */
    public static Profile profile(List<Bar> bars, ZoneId zone, long nowMillis) {
        List<Bar> usable = bars.stream().filter(b -> b.timeMillis() + HOUR_MS <= nowMillis).sorted(Comparator.comparingLong(Bar::timeMillis)).toList();
        if (usable.size() > MAX_BARS) usable = usable.subList(usable.size() - MAX_BARS, usable.size());
        double[] sum = new double[24];
        int[] n = new int[24];
        int valid = 0;
        long from = Long.MAX_VALUE, to = Long.MIN_VALUE;
        for (Bar b : usable) {
            if (!(b.volumeUsd() > 0) || !Double.isFinite(b.volumeUsd())) continue;
            int h = Instant.ofEpochMilli(b.timeMillis()).atZone(zone).getHour();
            sum[h] += b.volumeUsd();
            n[h]++;
            valid++;
            from = Math.min(from, b.timeMillis());
            to = Math.max(to, b.timeMillis());
        }
        if (valid < MIN_BARS) return null;
        double peak = 0;
        int peakHour = 0;
        for (int h = 0; h < 24; h++) {
            if (n[h] > 0 && sum[h] / n[h] > peak) {
                peak = sum[h] / n[h];
                peakHour = h;
            }
        }
        List<Hour> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Double avg = n[h] == 0 ? null : sum[h] / n[h];
            hours.add(new Hour(h, avg, avg == null ? null : avg / peak, n[h]));
        }
        return new Profile(hours, peakHour, valid, from, to + HOUR_MS);
    }
}
