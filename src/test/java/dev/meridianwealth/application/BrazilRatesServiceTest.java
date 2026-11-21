package dev.meridianwealth.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.BrazilRatesService.Status;
import dev.meridianwealth.domain.BrazilRates;
import dev.meridianwealth.domain.RateObservation;
import dev.meridianwealth.domain.RateSeries;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrazilRatesServiceTest {
    private static BrazilRatesService service(BrazilRates rates, String today) {
        Clock clock = Clock.fixed(Instant.parse(today + "T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        return new BrazilRatesService(() -> rates, clock);
    }

    private static RateObservation obs(RateSeries s, String value, String date) {
        return new RateObservation(s, new BigDecimal(value), LocalDate.parse(date), Instant.parse("2026-11-21T12:00:00Z"));
    }

    @Test void everyIndicatorAppearsEvenWithoutDataAndNothingIsInvented() {
        List<BrazilRatesService.Item> items = service(BrazilRates.unavailable(), "2026-11-21").items();
        assertThat(items).hasSize(RateSeries.values().length);
        assertThat(items).allSatisfy(i -> {
            assertThat(i.status()).isEqualTo(Status.UNAVAILABLE);
            assertThat(i.observation()).isNull();
            assertThat(i.ageDays()).isNull();
        });
        assertThat(service(BrazilRates.unavailable(), "2026-11-21").fetchedAt()).isNull();
    }

    @Test void statusFollowsThePublicationFrequencyOfEachSeries() {
        BrazilRates rates = new BrazilRates(Map.of(
                RateSeries.CDI_DAILY, obs(RateSeries.CDI_DAILY, "0.050788", "2026-11-19"),
                RateSeries.SELIC_TARGET, obs(RateSeries.SELIC_TARGET, "13.75", "2026-11-10"),
                RateSeries.IPCA_MONTHLY, obs(RateSeries.IPCA_MONTHLY, "0.14", "2026-10-01")), Instant.parse("2026-11-21T12:00:00Z"));
        BrazilRatesService s = service(rates, "2026-11-21");
        Map<RateSeries, BrazilRatesService.Item> byKey = new java.util.EnumMap<>(RateSeries.class);
        s.items().forEach(i -> byKey.put(i.series(), i));

        assertThat(byKey.get(RateSeries.CDI_DAILY).status()).isEqualTo(Status.OK);
        assertThat(byKey.get(RateSeries.CDI_DAILY).ageDays()).isEqualTo(2);
        assertThat(byKey.get(RateSeries.SELIC_TARGET).status()).as("série preenchida para a frente: a data não conta, e a confirmação é de 3 h atrás").isEqualTo(Status.OK);
        assertThat(byKey.get(RateSeries.IPCA_MONTHLY).status()).as("51 dias é normal para o IPCA mensal").isEqualTo(Status.OK);
        assertThat(byKey.get(RateSeries.PTAX_SELL).status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(s.fetchedAt()).isEqualTo(Instant.parse("2026-11-21T12:00:00Z"));
        assertThat(s.today()).isEqualTo(LocalDate.parse("2026-11-21"));
    }

    @Test void staleWhenTheCollectionStoppedEvenIfTheDateIsToday() {
        BrazilRates rates = new BrazilRates(Map.of(
                RateSeries.SELIC_TARGET, obs(RateSeries.SELIC_TARGET, "13.75", "2026-11-21"),
                RateSeries.PTAX_SELL, obs(RateSeries.PTAX_SELL, "5.88890", "2026-11-19")), Instant.parse("2026-11-21T12:00:00Z"));
        // Cinco horas depois da última confirmação (12:00 UTC), com a data da Selic ainda "de hoje".
        Clock later = Clock.fixed(Instant.parse("2026-11-21T17:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        Map<RateSeries, BrazilRatesService.Item> byKey = new java.util.EnumMap<>(RateSeries.class);
        new BrazilRatesService(() -> rates, later).items().forEach(i -> byKey.put(i.series(), i));
        assertThat(byKey.get(RateSeries.SELIC_TARGET).status()).isEqualTo(Status.STALE);
        assertThat(byKey.get(RateSeries.PTAX_SELL).status()).isEqualTo(Status.STALE);
        assertThat(byKey.get(RateSeries.CDI_DAILY).status()).as("nunca coletado continua sem dado").isEqualTo(Status.UNAVAILABLE);
    }

    @Test void todayFollowsTheBrasiliaCalendarNotUtc() {
        // 23:30 em Brasília de 20/11 já é 21/11 em UTC; o "hoje" do indicador é o de Brasília.
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:30:00Z"), ZoneId.of("America/Sao_Paulo"));
        assertThat(new BrazilRatesService(BrazilRates::unavailable, clock).today()).isEqualTo(LocalDate.parse("2026-11-20"));
    }
}
