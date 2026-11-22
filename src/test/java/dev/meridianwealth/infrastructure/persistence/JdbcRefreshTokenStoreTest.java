package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.testsupport.PostgresDb;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class JdbcRefreshTokenStoreTest extends RefreshTokenStoreContract {
    private final JdbcTemplate jdbc = PostgresDb.jdbc();
    private final JdbcRefreshTokenStore store = new JdbcRefreshTokenStore(jdbc, new DataSourceTransactionManager(PostgresDb.dataSource()));
    private final JdbcUserStore users = new JdbcUserStore(jdbc);

    @BeforeEach
    void clean() { PostgresDb.reset(); }

    @Override
    RefreshTokenStore store() { return store; }

    @Override
    UUID newUser() {
        UUID id = UUID.randomUUID();
        users.createIfAbsent(new UserAccount(id, id + "@exemplo.com", "U", null, AuthProvider.DEMO, null, null, Instant.now(), null));
        return id;
    }

    @Test void onlyTheHashIsStoredAndExpiredRowsAreSweptOnSave() {
        UUID u = newUser();
        store.save(new RefreshTokenStore.Entry(hash("vivo"), "f1", u, Instant.now().plusSeconds(600)));
        store.save(new RefreshTokenStore.Entry(hash("velho"), "f2", u, Instant.now().minusSeconds(3600)));
        store.save(new RefreshTokenStore.Entry(hash("novo"), "f3", u, Instant.now().plusSeconds(600)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).as("o expirado saiu na gravação seguinte").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT max(length(token_hash)) FROM refresh_tokens", Integer.class)).isEqualTo(64);
    }
}
