package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rate em BigDecimal (ADR 002) e a regra de defasagem dos indicadores oficiais. */
class RateTest {
    @Test void percentAndFractionKeepTheExactDecimal() {
        Rate selic = Rate.ofPercent("13.75");
        assertThat(selic.percent()).isEqualByComparingTo("13.75");
        assertThat(selic.fraction()).isEqualByComparingTo("0.1375");
        assertThat(new BigDecimal("0.1").add(new BigDecimal("0.2"))).as("sem erro de binário").isEqualByComparingTo("0.3");
    }

    @Test void dailyCdiCompoundedOver252DaysGivesTheOfficialAnnualizedCdi() {
        // SGS 12 (0,050788% ao dia) e SGS 4389 (13,65% ao ano, base 252) publicados em 2026-11-19.
        Rate annual = Rate.ofPercent("0.050788").compoundedOver(252);
        assertThat(annual.rounded(2).percent()).isEqualByComparingTo("13.65");
    }

    @Test void twelveMonthlyIpcaRatesCompoundToTheOfficialTwelveMonthFigure() {
        // SGS 433 (nov/2025 a out/2026) e SGS 13522 (4,63% em 12 meses em out/2026).
        String[] monthly = {"0.18", "0.33", "0.33", "0.70", "0.88", "0.67", "0.58", "0.16", "0.07", "-0.32", "0.82", "0.14"};
        BigDecimal factor = BigDecimal.ONE;
        for (String m : monthly) factor = factor.multiply(BigDecimal.ONE.add(Rate.ofPercent(m).fraction()));
        assertThat(new Rate(factor.subtract(BigDecimal.ONE).movePointRight(2)).rounded(2).percent()).isEqualByComparingTo("4.63");
    }

    @Test void percentOfTheCdiAppliesToThePeriodRate() {
        assertThat(Rate.ofPercent("0.05").percentOf(new BigDecimal("110")).percent()).isEqualByComparingTo("0.055");
        assertThat(Rate.ofPercent("0.05").percentOf(new BigDecimal("100")).percent()).isEqualByComparingTo("0.05");
    }

    @Test void roundingIsBankersAndCompoundingValidatesInput() {
        assertThat(Rate.ofPercent("2.5").rounded(0).percent()).isEqualByComparingTo("2");
        assertThat(Rate.ofPercent("3.5").rounded(0).percent()).isEqualByComparingTo("4");
        assertThat(Rate.ofPercent("5").compoundedOver(0).percent()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> Rate.ofPercent("5").compoundedOver(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Rate.ofPercent("1").compareTo(Rate.ofPercent("2"))).isNegative();
    }

    @Test void dailySeriesBecomeStaleAfterFiveDaysAndMonthlyAfterSeventyFive() {
        LocalDate today = LocalDate.parse("2026-11-21");
        RateObservation cdi = new RateObservation(RateSeries.CDI_DAILY, new BigDecimal("0.050788"), LocalDate.parse("2026-11-16"));
        assertThat(cdi.ageDays(today)).isEqualTo(5);
        assertThat(cdi.isStale(today)).isFalse();
        assertThat(cdi.isStale(today.plusDays(1))).isTrue();

        RateObservation ipca = new RateObservation(RateSeries.IPCA_MONTHLY, new BigDecimal("0.14"), LocalDate.parse("2026-10-01"));
        assertThat(ipca.ageDays(today)).isEqualTo(51);
        assertThat(ipca.isStale(today)).isFalse();
        assertThat(ipca.isStale(LocalDate.parse("2026-12-16"))).isTrue();
        assertThat(cdi.ageDays(LocalDate.parse("2026-11-01"))).as("data futura não gera idade negativa").isZero();
    }

    @Test void brazilRatesNeverInventMissingIndicators() {
        BrazilRates none = BrazilRates.unavailable();
        assertThat(none.fetchedAt()).isNull();
        assertThat(none.get(RateSeries.SELIC_TARGET)).isEmpty();

        RateObservation selic = new RateObservation(RateSeries.SELIC_TARGET, new BigDecimal("13.75"), LocalDate.parse("2026-11-21"));
        BrazilRates one = none.with(RateSeries.SELIC_TARGET, selic, Instant.parse("2026-11-21T10:00:00Z"));
        assertThat(one.get(RateSeries.SELIC_TARGET)).contains(selic);
        assertThat(one.get(RateSeries.CDI_DAILY)).isEmpty();
        assertThat(none.get(RateSeries.SELIC_TARGET)).as("imutável").isEmpty();
        assertThat(new BrazilRates(Map.of(RateSeries.SELIC_TARGET, selic), null).observations()).hasSize(1);
    }

    @Test void seriesCatalogMatchesTheConfirmedSgsCodes() {
        assertThat(RateSeries.SELIC_TARGET.sgsCode()).isEqualTo(432);
        assertThat(RateSeries.CDI_DAILY.sgsCode()).isEqualTo(12);
        assertThat(RateSeries.CDI_ANNUAL.sgsCode()).isEqualTo(4389);
        assertThat(RateSeries.IPCA_MONTHLY.sgsCode()).isEqualTo(433);
        assertThat(RateSeries.IPCA_12M.sgsCode()).isEqualTo(13522);
        assertThat(RateSeries.PTAX_BUY.sgsCode()).as("PTAX vem do Olinda").isNull();
        assertThat(RateSeries.PTAX_SELL.sgsCode()).isNull();
    }
}
