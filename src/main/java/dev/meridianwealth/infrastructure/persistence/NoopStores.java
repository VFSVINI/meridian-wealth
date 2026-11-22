package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.SignalRecord;
import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.SignalStore;
import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.infrastructure.security.InMemoryRefreshTokenStore;
import dev.meridianwealth.infrastructure.security.InMemoryUserStore;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Persistência desligada (execução sem Docker): candles e sinais não são gravados e as leituras voltam vazias; contas, sessões e
 * posições ficam em memória (somem no reinício). Com a persistência ligada, os adaptadores JDBC ocupam o lugar destes.
 */
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
    UserStore userStore() { return new InMemoryUserStore(); }

    @Bean
    RefreshTokenStore refreshTokenStore() { return new InMemoryRefreshTokenStore(); }

    @Bean
    PositionStore positionStore() { return new InMemoryPositionStore(); }

    @Bean
    SignalStore signalStore() {
        return new SignalStore() {
            @Override public void save(SignalRecord record) { }

            @Override public List<SignalRecord> recent(String symbol, int limit) { return List.of(); }
        };
    }
}
