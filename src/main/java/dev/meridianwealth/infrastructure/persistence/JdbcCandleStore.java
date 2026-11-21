package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.domain.Candle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "true")
public class JdbcCandleStore implements CandleStore {
    private static final String UPSERT = "INSERT INTO candles(symbol, ts, open, high, low, close, volume, source) "
            + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (symbol, ts) DO UPDATE SET open = EXCLUDED.open, "
            + "high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close, volume = EXCLUDED.volume, "
            + "source = EXCLUDED.source";

    private final JdbcTemplate jdbc;

    public JdbcCandleStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void saveAll(String symbol, String source, List<Candle> candles) {
        jdbc.batchUpdate(UPSERT, candles, 200, (ps, c) -> {
            ps.setString(1, symbol);
            ps.setLong(2, c.time());
            ps.setDouble(3, c.open());
            ps.setDouble(4, c.high());
            ps.setDouble(5, c.low());
            ps.setDouble(6, c.close());
            ps.setDouble(7, c.volume());
            ps.setString(8, source);
        });
    }

    @Override
    public List<Candle> load(String symbol, int limit) {
        List<Candle> rows = new ArrayList<>(jdbc.query(
                "SELECT ts, open, high, low, close, volume FROM candles WHERE symbol = ? ORDER BY ts DESC LIMIT ?",
                (rs, i) -> new Candle(rs.getLong(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6)),
                symbol, limit));
        Collections.reverse(rows);
        return rows;
    }
}
