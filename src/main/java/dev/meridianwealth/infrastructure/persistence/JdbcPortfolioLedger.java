package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.AssetRef;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.EntryType;
import dev.meridianwealth.domain.portfolio.FxSource;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Livro de movimentações no PostgreSQL. Cada {@link #commit} é uma única transação: a mudança da posição (condicionada à versão) e a
 * gravação da entrada acontecem juntas ou nenhuma acontece. As entradas são só acrescentadas (um gatilho bloqueia UPDATE) e a mesma
 * {@code Idempotency-Key} do mesmo usuário é única por índice, então duas requisições simultâneas gravam uma só vez: a perdedora
 * descobre a entrada da vencedora e a devolve como {@link Commit.Duplicate}.
 */
@Repository
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "true")
public class JdbcPortfolioLedger implements PortfolioLedger {
    private static final int ALL_LIMIT = 100_000;
    private static final String COLUMNS = "id, user_id, position_id, asset_key, symbol, asset_type, label, entry_type, trade_date, quantity, unit_price, fees, currency, "
            + "fx_brl_per_usd, fx_source, fx_date, quantity_after, average_price_after, average_cost_brl_after, realized_pnl, realized_pnl_brl, version_after, "
            + "idempotency_key, request_hash, created_at";
    private static final RowMapper<LedgerEntry> MAPPER = JdbcPortfolioLedger::map;

    private final JdbcPositionStore positions;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcPortfolioLedger(JdbcPositionStore positions, JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.positions = positions;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
    }

    private static LedgerEntry map(ResultSet rs, int row) throws SQLException {
        AssetRef asset = new AssetRef(rs.getString("symbol"), AssetType.valueOf(rs.getString("asset_type")), rs.getString("label"));
        String source = rs.getString("fx_source");
        Date fxDate = rs.getDate("fx_date");
        return new LedgerEntry(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("position_id", UUID.class), asset,
                EntryType.valueOf(rs.getString("entry_type")), rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("fees"), Currency.valueOf(rs.getString("currency").trim()), rs.getBigDecimal("fx_brl_per_usd"), source == null ? null : FxSource.valueOf(source),
                fxDate == null ? null : fxDate.toLocalDate(), rs.getBigDecimal("quantity_after"), rs.getBigDecimal("average_price_after"),
                rs.getBigDecimal("average_cost_brl_after"), rs.getBigDecimal("realized_pnl"), rs.getBigDecimal("realized_pnl_brl"), rs.getLong("version_after"),
                rs.getString("idempotency_key"), rs.getString("request_hash") == null ? null : rs.getString("request_hash").trim(), rs.getTimestamp("created_at").toInstant());
    }

    @Override
    public Commit commit(Change change) {
        LedgerEntry e = change.entry();
        Commit result = tx.execute(status -> {
            if (e.idempotencyKey() != null) {
                Optional<LedgerEntry> existing = findByKey(e.userId(), e.idempotencyKey());
                if (existing.isPresent()) return new Commit.Duplicate(existing.get());
            }
            Position saved = null;
            switch (change.op()) {
                case OPEN -> {
                    if (!positions.insertIfAbsent(change.next())) return lost(e, new Commit.PositionExists());
                    saved = change.next();
                }
                case UPDATE -> {
                    Optional<Position> updated = positions.update(change.next(), change.expectedVersion());
                    if (updated.isEmpty()) return lost(e, new Commit.VersionConflict());
                    saved = updated.get();
                }
                case DELETE -> {
                    if (!positions.delete(change.positionId(), e.userId(), change.expectedVersion())) return lost(e, new Commit.VersionConflict());
                }
                default -> throw new IllegalStateException("Operação desconhecida");
            }
            try {
                insert(e);
            } catch (DuplicateKeyException race) {
                status.setRollbackOnly();
                return findByKey(e.userId(), e.idempotencyKey()).<Commit>map(Commit.Duplicate::new).orElse(new Commit.VersionConflict());
            }
            return new Commit.Committed(saved, e);
        });
        return result == null ? new Commit.VersionConflict() : result;
    }

    /** A escrita perdeu a corrida. Se foi para outra requisição com a mesma chave, é uma repetição, não um conflito. */
    private Commit lost(LedgerEntry e, Commit reason) {
        if (e.idempotencyKey() != null) {
            Optional<LedgerEntry> existing = findByKey(e.userId(), e.idempotencyKey());
            if (existing.isPresent()) return new Commit.Duplicate(existing.get());
        }
        return reason;
    }

    private void insert(LedgerEntry e) {
        jdbc.update("INSERT INTO ledger_entries(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", e.id(), e.userId(), e.positionId(), e.asset().key(),
                e.asset().symbol(), e.asset().type().name(), e.asset().label(), e.type().name(), Date.valueOf(e.tradeDate()), e.quantity(), e.unitPrice(), e.fees(),
                e.currency().name(), e.fxBrlPerUsd(), e.fxSource() == null ? null : e.fxSource().name(), e.fxDate() == null ? null : Date.valueOf(e.fxDate()), e.quantityAfter(),
                e.averagePriceAfter(), e.averageCostBrlAfter(), e.realizedPnl(), e.realizedPnlBrl(), e.versionAfter(), e.idempotencyKey(), e.requestHash(),
                Timestamp.from(e.createdAt()));
    }

    @Override
    public Optional<LedgerEntry> findByKey(UUID userId, String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        return jdbc.query("SELECT " + COLUMNS + " FROM ledger_entries WHERE user_id = ? AND idempotency_key = ?", MAPPER, userId, idempotencyKey).stream().findFirst();
    }

    @Override
    public List<LedgerEntry> entries(UUID userId, String assetKey, LocalDate from, LocalDate to, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM ledger_entries WHERE user_id = ?");
        List<Object> args = new ArrayList<>(List.of(userId));
        if (assetKey != null) {
            sql.append(" AND asset_key = ?");
            args.add(assetKey);
        }
        if (from != null) {
            sql.append(" AND trade_date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND trade_date <= ?");
            args.add(Date.valueOf(to));
        }
        sql.append(" ORDER BY seq LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public List<LedgerEntry> all(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM ledger_entries WHERE user_id = ? ORDER BY seq LIMIT " + ALL_LIMIT, MAPPER, userId);
    }

    @Override
    public Optional<LocalDate> lastTradeDate(UUID userId, String assetKey) {
        Date d = jdbc.queryForObject("SELECT max(trade_date) FROM ledger_entries WHERE user_id = ? AND asset_key = ?", Date.class, userId, assetKey);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }
}
