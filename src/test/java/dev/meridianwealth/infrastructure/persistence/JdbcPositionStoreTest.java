package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.testsupport.PostgresDb;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** O mesmo contrato, contra um PostgreSQL de verdade com as migrações do Flyway. */
class JdbcPositionStoreTest extends PositionStoreContract {
    private final JdbcTemplate jdbc = PostgresDb.jdbc();
    private final JdbcPositionStore store = new JdbcPositionStore(jdbc);
    private final JdbcUserStore users = new JdbcUserStore(jdbc);

    @BeforeEach
    void clean() { PostgresDb.reset(); }

    @Override
    PositionStore store() { return store; }

    @Override
    UUID newUser() {
        UUID id = UUID.randomUUID();
        users.createIfAbsent(new UserAccount(id, id + "@exemplo.com", "U", null, AuthProvider.DEMO, null, null, Instant.now(), null));
        return id;
    }

    @Test void deletingTheUserRemovesItsPositionsAndSessionsByCascade() {
        UUID u = newUser();
        store.insertIfAbsent(position(u, "PETR4", "1", "1", Instant.now()));
        jdbc.update("INSERT INTO refresh_tokens(token_hash, family_id, user_id, expires_at) VALUES (?,?,?,?)", "a".repeat(64), "fam", u,
                Timestamp.from(Instant.now().plusSeconds(60)));
        jdbc.update("DELETE FROM users WHERE id = ?", u);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test void theDatabaseItselfRefusesInvalidPositions() {
        UUID u = newUser();
        String sql = "INSERT INTO positions(id, user_id, asset_key, symbol, asset_type, label, quantity, average_price, currency, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,now(),now())";
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), u, "K1", "K1", "ACAO", "x", 0, 1, "BRL")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), u, "K2", "K2", "ACAO", "x", 1, -1, "BRL")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), u, "K3", "K3", "ACAO", "x", 1, 1, "EUR")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), UUID.randomUUID(), "K4", "K4", "ACAO", "x", 1, 1, "BRL")).as("dono inexistente")
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(sql, UUID.randomUUID(), u, "K5", "K5", "ACAO", "x", 1, 1, "BRL");
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), u, "K5", "K5", "ACAO", "x", 1, 1, "BRL")).as("(usuário, ativo) único")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void quantitiesAreStoredAsExactNumericsNotFloats() {
        UUID u = newUser();
        store.insertIfAbsent(position(u, "EXATO", "0.1", "0.2", Instant.now()));
        assertThat(jdbc.queryForObject("SELECT quantity::text FROM positions WHERE user_id = ?", String.class, u)).isEqualTo("0.1000000000");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name = 'positions' AND column_name = 'average_price'", String.class))
                .isEqualTo("numeric");
    }
}
