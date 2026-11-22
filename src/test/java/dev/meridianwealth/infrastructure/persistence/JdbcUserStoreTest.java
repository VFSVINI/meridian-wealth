package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.testsupport.PostgresDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcUserStoreTest extends UserStoreContract {
    private final JdbcTemplate jdbc = PostgresDb.jdbc();
    private final JdbcUserStore store = new JdbcUserStore(jdbc);

    @BeforeEach
    void clean() { PostgresDb.reset(); }

    @Override
    UserStore store() { return store; }

    @Test void theCredentialRulesAreEnforcedByTheDatabaseToo() {
        String sql = "INSERT INTO users(id, email, display_name, auth_provider, password_hash, google_subject, created_at) VALUES (gen_random_uuid(), ?, 'x', ?, ?, ?, now())";
        for (Object[] bad : new Object[][] {{"a@x.com", "PASSWORD", null, null}, {"b@x.com", "GOOGLE", null, null}, {"c@x.com", "GOOGLE", "hash", "sub"},
            {"d@x.com", "DEMO", "hash", null}, {"e@x.com", "ADMIN", null, null}}) {
            try {
                jdbc.update(sql, bad);
                throw new AssertionError("o banco deveria recusar " + bad[0]);
            } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                assertThat(expected).isNotNull();
            }
        }
    }

    @Test void passwordsAreNeverStoredInTheClearAndEmailIsCaseSensitiveAfterNormalization() {
        store.createIfAbsent(password("ana@exemplo.com"));
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users", String.class)).startsWith("$2a$");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'password'", Integer.class)).isZero();
    }
}
