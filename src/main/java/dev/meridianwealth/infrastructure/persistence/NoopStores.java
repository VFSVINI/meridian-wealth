package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.SignalRecord;
import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.application.port.SignalStore;
import dev.meridianwealth.domain.Candle;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Persistência desligada (execução sem Docker): nada é gravado e as leituras voltam vazias. */
@Configuration
@ConditionalOnProperty(name = "meridianwealth.persistence.enabled", havingValue = "false", matchIfMissing = true)
public class NoopStores {
    @Bean
    CandleStore candleStore() {
        return new CandleStore() {
            @Override public void saveAll(String symbol, String source, List<Candle> candles) { }

            @Override public List<Candle> load(String symbol, int limit) { return List.of(); }
        };
    }

    @Bean
    SignalStore signalStore() {
        return new SignalStore() {
            @Override public void save(SignalRecord record) { }

            @Override public List<SignalRecord> recent(String symbol, int limit) { return List.of(); }
        };
    }
}
