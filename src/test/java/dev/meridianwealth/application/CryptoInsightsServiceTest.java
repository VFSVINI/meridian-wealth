package dev.meridianwealth.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.CryptoInsightsPort;
import dev.meridianwealth.domain.crypto.FearGreed;
import dev.meridianwealth.domain.crypto.HourlyVolume.Bar;
import dev.meridianwealth.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CryptoInsightsServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");

    private static List<Bar> bars() {
        List<Bar> out = new ArrayList<>();
        for (int i = 0; i < 7 * 24; i++) out.add(new Bar(NOW.toEpochMilli() - (7 * 24 - i) * 3_600_000L, 100 + i % 24));
        return out;
    }

    private static final class Fake implements CryptoInsightsPort {
        boolean up = true;
        final AtomicInteger hourlyCalls = new AtomicInteger(), fearCalls = new AtomicInteger();

        @Override public Optional<Hourly> hourly(String symbol) { hourlyCalls.incrementAndGet(); return up ? Optional.of(new Hourly("Coinbase", bars())) : Optional.empty(); }

        @Override public Optional<FearGreed> fearGreed() { fearCalls.incrementAndGet(); return up ? Optional.of(FearGreed.of(62, "Greed", 1)) : Optional.empty(); }
    }

    @Test void buildsTheProfileAndTheIndexAndCachesFor15Minutes() {
        Fake port = new Fake();
        MutableClock clock = new MutableClock(NOW);
        CryptoInsightsService s = new CryptoInsightsService(port, clock);
        CryptoInsightsService.View v = s.view();
        assertThat(v.hourly().hours()).hasSize(24);
        assertThat(v.hourlySource()).isEqualTo("Coinbase");
        assertThat(v.zone()).isEqualTo("America/Sao_Paulo");
        assertThat(v.fearGreed().value()).isEqualTo(62);
        assertThat(v.unavailable()).isEmpty();
        clock.advance(Duration.ofMinutes(14));
        s.view();
        assertThat(port.hourlyCalls.get()).as("dentro do prazo sai do cache").isEqualTo(1);
        clock.advance(Duration.ofMinutes(2));
        s.view();
        assertThat(port.hourlyCalls.get()).isEqualTo(2);
        assertThat(port.fearCalls.get()).isEqualTo(2);
    }

    @Test void whenTheSourcesAreDownNothingIsEstimatedAndTheyAreNotRetriedBeforeTheBackoff() {
        Fake port = new Fake();
        port.up = false;
        MutableClock clock = new MutableClock(NOW);
        CryptoInsightsService s = new CryptoInsightsService(port, clock);
        CryptoInsightsService.View v = s.view();
        assertThat(v.hourly()).isNull();
        assertThat(v.fearGreed()).isNull();
        assertThat(v.unavailable()).containsExactly("hourly", "fearGreed");
        clock.advance(Duration.ofMinutes(1));
        s.view();
        assertThat(port.hourlyCalls.get()).as("a espera de 2 minutos evita consultar a cada visita").isEqualTo(1);
        clock.advance(Duration.ofMinutes(2));
        port.up = true;
        assertThat(s.view().unavailable()).isEmpty();
        assertThat(port.hourlyCalls.get()).isEqualTo(2);
    }

    @Test void theLastGoodDataSurvivesAFailureAfterTheCacheExpires() {
        Fake port = new Fake();
        MutableClock clock = new MutableClock(NOW);
        CryptoInsightsService s = new CryptoInsightsService(port, clock);
        s.view();
        port.up = false;
        clock.advance(Duration.ofMinutes(20));
        CryptoInsightsService.View v = s.view();
        assertThat(v.hourly()).as("último dado bom").isNotNull();
        assertThat(v.fearGreed().value()).isEqualTo(62);
        assertThat(v.unavailable()).isEmpty();
        assertThat(port.hourlyCalls.get()).isEqualTo(2);
        clock.advance(Duration.ofMinutes(1));
        s.view();
        assertThat(port.hourlyCalls.get()).as("depois da falha espera antes de tentar de novo").isEqualTo(2);
    }

    @Test void anExceptionFromTheAdapterCountsAsAFailureNotACrash() {
        CryptoInsightsPort boom = new CryptoInsightsPort() {
            @Override public Optional<Hourly> hourly(String symbol) { throw new IllegalStateException("x"); }

            @Override public Optional<FearGreed> fearGreed() { throw new IllegalStateException("y"); }
        };
        CryptoInsightsService.View v = new CryptoInsightsService(boom, new MutableClock(NOW)).view();
        assertThat(v.unavailable()).containsExactly("hourly", "fearGreed");
    }

    @Test void tooFewBarsMeansNoProfileInsteadOfAGuess() {
        CryptoInsightsPort thin = new CryptoInsightsPort() {
            @Override public Optional<Hourly> hourly(String symbol) { return Optional.of(new Hourly("Coinbase", bars().subList(0, 10))); }

            @Override public Optional<FearGreed> fearGreed() { return Optional.empty(); }
        };
        assertThat(new CryptoInsightsService(thin, new MutableClock(NOW)).view().unavailable()).contains("hourly");
    }
}
