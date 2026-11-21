package dev.chronosquant.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chronosquant.application.HistoryResult;
import dev.chronosquant.application.StreamSettings;
import dev.chronosquant.application.port.CandleStore;
import dev.chronosquant.domain.Candle;
import dev.chronosquant.infrastructure.cache.InMemoryCacheStore;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveMarketDataTest {
    private static String csv(int n) {
        StringBuilder b = new StringBuilder("Date,Open,High,Low,Close,Volume\n");
        LocalDate d = LocalDate.of(2023, 1, 2);
        for (int i = 0; i < n; i++) {
            double p = 100 + i * 0.1;
            b.append(d.plusDays(i)).append(',').append(p).append(',').append(p + 1).append(',').append(p - 1)
                    .append(',').append(p + 0.5).append(",1000\n");
        }
        return b.toString();
    }

    private static CandleStore memoryStore(List<Candle> saved) {
        return new CandleStore() {
            @Override public void saveAll(String symbol, String source, List<Candle> candles) { saved.addAll(candles); }

            @Override public List<Candle> load(String symbol, int limit) { return List.of(); }
        };
    }

    private static LiveMarketData build(HttpGateway gw, List<Candle> saved) {
        SimulatedFlow sim = new SimulatedFlow();
        YahooClient yahoo = new YahooClient(gw);
        FredClient fred = new FredClient(gw, "");
        FinnhubClient finnhub = new FinnhubClient(gw, "");
        LiveMacroData macro = new LiveMacroData(sim, yahoo, fred, finnhub, new RssNewsClient(gw), "auto");
        return new LiveMarketData(sim, yahoo, new StooqClient(gw), new FrankfurterClient(gw), finnhub, memoryStore(saved),
                new InMemoryCacheStore(), new StreamSettings(false), macro, "auto", 30, 600, 25);
    }

    @Test void failsOverFromYahooToStooqAndPersists() {
        List<Candle> saved = new ArrayList<>();
        HttpGateway gw = (url, h) -> {
            if (url.contains("yahoo")) throw new IOException("HTTP 503");
            if (url.contains("stooq.com/q/d/l")) return csv(200);
            throw new IOException("indisponível");
        };
        HistoryResult r = build(gw, saved).history("SPY", 500);
        assertThat(r.source()).isEqualTo("Stooq");
        assertThat(r.simulated()).isFalse();
        assertThat(r.candles()).hasSize(200);
        assertThat(saved).hasSize(200);
    }

    @Test void fallsBackToSimulationWhenEveryProviderFails() {
        HttpGateway gw = (url, h) -> { throw new IOException("sem rede"); };
        HistoryResult r = build(gw, new ArrayList<>()).history("AAPL", 300);
        assertThat(r.simulated()).isTrue();
        assertThat(r.source()).isEqualTo("SIMULADO");
        assertThat(r.candles()).hasSize(300);
    }

    @Test void liveBarUpdatesInPlaceAndFlowStaysNearTheRealQuote() {
        HttpGateway gw = (url, h) -> csv(200);
        LiveMarketData d = build(gw, new ArrayList<>());
        HistoryResult hist = d.history("SPY", 500);
        double last = hist.candles().get(hist.candles().size() - 1).close();
        var u = d.next("SPY");
        assertThat(u.newBar()).isFalse();
        assertThat(u.simulated()).isFalse();
        assertThat(u.candle().close()).isEqualTo(last);
    }
}
