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
 * As rotas de cálculo pesado (Monte Carlo, cerca de 0,3 s de CPU cada) têm um limite próprio e baixo, por IP quando anônimas e por usuário quando
 * logadas, separado do limite geral das leituras baratas: sem isso, um só cliente ocupava vários núcleos dentro dos 300 por minuto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false", "meridianwealth.rate-limit.heavy-per-minute=3"})
class HeavyRouteRateLimitIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Value("${local.server.port}")
    private int port;

    private static final Map<String, Object> BODY = Map.of("initial", "10000", "horizonYears", 1, "expectedReturnPercent", "5", "volatilityPercent", "10", "seed", 1);

    @Test void anonymousClientsGetOnlyAFewSimulationsPerMinuteAndCheapRoutesAreUnaffected() {
        Http http = new Http(port);
        for (int i = 0; i < 3; i++) assertThat(http.post("/api/risk/monte-carlo", BODY).status()).as("simulação " + (i + 1)).isEqualTo(200);
        Http.Reply blocked = http.post("/api/risk/monte-carlo", BODY);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.header("Retry-After")).isEqualTo("60");
        assertThat(http.get("/api/universe").status()).as("as leituras baratas seguem no limite geral").isEqualTo(200);
        assertThat(http.get("/api/brazil/rates").status()).isEqualTo(200);
    }

    @Test void eachLoggedInUserHasItsOwnHeavyBucketAndThePortfolioRouteCountsToo() {
        Http http = new Http(port);
        String a = token(http), b = token(http);
        for (int i = 0; i < 3; i++) assertThat(http.post("/api/me/risk/monte-carlo", Http.bearer(a), Map.of("horizonYears", 1)).status()).as("tentativa " + (i + 1)).isIn(422, 200);
        assertThat(http.post("/api/me/risk/monte-carlo", Http.bearer(a), Map.of("horizonYears", 1)).status()).isEqualTo(429);
        assertThat(http.post("/api/me/risk/monte-carlo", Http.bearer(b), Map.of("horizonYears", 1)).status()).as("outro usuário, outro balde").isIn(422, 200);
        assertThat(http.get("/api/me", Http.bearer(a)).status()).as("o resto da conta não é afetado").isEqualTo(200);
    }

    private static String token(Http http) {
        Http.Reply r = http.post("/api/auth/register", Map.of("email", "heavy-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com", "password", "uma frase longa e boa", "displayName", "H"));
        assertThat(r.status()).isEqualTo(201);
        return (String) r.get("accessToken");
    }
}
