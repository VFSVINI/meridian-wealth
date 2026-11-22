package dev.meridianwealth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.TokenService;
import dev.meridianwealth.infrastructure.security.ClientIpResolver;
import org.junit.jupiter.api.Test;

/** A configuração de segurança falha na partida, em vez de subir insegura. */
class SecurityConfigTest {
    @Test void corsAcceptsOnlyExactHttpOrigins() {
        assertThat(new SecurityConfig("").toString()).isNotNull();
        new SecurityConfig("https://app.exemplo.com, http://localhost:3000");
        for (String bad : new String[] {"*", "https://*.exemplo.com", "app.exemplo.com", "ftp://x.com", "null"}) {
            assertThatThrownBy(() -> new SecurityConfig(bad)).as(bad).isInstanceOf(IllegalStateException.class).hasMessageContaining("MW_CORS_ORIGINS");
        }
    }

    @Test void theJwtSecretMustBeLongEnoughAndAnEmptyOneFallsBackToARandomPerBootSecret() {
        SecurityConfig c = new SecurityConfig("");
        assertThatThrownBy(() -> c.tokenService("curto")).isInstanceOf(IllegalStateException.class).hasMessageContaining("MW_JWT_SECRET");
        TokenService random = c.tokenService("");
        TokenService other = c.tokenService("");
        assertThat(random).isNotNull();
        var user = new dev.meridianwealth.domain.auth.UserAccount(java.util.UUID.randomUUID(), "a@b.com", "A", null, dev.meridianwealth.domain.auth.AuthProvider.DEMO, null,
                null, java.time.Instant.now(), null);
        String t = random.issueAccess(user, java.time.Instant.now(), java.time.Duration.ofMinutes(1)).token();
        assertThat(other.verifyAccess(t, java.time.Instant.now())).as("cada partida tem o seu segredo").isEmpty();
        TokenService fixed = c.tokenService("x".repeat(40));
        assertThat(fixed.verifyAccess(fixed.issueAccess(user, java.time.Instant.now(), java.time.Duration.ofMinutes(1)).token(), java.time.Instant.now())).isPresent();
    }

    @Test void trustedProxyListIsValidatedAtStartup() {
        SecurityConfig c = new SecurityConfig("");
        assertThat(c.clientIpResolver("10.0.0.0/8, ::1")).isInstanceOf(ClientIpResolver.class);
        assertThatThrownBy(() -> c.clientIpResolver("meu-proxy")).isInstanceOf(IllegalArgumentException.class);
    }
}
