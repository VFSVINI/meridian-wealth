package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.application.port.FundamentalsPort;
import dev.meridianwealth.domain.Fundamentals;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fundamentos via Finnhub (apenas ações dos EUA, com chave), em cache por 12 h. */
@Component
public class FundamentalsService implements FundamentalsPort {
    private static final Logger log = LoggerFactory.getLogger(FundamentalsService.class);

    private final FinnhubClient finnhub;
    private final CacheStore cache;
    private final ObjectMapper mapper;

    public FundamentalsService(FinnhubClient finnhub, CacheStore cache, ObjectMapper mapper) {
        this.finnhub = finnhub;
        this.cache = cache;
        this.mapper = mapper;
    }

    @Override
    public Optional<Fundamentals> fundamentals(String symbol) {
        Symbols sy = Universe.entry(symbol).symbols();
        if (!finnhub.enabled() || sy.finnhub() == null || !"Ação".equals(Universe.entry(symbol).instrument().assetClass())) {
            return Optional.empty();
        }
        String key = "fund:" + symbol;
        try {
            Optional<String> hit = cache.get(key);
            if (hit.isPresent()) return Optional.of(mapper.readValue(hit.get(), Fundamentals.class));
            Fundamentals f = finnhub.fundamentals(symbol, sy.finnhub());
            cache.put(key, mapper.writeValueAsString(f), Duration.ofHours(12));
            return Optional.of(f);
        } catch (Exception e) {
            log.debug("Fundamentos de {} indisponíveis: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }
}
