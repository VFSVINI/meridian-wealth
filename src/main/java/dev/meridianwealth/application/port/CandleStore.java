package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.Candle;
import java.util.List;

public interface CandleStore {
    void saveAll(String symbol, String source, List<Candle> candles);

    List<Candle> load(String symbol, int limit);
}
