package dev.meridianwealth.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.BacktestService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Dívida 4: o cache de backtest precisa ida e volta fiel do record via Jackson. */
class JsonBacktestCacheTest {
    private final InMemoryCacheStore store = new InMemoryCacheStore();
    private final JsonBacktestCache cache = new JsonBacktestCache(store, new ObjectMapper());

    @Test void roundTripPreservesEveryField() {
        BacktestService.Result r = new BacktestService.Result(
                List.of(new BacktestService.Trade(120, 180, 101.25, 108.5, 0.0716, "realização"),
                        new BacktestService.Trade(200, 230, 110.0, 104.0, -0.0545, "stop técnico")),
                0.0125, 0.083, 0.5, 0.12, 380);
        cache.put("bt:k", r, Duration.ofMinutes(1));
        assertThat(cache.get("bt:k")).contains(r);
    }

    @Test void unreadableEntryIsAMissNotAnError() {
        store.put("bt:ruim", "{\"trades\":\"isto não é uma lista\"}", Duration.ofMinutes(1));
        assertThat(cache.get("bt:ruim")).isEmpty();
        assertThat(cache.get("bt:ausente")).isEmpty();
    }
}
