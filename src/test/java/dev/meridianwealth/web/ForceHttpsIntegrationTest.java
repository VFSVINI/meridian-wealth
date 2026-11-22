package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.Http;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Com {@code MW_FORCE_HTTPS} e o proxy local declarado confiável: HTTP puro vira 308 para o HTTPS (método preservado, porta padrão, host
 * validado), o que o proxy anuncia como HTTPS passa, a verificação de saúde fica de fora, e o cookie de sessão ganha {@code Secure}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false",
        "meridianwealth.security.force-https=true", "meridianwealth.security.trusted-proxies=127.0.0.1,::1"})
class ForceHttpsIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Value("${local.server.port}")
    private int port;

    private static final Map<String, String> HTTPS = Map.of("X-Forwarded-Proto", "https");

    @Test void plainHttpIsRedirectedPermanentlyKeepingPathAndQuery() {
        Http http = new Http(port);
        Http.Reply r = http.get("/api/universe?x=1");
        assertThat(r.status()).isEqualTo(308);
        assertThat(r.header("Location")).isEqualTo("https://localhost/api/universe?x=1");
        assertThat(http.post("/api/auth/login", Map.of("email", "a@b.co", "password", "x")).status()).as("o método é preservado: 308, não 301").isEqualTo(308);
    }

    @Test void whatTheTrustedProxyAnnouncesAsHttpsPassesAndTheHealthCheckIsExempt() {
        Http http = new Http(port);
        assertThat(http.get("/api/universe", HTTPS).status()).isEqualTo(200);
        assertThat(http.get("/", HTTPS).header("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(http.get("/actuator/health").status()).as("sondas internas não são redirecionadas").isEqualTo(200);
    }

    @Test void theSessionCookieIsSecureBehindTheTrustedHttpsProxy() {
        Http http = new Http(port);
        Http.Reply reg = http.post("/api/auth/register", Map.of("X-Session-Mode", "cookie", "X-Forwarded-Proto", "https"),
                Map.of("email", "https-" + UUID.randomUUID() + "@teste.invalid", "password", "uma frase longa e segura 1", "displayName", "T"));
        assertThat(reg.status()).isEqualTo(201);
        assertThat(reg.header("Set-Cookie")).contains("Secure").contains("HttpOnly").contains("SameSite=Strict");
    }
}
