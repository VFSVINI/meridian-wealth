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
        assertThat(r.observations().values()).noneMatch(o -> o.isStale(TODAY));
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

    @Test void disabledNeverCallsTheBcb() {
        FixtureHttpGateway gw = new FixtureHttpGateway();
        LiveBrazilRates rates = live(gw, false);
        rates.refresh();
        assertThat(gw.requested()).isEmpty();
        assertThat(rates.current().observations()).isEmpty();
    }
}
