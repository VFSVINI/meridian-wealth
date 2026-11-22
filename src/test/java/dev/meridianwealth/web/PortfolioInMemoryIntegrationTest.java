package dev.meridianwealth.web;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** A carteira na execução sem Docker (persistência desligada): adaptadores em memória. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false",
        "meridianwealth.security.bcrypt-strength=4", "meridianwealth.rate-limit.api-per-minute=100000", "meridianwealth.rate-limit.user-per-minute=100000",
        "meridianwealth.rate-limit.auth-per-minute=100000"})
class PortfolioInMemoryIntegrationTest extends PortfolioApiScenarios {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }
}
