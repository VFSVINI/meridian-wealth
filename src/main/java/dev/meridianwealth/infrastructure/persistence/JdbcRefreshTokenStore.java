package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.RefreshTokenStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh tokens no PostgreSQL, com a rotação atômica no banco: um único {@code UPDATE ... WHERE used = FALSE RETURNING} consome o
 * token, e a escrita do token novo vai na mesma transação. Com dois pedidos simultâneos para o mesmo token, o segundo espera o
 * primeiro confirmar, reavalia {@code used = FALSE} e não encontra linha: o que o classifica como reuso e revoga a família.
 */
@Repository
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "true")
public class JdbcRefreshTokenStore implements RefreshTokenStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcRefreshTokenStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public void save(Entry e) {
        jdbc.update("DELETE FROM refresh_tokens WHERE expires_at < ?", Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO refresh_tokens(token_hash, family_id, user_id, expires_at) VALUES (?,?,?,?)", e.hash(), e.familyId(), e.userId(),
                Timestamp.from(e.expiresAt()));
    }

    @Override
    public Rotation rotate(String presentedHash, String nextHash, Instant nextExpiresAt, Instant now) {
        return tx.execute(status -> {
            List<Entry> consumed = jdbc.query("UPDATE refresh_tokens SET used = TRUE WHERE token_hash = ? AND used = FALSE AND expires_at > ? "
                    + "RETURNING token_hash, family_id, user_id, expires_at", (rs, i) -> new Entry(rs.getString(1).trim(), rs.getString(2),
                    rs.getObject(3, UUID.class), rs.getTimestamp(4).toInstant()), presentedHash, Timestamp.from(now));
            if (!consumed.isEmpty()) {
                Entry previous = consumed.get(0);
                jdbc.update("INSERT INTO refresh_tokens(token_hash, family_id, user_id, expires_at) VALUES (?,?,?,?)", nextHash, previous.familyId(),
                        previous.userId(), Timestamp.from(nextExpiresAt));
                return new Rotation.Rotated(previous);
            }
            List<Object[]> found = jdbc.query("SELECT family_id, user_id, expires_at, used FROM refresh_tokens WHERE token_hash = ?",
                    (rs, i) -> new Object[] {rs.getString(1), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant(), rs.getBoolean(4)}, presentedHash);
            if (found.isEmpty()) return new Rotation.Invalid();
            Object[] row = found.get(0);
            String family = (String) row[0];
            if (!((Instant) row[2]).isAfter(now)) {
                jdbc.update("DELETE FROM refresh_tokens WHERE token_hash = ?", presentedHash);
                return new Rotation.Invalid();
            }
            // existe, não expirou e não pôde ser consumido: já foi usado. Quem o reapresenta o copiou.
            jdbc.update("DELETE FROM refresh_tokens WHERE family_id = ?", family);
            return new Rotation.Reused(family, (UUID) row[1]);
        });
    }

    @Override
    public void revokeFamilyOf(String presentedHash) {
        jdbc.update("DELETE FROM refresh_tokens WHERE family_id = (SELECT family_id FROM refresh_tokens WHERE token_hash = ?)", presentedHash);
    }

    @Override
    public void revokeAllOf(UUID userId) { jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId); }
}
