package dev.meridianwealth.application.port;

import dev.meridianwealth.application.BacktestService;
import java.time.Duration;
import java.util.Optional;

/** Cache de resultados de backtest; a serialização é responsabilidade do adaptador. */
public interface BacktestCache {
    Optional<BacktestService.Result> get(String key);

    void put(String key, BacktestService.Result result, Duration ttl);
}
