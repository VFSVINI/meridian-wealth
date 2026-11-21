package dev.meridianwealth.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.HistoryResult;
import dev.meridianwealth.application.LiveUpdate;
import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.infrastructure.cache.InMemoryCacheStore;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static List<Candle> stored(int n) {
        long start = LocalDate.of(2024, 1, 2).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Candle(start + i * 86_400_000L, 50, 51, 49, 50 + i * 0.01, 10));
        return out;
    }

    private static CandleStore store(List<Candle> saved, List<Candle> toLoad) {
        return new CandleStore() {
            @Override public void saveAll(String symbol, String source, List<Candle> candles) { saved.addAll(candles); }

            @Override public List<Candle> load(String symbol, int limit) { return toLoad; }
        };
    }

    private static LiveMarketData build(HttpGateway gw, CandleStore store) {
        YahooClient yahoo = new YahooClient(gw);
        FinnhubClient finnhub = new FinnhubClient(gw, "");
        LiveMacroData macro = new LiveMacroData(yahoo, new FredClient(gw, ""), finnhub, new RssNewsClient(gw));
        return new LiveMarketData(yahoo, new StooqClient(gw), new FrankfurterClient(gw), finnhub, store,
                new InMemoryCacheStore(), macro, 30, 600, 25);
    }

    @Test void failsOverFromYahooToStooqAndPersists() {
        List<Candle> saved = new ArrayList<>();
        HttpGateway gw = (url, h) -> {
            if (url.contains("yahoo")) throw new IOException("HTTP 503");
            if (url.contains("stooq.com/q/d/l")) return csv(200);
            throw new IOException("indisponível");
        };
        HistoryResult r = build(gw, store(saved, List.of())).history("SPY", 500);
        assertThat(r.source()).isEqualTo("Stooq");
        assertThat(r.freshness()).isIn(Freshness.DELAYED, Freshness.END_OF_DAY);
        assertThat(r.candles()).hasSize(200);
        assertThat(saved).hasSize(200);
    }

    @Test void usesLastStoredRealDataWhenEveryProviderFails() {
        HttpGateway gw = (url, h) -> { throw new IOException("sem rede"); };
        LiveMarketData d = build(gw, store(new ArrayList<>(), stored(200)));
        HistoryResult r = d.history("AAPL", 300);
        assertThat(r.source()).isEqualTo("Banco");
        assertThat(r.freshness()).isEqualTo(Freshness.STORED);
        assertThat(d.next("AAPL")).get().extracting(u -> u.quote().freshness()).isEqualTo(Freshness.STORED);
    }

    @Test void returnsNothingWhenNoRealDataExists() {
        HttpGateway gw = (url, h) -> { throw new IOException("sem rede"); };
        LiveMarketData d = build(gw, store(new ArrayList<>(), List.of()));
        HistoryResult r = d.history("AAPL", 300);
        assertThat(r.available()).isFalse();
        assertThat(r.candles()).isEmpty();
        assertThat(d.next("AAPL")).isEmpty();
    }

    @Test void liveBarReflectsTheExactQuoteWithoutSyntheticMovement() {
        HttpGateway gw = (url, h) -> {
            if (url.contains("stooq.com/q/d/l")) return csv(200);
            throw new IOException("indisponível");
        };
        LiveMarketData d = build(gw, store(new ArrayList<>(), List.of()));
        HistoryResult hist = d.history("SPY", 500);
        double last = hist.candles().get(hist.candles().size() - 1).close();
        for (int i = 0; i < 50; i++) {
            LiveUpdate u = d.next("SPY").orElseThrow();
            assertThat(u.newBar()).isFalse();
            assertThat(u.candle().close()).isEqualTo(last);
            assertThat(u.quote().source()).isEqualTo("Stooq");
        }
    }

    @Test void providerQuoteReplacesStoredDataAndKeepsItsOrigin() {
        AtomicBoolean online = new AtomicBoolean(false);
        FixtureHttpGateway fixtures = new FixtureHttpGateway();
        HttpGateway gw = (url, h) -> {
            if (!online.get()) throw new IOException("sem rede");
            return fixtures.get(url, h);
        };
        LiveMarketData d = build(gw, store(new ArrayList<>(), stored(200)));
        d.history("SPY", 500);
        online.set(true);
        d.refresh("SPY");
        LiveUpdate u = d.next("SPY").orElseThrow();
        assertThat(u.quote().source()).isEqualTo("Yahoo");
        assertThat(u.quote().freshness()).isIn(Freshness.DELAYED, Freshness.END_OF_DAY);
        assertThat(u.quote().quoteTime()).isEqualTo(1_795_208_404_000L);
        assertThat(u.candle().close()).isEqualTo(689.81);
    }

    @Test void yahooHistoryCarriesTheRealQuoteTimeNotTheBarOpen() {
        LiveMarketData d = build(new FixtureHttpGateway(), store(new ArrayList<>(), List.of()));
        HistoryResult r = d.history("SPY", 500);
        assertThat(r.source()).isEqualTo("Yahoo");
        assertThat(r.quoteTime()).isEqualTo(1_795_208_404_000L);
        assertThat(r.candles().get(r.candles().size() - 1).time()).isNotEqualTo(r.quoteTime());
    }

    @Test void freshnessFollowsTheSourceContractAndTheSession() {
        assertThat(LiveMarketData.freshness("Finnhub", true)).isEqualTo(Freshness.REAL_TIME);
        assertThat(LiveMarketData.freshness("Yahoo", true)).isEqualTo(Freshness.DELAYED);
        assertThat(LiveMarketData.freshness("Stooq", true)).isEqualTo(Freshness.DELAYED);
        assertThat(LiveMarketData.freshness("Yahoo", false)).isEqualTo(Freshness.END_OF_DAY);
        assertThat(LiveMarketData.freshness("Frankfurter", true)).isEqualTo(Freshness.END_OF_DAY);
        assertThat(LiveMarketData.freshness("Banco", true)).isEqualTo(Freshness.STORED);
        assertThat(LiveMarketData.freshness("Banco", false)).isEqualTo(Freshness.STORED);
    }
}
