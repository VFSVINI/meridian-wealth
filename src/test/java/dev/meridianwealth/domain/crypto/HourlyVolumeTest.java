package dev.meridianwealth.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.crypto.HourlyVolume.Bar;
import dev.meridianwealth.domain.crypto.HourlyVolume.Profile;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HourlyVolumeTest {
    private static final long H = 3_600_000L;
    private static final long START = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli();

    /** {@code days} dias de barras a partir de START, com o volume dado em função da hora UTC. */
    private static List<Bar> days(int days, java.util.function.IntToDoubleFunction volumeByUtcHour) {
        List<Bar> out = new ArrayList<>();
        for (int i = 0; i < days * 24; i++) out.add(new Bar(START + i * H, volumeByUtcHour.applyAsDouble(i % 24)));
        return out;
    }

    private static long after(int bars) { return START + bars * H; }

    @Test void averagesEachHourOfTheDayAndFindsThePeak() {
        Profile p = HourlyVolume.profile(days(7, h -> h == 14 ? 300 : 100), ZoneOffset.UTC, after(7 * 24));
        assertThat(p.hours()).hasSize(24);
        assertThat(p.hours().get(14).averageUsd()).isEqualTo(300.0);
        assertThat(p.hours().get(3).averageUsd()).isEqualTo(100.0);
        assertThat(p.peakHour()).isEqualTo(14);
        assertThat(p.hours().get(14).relative()).isEqualTo(1.0);
        assertThat(p.hours().get(3).relative()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(p.hours()).allSatisfy(h -> assertThat(h.samples()).isEqualTo(7));
        assertThat(p.bars()).isEqualTo(168);
    }

    @Test void theHourOfTheDayFollowsTheRequestedZone() {
        Profile utc = HourlyVolume.profile(days(7, h -> h == 14 ? 300 : 100), ZoneOffset.UTC, after(7 * 24));
        Profile sp = HourlyVolume.profile(days(7, h -> h == 14 ? 300 : 100), ZoneId.of("America/Sao_Paulo"), after(7 * 24));
        assertThat(utc.peakHour()).isEqualTo(14);
        assertThat(sp.peakHour()).as("Brasília fica 3 horas atrás de UTC").isEqualTo(11);
    }

    @Test void missingVolumeIsNotZeroItIsLeftOutOfTheAverageAndAnEmptyHourIsNull() {
        List<Bar> bars = days(7, h -> h == 5 ? 0 : 100);
        Profile p = HourlyVolume.profile(bars, ZoneOffset.UTC, after(7 * 24));
        assertThat(p.hours().get(5).averageUsd()).isNull();
        assertThat(p.hours().get(5).relative()).isNull();
        assertThat(p.hours().get(5).samples()).isZero();
        assertThat(p.hours().get(6).averageUsd()).isEqualTo(100.0);
        assertThat(p.bars()).as("só as barras com volume contam").isEqualTo(7 * 23);
        List<Bar> odd = new ArrayList<>(days(2, h -> 10));
        odd.set(3, new Bar(odd.get(3).timeMillis(), Double.NaN));
        odd.set(4, new Bar(odd.get(4).timeMillis(), -5));
        assertThat(HourlyVolume.profile(odd, ZoneOffset.UTC, after(48)).bars()).isEqualTo(46);
    }

    @Test void theBarOfTheCurrentHourIsPartialAndStaysOut() {
        List<Bar> bars = days(3, h -> 100);
        Profile p = HourlyVolume.profile(bars, ZoneOffset.UTC, after(3 * 24) - 1);
        assertThat(p.bars()).isEqualTo(3 * 24 - 1);
        assertThat(p.toMillis()).isEqualTo(after(3 * 24 - 1));
    }

    @Test void onlyTheLast168BarsCountAndOrderDoesNotMatter() {
        List<Bar> bars = new ArrayList<>(days(10, h -> 100));
        for (int i = 0; i < 24; i++) bars.set(i, new Bar(bars.get(i).timeMillis(), 9_999));   // as 24 mais antigas devem ficar de fora
        java.util.Collections.reverse(bars);
        Profile p = HourlyVolume.profile(bars, ZoneOffset.UTC, after(10 * 24));
        assertThat(p.bars()).isEqualTo(HourlyVolume.MAX_BARS);
        assertThat(p.hours()).allSatisfy(h -> assertThat(h.averageUsd()).isEqualTo(100.0));
        assertThat(p.fromMillis()).isEqualTo(after(10 * 24 - 168));
    }

    @Test void lessThanADayOfValidBarsGivesNoProfile() {
        assertThat(HourlyVolume.profile(days(1, h -> 100).subList(0, 23), ZoneOffset.UTC, after(48))).isNull();
        assertThat(HourlyVolume.profile(List.of(), ZoneOffset.UTC, after(48))).isNull();
        assertThat(HourlyVolume.profile(days(1, h -> 100), ZoneOffset.UTC, after(24))).as("24 barras completas bastam").isNotNull();
    }
}
