package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoRiskAndHoursTest {
    @Test void escalationHeadlineScoresHighAndMapsSectors() {
        NewsItem n = GeoRiskScorer.score("Missile strike escalates war, oil prices jump", "t");
        assertThat(n.risk()).isGreaterThan(0.8);
        assertThat(n.sectors()).contains("energia", "defesa");
    }

    @Test void deescalationLowersRisk() {
        assertThat(GeoRiskScorer.score("Ceasefire agreement reached after war talks", "t").risk()).isLessThan(0.3);
    }

    @Test void neutralHeadlineIsZeroAndAggregateIsBounded() {
        NewsItem calm = GeoRiskScorer.score("Local bakery wins award", "t");
        assertThat(calm.risk()).isZero();
        List<NewsItem> hot = List.of(GeoRiskScorer.score("War and sanctions deepen", "t"), GeoRiskScorer.score("Invasion fears", "t"));
        assertThat(GeoRiskScorer.aggregate(hot)).isBetween(0.5, 1.0);
        assertThat(GeoRiskScorer.aggregate(List.of(calm))).isZero();
        assertThat(GeoRiskScorer.sectorImpact(hot)).containsKey("energia");
    }

    @Test void macroRiskForSectorAndSafeHaven() {
        MacroSnapshot m = MacroSnapshot.of(30.0, 104.0, 4.2, 0.6, "x", "t", List.of(), java.util.Map.of("energia", 0.9));
        assertThat(m.riskFor("energia")).isGreaterThan(m.riskFor("tecnologia"));
        assertThat(m.riskFor("refugio")).isLessThan(m.riskScore());
    }

    @Test void marketHoursByRegion() {
        Instant tueNoonNy = Instant.parse("2026-11-17T17:00:00Z");   // terça, 12:00 em Nova York
        Instant satNoon = Instant.parse("2026-11-21T17:00:00Z");
        assertThat(MarketHours.isOpen("EUA", "Ação", tueNoonNy)).isTrue();
        assertThat(MarketHours.isOpen("EUA", "Ação", Instant.parse("2026-11-17T23:30:00Z"))).isFalse();
        assertThat(MarketHours.isOpen("Europa", "Ação", Instant.parse("2026-11-17T10:00:00Z"))).isTrue();
        assertThat(MarketHours.isOpen("EUA", "Ação", satNoon)).isFalse();
        assertThat(MarketHours.isOpen("Global", "Câmbio", tueNoonNy)).isTrue();
        assertThat(MarketHours.isOpen("Global", "Câmbio", satNoon)).isFalse();
    }

    @Test void relativeStrengthAndVolume() {
        double[] a = new double[70];
        double[] b = new double[70];
        for (int i = 0; i < 70; i++) { a[i] = 100 + i; b[i] = 100; }
        assertThat(Indicators.relativeStrength(a, b, 60)).isCloseTo(60.0 / 109.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(Indicators.relativeStrength(a, null, 60)).isZero();
    }
}
