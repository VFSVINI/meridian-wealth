package dev.meridianwealth.config;

import dev.meridianwealth.application.BacktestService;
import dev.meridianwealth.application.BrazilRatesService;
import dev.meridianwealth.application.CompositionService;
import dev.meridianwealth.application.ReportsService;
import dev.meridianwealth.application.port.ReportsPort;
import dev.meridianwealth.application.port.CompositionPort;
import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.StatusService;
import dev.meridianwealth.application.StreamService;
import dev.meridianwealth.application.port.BacktestCache;
import dev.meridianwealth.application.port.BrazilRatesPort;
import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.application.port.FundamentalsPort;
import dev.meridianwealth.application.port.MarketDataPort;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.application.port.SignalStore;
import dev.meridianwealth.application.port.TickSink;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composição na borda (ADR 001): os casos de uso da camada {@code application} são POJOs e só
 * aqui são ligados às portas implementadas pelos adaptadores de {@code infrastructure} e {@code web}.
 */
@Configuration
public class ApplicationConfig {
    @Bean
    BacktestService backtestService() { return new BacktestService(); }

    @Bean(initMethod = "loadHistories")
    MarketService marketService(MarketDataPort data, SignalStore signals, BacktestCache backtestCache,
                                FundamentalsPort fundamentals, BacktestService backtests) {
        return new MarketService(data, signals, backtestCache, fundamentals, backtests);
    }

    @Bean
    StreamService streamService(MarketService market, List<TickSink> sinks) {
        return new StreamService(market, sinks);
    }

    /** Relógio de Brasília: define o "hoje" da regra de defasagem dos indicadores oficiais. */
    @Bean
    Clock brasiliaClock() { return Clock.system(ZoneId.of("America/Sao_Paulo")); }

    @Bean
    BrazilRatesService brazilRatesService(BrazilRatesPort rates, Clock brasiliaClock) {
        return new BrazilRatesService(rates, brasiliaClock);
    }

    @Bean
    CompositionService compositionService(CompositionPort composition, Clock brasiliaClock) {
        return new CompositionService(composition, brasiliaClock);
    }

    @Bean
    ReportsService reportsService(ReportsPort reports, Clock brasiliaClock) { return new ReportsService(reports, brasiliaClock); }

    @Bean
    StatusService statusService(CacheStore cache, StreamService stream, List<ProviderStatusSource> providers,
                                MarketService market,
                                @Value("${meridianwealth.persistence.enabled:false}") boolean persistence) {
        return new StatusService(cache, persistence, stream, providers, market);
    }
}
