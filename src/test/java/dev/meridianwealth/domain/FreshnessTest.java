package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FreshnessTest {
    @Test void labelsAndDescriptionsAreExactlyWhatTheUserReads() {
        assertThat(Freshness.REAL_TIME.label()).isEqualTo("tempo real");
        assertThat(Freshness.DELAYED.label()).isEqualTo("~15 min");
        assertThat(Freshness.END_OF_DAY.label()).isEqualTo("fechamento");
        assertThat(Freshness.STORED.label()).isEqualTo("último dado real gravado");
        assertThat(Freshness.REAL_TIME.description()).isEqualTo("atraso de segundos");
        assertThat(Freshness.DELAYED.description()).isEqualTo("atraso típico de 15 minutos ou mais");
        assertThat(Freshness.END_OF_DAY.description()).isEqualTo("preço de fechamento ou referência diária");
        assertThat(Freshness.STORED.description()).isEqualTo("sem dado em tempo real; exibindo o último dado real do banco");
    }

    @Test void maxAgeIsTwoMinutesForRealTimeThirtyForDelayedAndUnlimitedOtherwise() {
        assertThat(Freshness.REAL_TIME.maxAgeMillis()).isEqualTo(120_000L);
        assertThat(Freshness.DELAYED.maxAgeMillis()).isEqualTo(1_800_000L);
        assertThat(Freshness.END_OF_DAY.maxAgeMillis()).isEqualTo(Long.MAX_VALUE);
        assertThat(Freshness.STORED.maxAgeMillis()).isEqualTo(Long.MAX_VALUE);
    }

    @Test void aQuoteIsStaleOnlyWhenTheMarketIsOpenItHasATimeAndItIsOlderThanTheLimit() {
        long now = 10_000_000L;
        assertThat(Freshness.REAL_TIME.isStale(now - 120_000L, now, true)).as("no limite exato ainda vale").isFalse();
        assertThat(Freshness.REAL_TIME.isStale(now - 120_001L, now, true)).isTrue();
        assertThat(Freshness.DELAYED.isStale(now - 1_800_000L, now, true)).isFalse();
        assertThat(Freshness.DELAYED.isStale(now - 1_800_001L, now, true)).isTrue();
        assertThat(Freshness.REAL_TIME.isStale(now - 9_999_999L, now, false)).as("pregão fechado: nunca desatualizada").isFalse();
        assertThat(Freshness.REAL_TIME.isStale(0, now, true)).as("sem horário de cotação").isFalse();
        assertThat(Freshness.REAL_TIME.isStale(1, now, true)).as("horário 1 já conta como informado").isTrue();
        assertThat(Freshness.END_OF_DAY.isStale(1, now, true)).isFalse();
        assertThat(Freshness.STORED.isStale(1, now, true)).isFalse();
    }
}
