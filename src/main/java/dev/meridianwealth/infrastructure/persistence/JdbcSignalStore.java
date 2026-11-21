package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.SignalRecord;
import dev.meridianwealth.application.port.SignalStore;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "true")
public class JdbcSignalStore implements SignalStore {
    private final JdbcTemplate jdbc;

    public JdbcSignalStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void save(SignalRecord r) {
        jdbc.update("INSERT INTO signals(symbol, ts, score, signal, phase, regime, confidence, risk, price) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                r.symbol(), r.ts(), r.score(), r.signal(), r.phase(), r.regime(), r.confidence(), r.risk(), r.price());
    }

    @Override
    public List<SignalRecord> recent(String symbol, int limit) {
        return jdbc.query("SELECT symbol, ts, score, signal, phase, regime, confidence, risk, price FROM signals "
                        + "WHERE symbol = ? ORDER BY ts DESC, id DESC LIMIT ?",
                (rs, i) -> new SignalRecord(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8), rs.getDouble(9)),
                symbol, limit);
    }
}
