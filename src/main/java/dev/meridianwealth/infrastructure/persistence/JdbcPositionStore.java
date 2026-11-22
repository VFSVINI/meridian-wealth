package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.AssetRef;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.Position;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Posições no PostgreSQL. Toda consulta e toda escrita filtram pelo dono ({@code user_id}); a atualização e a exclusão são
 * condicionadas à {@code version}, então duas escritas sobre a mesma versão resultam em exatamente uma vencedora (a outra afeta
 * zero linhas). Quantidades e preços são {@code NUMERIC}, lidos e gravados como {@link java.math.BigDecimal}.
 */
@Repository
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "true")
public class JdbcPositionStore implements PositionStore {
    private static final String COLUMNS = "id, user_id, asset_key, symbol, asset_type, label, quantity, average_price, average_cost_brl, currency, created_at, updated_at, version";
    private static final RowMapper<Position> MAPPER = JdbcPositionStore::map;
    private final JdbcTemplate jdbc;

    public JdbcPositionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static Position map(ResultSet rs, int row) throws SQLException {
        AssetRef asset = new AssetRef(rs.getString("symbol"), AssetType.valueOf(rs.getString("asset_type")), rs.getString("label"));
        return new Position(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), asset, rs.getBigDecimal("quantity"),
                rs.getBigDecimal("average_price"), rs.getBigDecimal("average_cost_brl"), Currency.valueOf(rs.getString("currency").trim()), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    @Override
    public List<Position> findByUser(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM positions WHERE user_id = ? ORDER BY created_at, id", MAPPER, userId);
    }

    @Override
    public Optional<Position> find(UUID id, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM positions WHERE id = ? AND user_id = ?", MAPPER, id, userId).stream().findFirst();
    }

    @Override
    public int countByUser(UUID userId) { return jdbc.queryForObject("SELECT count(*) FROM positions WHERE user_id = ?", Integer.class, userId); }

    @Override
    public boolean insertIfAbsent(Position p) {
        return jdbc.update("INSERT INTO positions(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT DO NOTHING", p.id(), p.userId(),
                p.asset().key(), p.asset().symbol(), p.asset().type().name(), p.asset().label(), p.quantity(), p.averagePrice(), p.averageCostBrl(), p.currency().name(),
                Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt())) == 1;
    }

    @Override
    public Optional<Position> update(Position next, long expectedVersion) {
        int rows = jdbc.update("UPDATE positions SET quantity = ?, average_price = ?, average_cost_brl = ?, updated_at = ?, version = version + 1 WHERE id = ? AND user_id = ? AND version = ?",
                next.quantity(), next.averagePrice(), next.averageCostBrl(), Timestamp.from(next.updatedAt()), next.id(), next.userId(), expectedVersion);
        return rows == 1 ? Optional.of(next.withVersion(expectedVersion + 1)) : Optional.empty();
    }

    @Override
    public int deleteAllOf(UUID userId) { return jdbc.update("DELETE FROM positions WHERE user_id = ?", userId); }

    @Override
    public boolean delete(UUID id, UUID userId, long expectedVersion) {
        return jdbc.update("DELETE FROM positions WHERE id = ? AND user_id = ? AND version = ?", id, userId, expectedVersion) == 1;
    }
}
