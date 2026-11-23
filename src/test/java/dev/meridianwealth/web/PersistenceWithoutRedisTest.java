package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.infrastructure.persistence.JdbcUserStore;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.PostgresDb;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A configuração do deploy gratuito (docs/deploy.md): banco ligado e Flyway ativo, Redis desligado pela exclusão exata do runbook,
 * cache em memória. Prova que a aplicação sobe e responde saudável sem nenhum Redis (a saúde não pode depender dele).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false",
        "meridianwealth.persistence.enabled=true", "spring.datasource.username=postgres", "spring.datasource.password=postgres",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"})
class PersistenceWithoutRedisTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", PostgresDb::jdbcUrl); }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserStore users;

    @Test void theDeployConfigurationUsesTheDatabaseAndStaysHealthyWithoutRedis() {
        assertThat(users).isInstanceOf(JdbcUserStore.class);
        assertThat(rest.getForObject("/actuator/health", String.class)).doesNotContain("\"status\":\"DOWN\"");
        assertThat(rest.getForObject("/actuator/health/liveness", String.class)).contains("\"status\":\"UP\"");
        assertThat(rest.getForObject("/actuator/health/readiness", String.class)).contains("\"status\":\"UP\"");
    }
}
