package dev.meridianwealth.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.BacktestService;
import dev.meridianwealth.application.port.BacktestCache;
import dev.meridianwealth.application.port.CacheStore;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Adaptador do cache de backtest: serializa o resultado em JSON sobre o {@link CacheStore} (memória ou Redis). */
@Component
public class JsonBacktestCache implements BacktestCache {
    private static final Logger log = LoggerFactory.getLogger(JsonBacktestCache.class);

    private final CacheStore store;
    private final ObjectMapper mapper;

    public JsonBacktestCache(CacheStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    public Optional<BacktestService.Result> get(String key) {
        Optional<String> raw = store.get(key);
        if (raw.isEmpty()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(raw.get(), BacktestService.Result.class));
        } catch (JsonProcessingException e) {
            log.warn("Cache de backtest ilegível para {}; recalculando ({})", key, e.getOriginalMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, BacktestService.Result result, Duration ttl) {
        try {
            store.put(key, mapper.writeValueAsString(result), ttl);
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar backtest {} ({})", key, e.getOriginalMessage());
        } catch (RuntimeException e) {
            log.warn("Cache indisponível ao gravar backtest {} ({})", key, e.getMessage());
        }
    }
}
