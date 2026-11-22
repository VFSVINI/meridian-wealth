package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Contas no PostgreSQL. A unicidade de e-mail e de {@code sub} do Google é garantida pelos índices únicos (não por checagem na
 * aplicação), então dois cadastros simultâneos com o mesmo e-mail nunca criam duas contas.
 */
@Repository
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "true")
public class JdbcUserStore implements UserStore {
    private static final String COLUMNS = "id, email, display_name, avatar_url, auth_provider, password_hash, google_subject, investor_profile, created_at";
    private static final RowMapper<UserAccount> MAPPER = JdbcUserStore::map;
    private final JdbcTemplate jdbc;

    public JdbcUserStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static UserAccount map(ResultSet rs, int row) throws SQLException {
        String profile = rs.getString("investor_profile");
        return new UserAccount(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"), rs.getString("avatar_url"),
                AuthProvider.valueOf(rs.getString("auth_provider")), rs.getString("password_hash"), rs.getString("google_subject"),
                rs.getTimestamp("created_at").toInstant(), profile == null ? null : InvestorProfile.valueOf(profile));
    }

    @Override
    public Optional<UserAccount> findById(UUID id) { return one("SELECT " + COLUMNS + " FROM users WHERE id = ?", id); }

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) { return one("SELECT " + COLUMNS + " FROM users WHERE email = ?", normalizedEmail); }

    @Override
    public Optional<UserAccount> findByGoogleSubject(String subject) { return one("SELECT " + COLUMNS + " FROM users WHERE google_subject = ?", subject); }

    private Optional<UserAccount> one(String sql, Object arg) { return jdbc.query(sql, MAPPER, arg).stream().findFirst(); }

    @Override
    public boolean createIfAbsent(UserAccount a) {
        return jdbc.update("INSERT INTO users(id, email, display_name, avatar_url, auth_provider, password_hash, google_subject, investor_profile, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING", a.id(), a.email(), a.displayName(), a.avatarUrl(), a.provider().name(), a.passwordHash(),
                a.googleSubject(), a.profile() == null ? null : a.profile().name(), Timestamp.from(a.createdAt())) == 1;
    }

    @Override
    public void update(UserAccount a) {
        int rows = jdbc.update("UPDATE users SET display_name = ?, avatar_url = ?, investor_profile = ?, version = version + 1 WHERE id = ? AND email = ?",
                a.displayName(), a.avatarUrl(), a.profile() == null ? null : a.profile().name(), a.id(), a.email());
        if (rows == 0) throw new IllegalArgumentException("Conta inexistente ou e-mail alterado");
    }

    @Override
    public int countPermanent() { return jdbc.queryForObject("SELECT count(*) FROM users WHERE auth_provider <> 'DEMO'", Integer.class); }

    @Override
    public int countDemo() { return jdbc.queryForObject("SELECT count(*) FROM users WHERE auth_provider = 'DEMO'", Integer.class); }

    @Override
    public int purgeDemoBefore(Instant cutoff) { return jdbc.update("DELETE FROM users WHERE auth_provider = 'DEMO' AND created_at < ?", Timestamp.from(cutoff)); }
}
