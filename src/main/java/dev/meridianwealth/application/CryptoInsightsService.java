package dev.meridianwealth.application;

import dev.meridianwealth.application.port.CryptoInsightsPort;
import dev.meridianwealth.domain.crypto.FearGreed;
import dev.meridianwealth.domain.crypto.HourlyVolume;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Relógio de 24 horas e medo e ganância do cripto. Cada parte tem cache de 15 minutos, <b>último dado bom</b> se a fonte falha e espera de 2 minutos antes de tentar de novo
 * depois de uma falha, para uma fonte fora do ar não ser consultada a cada visita. O que não chegou vem em {@code unavailable}; nada é estimado.
 */
public class CryptoInsightsService {
    public static final Duration TTL = Duration.ofMinutes(15);
    public static final Duration BACKOFF = Duration.ofMinutes(2);
    public static final String SYMBOL = "BTC";
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    public record View(HourlyVolume.Profile hourly, String hourlySource, String zone, FearGreed fearGreed, Instant asOf, List<String> unavailable) {}

    private static final class Slot<T> {
        T value;
        Instant fetchedAt;
        Instant retryAt = Instant.MIN;
    }

    private final CryptoInsightsPort port;
    private final Clock clock;
    private final Slot<HourlyVolume.Profile> hourly = new Slot<>();
    private String hourlySource;
    private final Slot<FearGreed> fear = new Slot<>();

    public CryptoInsightsService(CryptoInsightsPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public synchronized View view() {
        Instant now = clock.instant();
        refresh(hourly, now, () -> port.hourly(SYMBOL).map(h -> {
            HourlyVolume.Profile p = HourlyVolume.profile(h.bars(), BRASILIA, now.toEpochMilli());
            if (p != null) hourlySource = h.source();
            return Optional.ofNullable(p);
        }).orElse(Optional.empty()));
        refresh(fear, now, port::fearGreed);
        List<String> missing = new ArrayList<>();
        if (hourly.value == null) missing.add("hourly");
        if (fear.value == null) missing.add("fearGreed");
        return new View(hourly.value, hourlySource, BRASILIA.getId(), fear.value, now, missing);
    }

    private static <T> void refresh(Slot<T> slot, Instant now, Supplier<Optional<T>> load) {
        boolean fresh = slot.value != null && slot.fetchedAt != null && now.isBefore(slot.fetchedAt.plus(TTL));
        if (fresh || now.isBefore(slot.retryAt)) return;
        Optional<T> got;
        try {
            got = load.get();
        } catch (RuntimeException e) {
            got = Optional.empty();
        }
        if (got.isPresent()) {
            slot.value = got.get();
            slot.fetchedAt = now;
            slot.retryAt = Instant.MIN;
        } else {
            slot.retryAt = now.plus(BACKOFF);
        }
    }
}
