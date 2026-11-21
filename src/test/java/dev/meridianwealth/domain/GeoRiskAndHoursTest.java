package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** Manchete real do radar (2026-11-21): um acordo de paz que cita "wars" não pode contar como risco de guerra. */
    @Test void peaceDeclarationIsTreatedAsDeescalation() {
        NewsItem n = GeoRiskScorer.score("China, Russia and US agree peace declaration with no mention of Ukraine and Iran wars", "Guardian World");
        assertThat(n.risk()).isLessThan(0.3);
        assertThat(n.sentiment()).isPositive();
        assertThat(GeoRiskScorer.score("Leaders sign peace agreement after war", "t").risk()).isLessThan(0.3);
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

    @Test void relativeStrengthAndVolume() {
        double[] a = new double[70];
        double[] b = new double[70];
        for (int i = 0; i < 70; i++) { a[i] = 100 + i; b[i] = 100; }
        assertThat(Indicators.relativeStrength(a, b, 60)).isCloseTo(60.0 / 109.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(Indicators.relativeStrength(a, null, 60)).isZero();
    }
}
