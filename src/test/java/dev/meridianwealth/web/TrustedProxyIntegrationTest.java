package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.Http;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Com o proxy local declarado confiável, o rate limit passa a contar por cliente real e o HSTS acompanha o esquema do proxy. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false",
        "meridianwealth.rate-limit.api-per-minute=3", "meridianwealth.security.trusted-proxies=127.0.0.1,::1"})
class TrustedProxyIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Value("${local.server.port}")
    private int port;

    private static Map<String, String> client(String ip) { return Map.of("X-Forwarded-For", ip); }

    @Test void eachRealClientBehindTheTrustedProxyHasItsOwnBucket() {
        Http http = new Http(port);
        for (int c = 1; c <= 6; c++) {
            for (int i = 0; i < 3; i++) assertThat(http.get("/api/universe", client("198.51.100." + c)).status()).as("cliente %d, chamada %d", c, i + 1).isEqualTo(200);
        }
        assertThat(http.get("/api/universe", client("198.51.100.1")).status()).as("o quarto pedido do mesmo cliente cai").isEqualTo(429);
        assertThat(http.get("/api/universe", client("198.51.100.99")).status()).as("outro cliente segue livre").isEqualTo(200);
    }

    @Test void forgedLeftmostEntriesDoNotCreateNewBuckets() {
        Http http = new Http(port);
        for (int i = 0; i < 3; i++) assertThat(http.get("/api/universe", client("1.1." + i + ".1, 203.0.113.50")).status()).isEqualTo(200);
        assertThat(http.get("/api/universe", client("9.9.9.9, 203.0.113.50")).status()).as("o que vale é o último endereço que não é proxy").isEqualTo(429);
    }

    @Test void hstsAndTheWebSocketCspFollowTheProxyScheme() {
        Http http = new Http(port);
        Http.Reply r = http.get("/", Map.of("X-Forwarded-Proto", "https", "X-Forwarded-For", "198.51.100.200"));
        assertThat(r.header("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(r.header("Content-Security-Policy")).contains("connect-src 'self' wss://localhost:" + port);
        assertThat(http.get("/", Map.of("X-Forwarded-For", "198.51.100.201")).header("Strict-Transport-Security")).isNull();
    }
}
