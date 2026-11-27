package dev.meridianwealth.config;

import dev.meridianwealth.application.BacktestService;
import dev.meridianwealth.application.BrazilHistoryService;
import dev.meridianwealth.application.BrazilRatesService;
import dev.meridianwealth.application.CompositionService;
import dev.meridianwealth.application.ReportsService;
import dev.meridianwealth.application.port.ReportsPort;
import dev.meridianwealth.application.port.CompositionPort;
import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.OrderImpactService;
import dev.meridianwealth.application.ThesisService;
import dev.meridianwealth.application.StatusService;
import dev.meridianwealth.application.StreamService;
import dev.meridianwealth.application.port.BacktestCache;
import dev.meridianwealth.application.port.BrazilHistoryPort;
import dev.meridianwealth.application.port.BrazilRatesPort;
import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.application.port.FundamentalsPort;
import dev.meridianwealth.application.port.MarketDataPort;
import dev.meridianwealth.application.port.EventPublisher;
import dev.meridianwealth.application.port.FxHistoryPort;
import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.application.portfolio.PortfolioService;
import dev.meridianwealth.application.privacy.DataSubjectService;
import dev.meridianwealth.application.risk.CorrelationService;
import dev.meridianwealth.application.risk.MonteCarloService;
import dev.meridianwealth.application.port.SignalStore;
import dev.meridianwealth.application.port.TickSink;
import dev.meridianwealth.infrastructure.events.InProcessEventBus;
import dev.meridianwealth.infrastructure.events.PortfolioEventMetrics;
import io.micrometer.core.instrument.MeterRegistry;
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
    BrazilHistoryService brazilHistoryService(BrazilHistoryPort history, Clock brasiliaClock) {
        return new BrazilHistoryService(history, brasiliaClock);
    }

    @Bean
    CompositionService compositionService(CompositionPort composition, Clock brasiliaClock) {
        return new CompositionService(composition, brasiliaClock);
    }

    @Bean
    ReportsService reportsService(ReportsPort reports, Clock brasiliaClock) { return new ReportsService(reports, brasiliaClock); }

    @Bean
    OrderImpactService orderImpactService(MarketService market) { return new OrderImpactService(market); }

    @Bean
    ThesisService thesisService(MarketService market) { return new ThesisService(market); }

    @Bean
    InProcessEventBus eventBus(MeterRegistry metrics) {
        InProcessEventBus bus = new InProcessEventBus();
        bus.subscribe(new PortfolioEventMetrics(metrics));
        return bus;
    }

    @Bean
    PortfolioService portfolioService(PositionStore positions, PortfolioLedger ledger, MarketService market, BrazilRatesService rates, FxHistoryPort fxHistory,
                                      EventPublisher events, Clock brasiliaClock) {
        return new PortfolioService(positions, ledger, market, rates, fxHistory, events, brasiliaClock);
    }

    @Bean
    MonteCarloService.PortfolioSource riskPortfolioSource(PortfolioService portfolio, MarketService market) {
        return who -> portfolio.list(who).lines().stream().map(l -> new MonteCarloService.Holding(l.assetKey(), l.valuation().position().asset().label(), l.symbol(),
                l.valuation().position().currency().name(), l.valuation().currentValueBrl(), l.type().category().name(), l.type().category().label(), riskOf(market, l.symbol()))).toList();
    }

    /** Faixa de risco do motor para o ativo, ou {@code null} sem dado suficiente (a tela chama de "sem classificação"). */
    private static String riskOf(MarketService market, String symbol) {
        if (symbol == null) return null;
        try {
            return market.assessment(symbol).risk().name();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Bean
    MonteCarloService monteCarloService(MonteCarloService.PortfolioSource source, MarketService market) { return new MonteCarloService(source, market::candles); }

    @Bean
    CorrelationService correlationService(MonteCarloService.PortfolioSource source, MarketService market) { return new CorrelationService(source, market::candles); }

    @Bean
    DataSubjectService dataSubjectService(AuthService auth, UserStore users, RefreshTokenStore refreshTokens, PositionStore positions, PortfolioLedger ledger,
                                          Clock brasiliaClock) {
        return new DataSubjectService(auth, users, refreshTokens, positions, ledger, brasiliaClock);
    }

    @Bean
    StatusService statusService(CacheStore cache, StreamService stream, List<ProviderStatusSource> providers,
                                MarketService market,
                                @Value("${meridianwealth.persistence.enabled:false}") boolean persistence) {
        return new StatusService(cache, persistence, stream, providers, market);
    }
}
