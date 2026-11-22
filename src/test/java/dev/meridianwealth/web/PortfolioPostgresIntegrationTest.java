package dev.meridianwealth.web;

import static dev.meridianwealth.testsupport.Http.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.infrastructure.persistence.JdbcPortfolioLedger;
import dev.meridianwealth.infrastructure.persistence.JdbcPositionStore;
import dev.meridianwealth.infrastructure.persistence.JdbcRefreshTokenStore;
import dev.meridianwealth.infrastructure.persistence.JdbcUserStore;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.Http;
import dev.meridianwealth.testsupport.PostgresDb;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A aplicação inteira com a persistência ligada, contra um PostgreSQL de verdade: o Flyway aplica V1 e V2, os adaptadores JDBC
 * substituem os de memória e os mesmos cenários da carteira valem. Também confere o que ficou gravado no banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false",
        "meridianwealth.security.bcrypt-strength=4", "meridianwealth.rate-limit.api-per-minute=100000", "meridianwealth.rate-limit.user-per-minute=100000",
        "meridianwealth.rate-limit.auth-per-minute=100000", "meridianwealth.persistence.enabled=true", "spring.autoconfigure.exclude=",
        "spring.datasource.username=postgres", "spring.datasource.password=postgres"})
class PortfolioPostgresIntegrationTest extends PortfolioApiScenarios {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", PostgresDb::jdbcUrl); }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserStore users;

    @Autowired
    RefreshTokenStore refreshTokens;

    @Autowired
    PositionStore positions;

    @Autowired
    PortfolioLedger ledger;

    @Test void theJdbcAdaptersReplaceTheInMemoryOnesAndFlywayAppliedBothMigrations() {
        assertThat(users).isInstanceOf(JdbcUserStore.class);
        assertThat(refreshTokens).isInstanceOf(JdbcRefreshTokenStore.class);
        assertThat(positions).isInstanceOf(JdbcPositionStore.class);
        assertThat(ledger).isInstanceOf(JdbcPortfolioLedger.class);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class)).contains("1", "2", "3");
    }

    @Test void whatIsStoredIsTheHashNotTheSecretAndTheNumbersAreExact() {
        String email = "banco-" + System.nanoTime() + "@exemplo.com";
        Http.Reply reg = http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Banco"));
        String refresh = (String) reg.get("refreshToken");
        String access = (String) reg.get("accessToken");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email)).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, refresh)).as("o token cru nunca é gravado").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id WHERE u.email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(http.put("/api/me/profile", bearer(access), Map.of("profile", "BOLD")).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT investor_profile FROM users WHERE email = ?", String.class, email)).as("o perfil de investidor agora persiste").isEqualTo("BOLD");
        assertThat(open(access, typed("LCI Banco Exato", "0.1", "0.2")).status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT quantity::text FROM positions p JOIN users u ON u.id = p.user_id WHERE u.email = ?", String.class, email)).isEqualTo("0.1000000000");
        // sair revoga a sessão no banco
        assertThat(http.post("/api/auth/logout", Map.of("refreshToken", refresh)).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id WHERE u.email = ?", Integer.class, email)).isZero();
    }

    @Test void everyOperationLeavesALedgerEntryInTheDatabaseAndThePositionIsItsFold() {
        String email = "livro-" + System.nanoTime() + "@exemplo.com";
        String access = (String) http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Livro")).get("accessToken");
        String id = idOf(open(access, typed("LCI Livro", "10", "100")));
        assertThat(buy(access, id, Map.of("quantity", "10", "unitPrice", "110", "fees", "20"), "chave-pg-1").status()).isEqualTo(200);
        assertThat(sell(access, id, Map.of("quantity", "4", "unitPrice", "120")).status()).isEqualTo(200);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT l.entry_type, l.quantity_after, l.average_price_after, l.realized_pnl, l.version_after, l.idempotency_key, length(l.request_hash) AS h "
                + "FROM ledger_entries l JOIN users u ON u.id = l.user_id WHERE u.email = ? ORDER BY l.seq", email);
        assertThat(rows).extracting(r -> r.get("entry_type")).containsExactly("OPENING", "BUY", "SELL");
        // (1.000 + 1.100 + 20) / 20 = 106; vende 4 a 120: resultado 4 x (120 - 106) = 56
        assertThat((BigDecimal) rows.get(1).get("average_price_after")).isEqualByComparingTo("106");
        assertThat((BigDecimal) rows.get(2).get("realized_pnl")).isEqualByComparingTo("56.00");
        assertThat(rows.get(1).get("idempotency_key")).isEqualTo("chave-pg-1");
        assertThat(((Number) rows.get(1).get("h")).intValue()).isEqualTo(64);
        BigDecimal positionQuantity = jdbc.queryForObject("SELECT p.quantity FROM positions p JOIN users u ON u.id = p.user_id WHERE u.email = ?", BigDecimal.class, email);
        assertThat(positionQuantity).as("a posição é o último estado do livro").isEqualByComparingTo((BigDecimal) rows.get(2).get("quantity_after")).isEqualByComparingTo("16");
        assertThat(jdbc.queryForObject("SELECT p.version FROM positions p JOIN users u ON u.id = p.user_id WHERE u.email = ?", Long.class, email))
                .isEqualTo(((Number) rows.get(2).get("version_after")).longValue());
    }

    @Test void marketDataPersistenceStillWorksAgainstRealPostgres() {
        // com a persistência ligada, os candles reais das fixtures passam a ser gravados pelo adaptador JDBC do item #1
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candles", Integer.class)).isGreaterThan(0);
    }
}
