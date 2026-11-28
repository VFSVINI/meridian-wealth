package dev.meridianwealth.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FearGreedTest {
    @Test void translatesTheSourceLabelsAndKeepsTheValueAndTime() {
        FearGreed f = FearGreed.of(10, "Extreme Fear", 1795824000L);
        assertThat(f.value()).isEqualTo(10);
        assertThat(f.label()).isEqualTo("Medo extremo");
        assertThat(f.classification()).isEqualTo("Extreme Fear");
        assertThat(f.updatedAt()).isEqualTo(Instant.ofEpochSecond(1795824000L));
        assertThat(FearGreed.of(30, "Fear", 1).label()).isEqualTo("Medo");
        assertThat(FearGreed.of(50, "Neutral", 1).label()).isEqualTo("Neutro");
        assertThat(FearGreed.of(60, "Greed", 1).label()).isEqualTo("Ganância");
        assertThat(FearGreed.of(90, "Extreme Greed", 1).label()).isEqualTo("Ganância extrema");
    }

    @Test void anUnknownOrMissingLabelIsNeverMadeUp() {
        assertThat(FearGreed.of(42, "Something New", 1).label()).isEqualTo("Something New");
        assertThat(FearGreed.of(42, null, 1).label()).isNull();
        assertThat(FearGreed.of(42, "  ", 1).label()).isNull();
    }

    @Test void theValueMustBeBetweenZeroAndOneHundred() {
        assertThat(FearGreed.of(0, "Extreme Fear", 1).value()).isZero();
        assertThat(FearGreed.of(100, "Extreme Greed", 1).value()).isEqualTo(100);
        assertThatThrownBy(() -> FearGreed.of(-1, "Fear", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FearGreed.of(101, "Greed", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
