package dev.meridianwealth.infrastructure.bcb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.BrazilRates;
import dev.meridianwealth.domain.RateObservation;
import dev.meridianwealth.domain.RateSeries;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LiveBrazilRatesTest {
    private static final Instant NOW = Instant.parse("2026-11-21T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TODAY = LocalDate.parse("2026-11-21");

    private static LiveBrazilRates live(HttpGateway gw, boolean enabled) {
        return new LiveBrazilRates(new BcbClient(gw, ResilientExecutor.withDefaults("BCB", 1000, 1000.0)), enabled, CLOCK);
    }

    @Test void refreshFillsEveryIndicatorFromTheRealFixtures() {
        FixtureHttpGateway gw = new FixtureHttpGateway();
        LiveBrazilRates rates = live(gw, true);
        assertThat(rates.current().observations()).isEmpty();
        rates.refresh();

        BrazilRates r = rates.current();
        assertThat(r.fetchedAt()).isEqualTo(NOW);
        assertThat(r.get(RateSeries.SELIC_TARGET)).get().extracting(RateObservation::value).isEqualTo(new java.math.BigDecimal("13.75"));
        assertThat(r.get(RateSeries.CDI_DAILY)).get().extracting(RateObservation::date).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(r.get(RateSeries.CDI_ANNUAL).orElseThrow().value()).isEqualByComparingTo("13.65");
        assertThat(r.get(RateSeries.IPCA_MONTHLY).orElseThrow().value()).isEqualByComparingTo("0.14");
        assertThat(r.get(RateSeries.IPCA_12M).orElseThrow().value()).isEqualByComparingTo("4.63");
        assertThat(r.get(RateSeries.PTAX_BUY).orElseThrow().value()).isEqualByComparingTo("5.8883");
        assertThat(r.get(RateSeries.PTAX_SELL).orElseThrow().value()).isEqualByComparingTo("5.8889");
        assertThat(r.get(RateSeries.PTAX_SELL).orElseThrow().date()).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(r.observations().values()).noneMatch(o -> o.isStale(TODAY, NOW));
        assertThat(r.observations().values()).allMatch(o -> o.confirmedAt().equals(NOW));
    }

    @Test void queriesUseTheDocumentedWindows() {
        FixtureHttpGateway gw = new FixtureHttpGateway();
        live(gw, true).refresh();
        assertThat(gw.requested()).anyMatch(u -> u.contains("sgs.432/dados?formato=json&dataInicial=07/10/2026&dataFinal=21/11/2026"));
        assertThat(gw.requested()).anyMatch(u -> u.contains("sgs.433/dados?formato=json&dataInicial=17/10/2025&dataFinal=21/11/2026"));
        assertThat(gw.requested()).anyMatch(u -> u.contains("@dataInicial='11-11-2026'&@dataFinalCotacao='11-21-2026'"));
        assertThat(gw.requested()).as("5 séries SGS e uma consulta de PTAX (compra e venda juntas)").hasSize(6);
    }

    @Test void oneFailingSeriesDoesNotBlockTheOthersAndIsFilledInOnTheNextRound() {
        AtomicBoolean ipcaDown = new AtomicBoolean(true);
        FixtureHttpGateway fixtures = new FixtureHttpGateway();
        HttpGateway gw = (url, h) -> {
            if (url.contains("sgs.433/") && ipcaDown.get()) throw new IOException("HTTP 503");
            return fixtures.get(url, h);
        };
        LiveBrazilRates rates = live(gw, true);
        rates.refresh();
        assertThat(rates.current().get(RateSeries.IPCA_MONTHLY)).as("sem dado, nunca estimado").isEmpty();
        assertThat(rates.current().get(RateSeries.SELIC_TARGET)).isPresent();
        assertThat(rates.current().get(RateSeries.PTAX_SELL)).isPresent();

        ipcaDown.set(false);
        rates.refresh();
        assertThat(rates.current().get(RateSeries.IPCA_MONTHLY)).isPresent();
    }

    @Test void aLaterOutageKeepsTheLastGoodValue() {
        AtomicBoolean down = new AtomicBoolean();
        FixtureHttpGateway fixtures = new FixtureHttpGateway();
        HttpGateway gw = (url, h) -> {
            if (down.get()) throw new IOException("sem rede");
            return fixtures.get(url, h);
        };
        LiveBrazilRates rates = live(gw, true);
        rates.refresh();
        BrazilRates good = rates.current();
        down.set(true);
        rates.refresh();
        assertThat(rates.current()).isEqualTo(good);
    }

    @Test void anOlderValueNeverReplacesANewerOne() {
        FixtureHttpGateway fixtures = new FixtureHttpGateway();
        AtomicBoolean stale = new AtomicBoolean();
        HttpGateway gw = (url, h) -> stale.get() && url.contains("sgs.432/")
                ? "[{\"data\":\"01/11/2026\",\"valor\":\"14.25\"}]" : fixtures.get(url, h);
        LiveBrazilRates rates = live(gw, true);
        rates.refresh();
        stale.set(true);
        rates.refresh();
        assertThat(rates.current().get(RateSeries.SELIC_TARGET).orElseThrow().value()).isEqualByComparingTo("13.75");
    }

    @Test void anEqualValueRenewsTheConfirmationButAnOlderOneIsStillIgnored() {
        FixtureHttpGateway fixtures = new FixtureHttpGateway();
        java.util.concurrent.atomic.AtomicReference<Instant> clockNow = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock moving = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("America/Sao_Paulo"); }

            @Override public Clock withZone(ZoneId zone) { return this; }

            @Override public Instant instant() { return clockNow.get(); }
        };
        LiveBrazilRates rates = new LiveBrazilRates(new BcbClient(fixtures, ResilientExecutor.withDefaults("BCB", 1000, 1000.0)), true, moving);
        rates.refresh();
        Instant firstConfirmation = rates.current().get(RateSeries.CDI_DAILY).orElseThrow().confirmedAt();

        clockNow.set(NOW.plus(java.time.Duration.ofMinutes(30)));
        rates.refresh();
        RateObservation again = rates.current().get(RateSeries.CDI_DAILY).orElseThrow();
        assertThat(again.value()).isEqualByComparingTo("0.050788");
        assertThat(again.confirmedAt()).as("mesmo valor e mesma data: só a confirmação anda").isAfter(firstConfirmation);
        assertThat(rates.current().fetchedAt()).isEqualTo(NOW.plus(java.time.Duration.ofMinutes(30)));
    }

    @Test void concurrentRefreshesNeverOverlapOnTheNetwork() throws Exception {
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger peak = new java.util.concurrent.atomic.AtomicInteger();
        FixtureHttpGateway fixtures = new FixtureHttpGateway();
        HttpGateway gw = (url, h) -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(15);
                return fixtures.get(url, h);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } finally {
                inFlight.decrementAndGet();
            }
        };
        LiveBrazilRates rates = live(gw, true);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            jobs.add(pool.submit(() -> {
                start.await();
                rates.refresh();
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> f : jobs) f.get();
        pool.shutdown();
        assertThat(peak.get()).as("duas rodadas simultâneas disputariam a cota do BCB e o mesmo retrato").isEqualTo(1);
        assertThat(rates.current().observations()).hasSize(RateSeries.values().length);
    }

    @Test void disabledNeverCallsTheBcb() {
        FixtureHttpGateway gw = new FixtureHttpGateway();
        LiveBrazilRates rates = live(gw, false);
        rates.refresh();
        assertThat(gw.requested()).isEmpty();
        assertThat(rates.current().observations()).isEmpty();
    }
}
