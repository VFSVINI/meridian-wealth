package dev.meridianwealth.application.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.portfolio.PortfolioException.Code;
import dev.meridianwealth.domain.auth.AuthProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O hash do conteúdo é o que separa uma repetição idempotente de uma chave reutilizada com outro conteúdo. */
class RequestHashTest {
    private static final AuthPrincipal ANA = new AuthPrincipal(UUID.randomUUID(), AuthProvider.PASSWORD);
    private static final AuthPrincipal BIA = new AuthPrincipal(UUID.randomUUID(), AuthProvider.PASSWORD);

    @Test void equivalentNumbersHashTheSameAndDifferentOnesDoNot() {
        String a = PortfolioService.hash("buy", ANA, new BigDecimal("10"), new BigDecimal("100.50"));
        assertThat(PortfolioService.hash("buy", ANA, new BigDecimal("10.000"), new BigDecimal("100.5"))).as("1,0 e 1,000 são o mesmo número").isEqualTo(a);
        assertThat(PortfolioService.hash("buy", ANA, new BigDecimal("10"), new BigDecimal("100.51"))).isNotEqualTo(a);
        assertThat(PortfolioService.hash("sell", ANA, new BigDecimal("10"), new BigDecimal("100.50"))).as("operação diferente").isNotEqualTo(a);
        assertThat(PortfolioService.hash("buy", BIA, new BigDecimal("10"), new BigDecimal("100.50"))).as("usuário diferente").isNotEqualTo(a);
        assertThat(PortfolioService.hash("buy", ANA, new BigDecimal("0"), null, LocalDate.parse("2026-03-10"))).isEqualTo(PortfolioService.hash("buy", ANA, new BigDecimal("0.00"), null, LocalDate.parse("2026-03-10")));
        assertThat(a).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test void absurdMagnitudesAreRefusedInstantlyInsteadOfBuildingAGigabyteString() {
        for (String huge : new String[] {"1E999999999", "1E-999999999", "-1E999999999", "9E+65", "1E-65"}) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> PortfolioService.hash("buy", ANA, new BigDecimal(huge))).as(huge).isInstanceOf(PortfolioException.class)
                    .extracting(e -> ((PortfolioException) e).code()).isEqualTo(Code.INVALID_INPUT);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).as("recusa de %s sem alocar", huge).isLessThan(Duration.ofSeconds(1));
        }
        assertThat(PortfolioService.hash("buy", ANA, new BigDecimal("999999999999999.12345678"))).isNotBlank();
    }
}
