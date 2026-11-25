package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.Http;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** A abertura de um ativo do catálogo chama provedores externos: ela tem o limite baixo das rotas pesadas, a busca (barata) não. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false", "meridianwealth.rate-limit.heavy-per-minute=4"})
class CatalogRateLimitIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Value("${local.server.port}")
    private int port;

    @Test void theOpenRouteHasItsOwnLowLimitBecauseEachCallHitsExternalProviders() {
        Http http = new Http(port);
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) statuses.add(http.post("/api/catalog/ZZZZ" + i + "/track", Map.of()).status());
        assertThat(statuses.subList(0, 4)).containsOnly(404);
        assertThat(statuses.subList(4, 6)).containsOnly(429);
        assertThat(http.get("/api/catalog/search?q=petr").status()).as("a busca é barata e segue no limite geral").isEqualTo(200);
    }
}
