package dev.meridianwealth.web;

import static dev.meridianwealth.testsupport.Http.bearer;
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
 * Rate limit com limites apertados e <b>sem</b> proxy confiável (o padrão): o {@code X-Forwarded-For} escrito pelo cliente não
 * muda o balde (dívida 5), o usuário autenticado tem balde próprio e as rotas de autenticação têm um limite menor por IP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false",
        "meridianwealth.security.bcrypt-strength=4", "meridianwealth.rate-limit.api-per-minute=6", "meridianwealth.rate-limit.auth-per-minute=3",
        "meridianwealth.rate-limit.user-per-minute=1000", "meridianwealth.rate-limit.demo-per-minute=1000"})
class RateLimitIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Value("${local.server.port}")
    private int port;

    private static Map<String, String> spoof() { return Map.of("X-Forwarded-For", "198.51.100." + (1 + (int) (Math.random() * 200)) + ", 203.0.113." + Math.floorMod(UUID.randomUUID().hashCode(), 200)); }

    @Test void spoofedForwardedForDoesNotBypassTheLimitAndAuthRoutesHaveTheirOwn() {
        Http http = new Http(port);
        Http.Reply demo = http.post("/api/auth/demo", spoof(), null);
        assertThat(demo.status()).isEqualTo(200);
        String token = (String) demo.get("accessToken");

        // rotas de autenticação: limite de 3 por minuto por IP (a demonstração já gastou 1)
        assertThat(http.post("/api/auth/login", spoof(), Map.of("email", "a@exemplo.com", "password", "errada errada errada")).status()).isEqualTo(401);
        assertThat(http.post("/api/auth/login", spoof(), Map.of("email", "a@exemplo.com", "password", "errada errada errada")).status()).isEqualTo(401);
        Http.Reply blocked = http.post("/api/auth/login", spoof(), Map.of("email", "a@exemplo.com", "password", "errada errada errada"));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.get("code")).isEqualTo("RATE_LIMITED");
        assertThat(blocked.header("Retry-After")).isEqualTo("60");

        // limite anônimo de 6 por minuto: 4 já foram gastos, restam 2 e o terceiro cai, mesmo com um X-Forwarded-For diferente a cada chamada
        assertThat(http.get("/api/universe", spoof()).status()).isEqualTo(200);
        assertThat(http.get("/api/universe", spoof()).status()).isEqualTo(200);
        assertThat(http.get("/api/universe", spoof()).status()).as("o cabeçalho forjado não abre um balde novo").isEqualTo(429);

        // o usuário autenticado tem balde próprio e continua passando com o IP esgotado
        assertThat(http.get("/api/me", bearer(token)).status()).isEqualTo(200);
        assertThat(http.get("/api/universe", bearer(token)).status()).isEqualTo(200);
        // o stream e as rotas fora de /api não entram na conta
        assertThat(http.get("/").status()).isEqualTo(200);
    }
}
