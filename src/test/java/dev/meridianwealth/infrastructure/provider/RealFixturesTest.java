package dev.meridianwealth.infrastructure.provider;

import static dev.meridianwealth.testsupport.FixtureHttpGateway.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.HistoryResult;
import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.infrastructure.cache.InMemoryCacheStore;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contrato dos parsers verificado contra respostas reais gravadas em 2026-11-20 (dívida 7). */
class RealFixturesTest {
    @Test void brapiHistoryParsesTheRealPayloadInChronologicalOrder() throws IOException {
        List<Candle> c = BrapiClient.parseHistory(resource("/fixtures/brapi/PETR4-2y.json"));
        assertThat(c).hasSizeGreaterThan(400);
        assertThat(c).isSortedAccordingTo((a, b) -> Long.compare(a.time(), b.time()));
        assertThat(c).allSatisfy(x -> {
            assertThat(x.close()).isPositive();
            assertThat(x.high()).isGreaterThanOrEqualTo(x.low());
        });
    }

    @Test void brapiErrorPayloadIsRejected() {
        assertThatThrownBy(() -> BrapiClient.parseHistory("{\"error\":true,\"message\":\"Ticker exige token\"}"))
                .hasMessageContaining("exige token");
        assertThatThrownBy(() -> BrapiClient.parseHistory("{\"results\":[{}]}")).isInstanceOf(IOException.class);
    }

    @Test void frankfurterRealSeriesAndLatest() throws IOException {
        List<Candle> s = FrankfurterClient.parseSeries(resource("/fixtures/frankfurter/USD-BRL-2y.json"), "BRL");
        assertThat(s).hasSizeGreaterThan(500);
        assertThat(s.get(s.size() - 1).close()).isBetween(3.0, 10.0);
        var q = new FrankfurterClient(new FixtureHttpGateway()).latest("USDBRL", "USD", "BRL");
        assertThat(q.price()).isEqualTo(5.938);
        assertThat(q.source()).isEqualTo("Frankfurter");
    }

    @Test void stooqJavascriptChallengeIsRejectedWithAClearMessage() {
        assertThatThrownBy(() -> StooqClient.parseHistory(resource("/fixtures/stooq/spy.us-bloqueio.html")))
                .hasMessageContaining("verificação JavaScript");
    }

    @Test void yahooReportsExchangeCurrencyInMinorUnitsForJohannesburg() throws IOException {
        YahooClient.Chart c = YahooClient.parseChart(resource("/fixtures/yahoo/NPN.JO-5d.json"));
        assertThat(c.price()).isGreaterThan(1000);
        assertThat(resource("/fixtures/yahoo/NPN.JO-5d.json")).contains("\"currency\":\"ZAc\"");
        assertThat(YahooClient.parseChart(resource("/fixtures/yahoo/7203.T-5d.json")).price()).isPositive();
    }

    @Test void b3HistoryComesFromBrapiFirstWithEndOfDaySeal() {
        CandleStore none = new CandleStore() {
            @Override public void saveAll(String symbol, String source, List<Candle> candles) { }

            @Override public List<Candle> load(String symbol, int limit) { return List.of(); }
        };
        FixtureHttpGateway gw = new FixtureHttpGateway();
        YahooClient yahoo = new YahooClient(gw);
        FinnhubClient finnhub = new FinnhubClient(gw, "");
        LiveMarketData d = new LiveMarketData(yahoo, new StooqClient(gw, false), new BrapiClient(gw, ""),
                new FrankfurterClient(gw), finnhub, new TwelveDataFeed(gw, "none", "", "DELAYED"), none, new InMemoryCacheStore(),
                new LiveMacroData(yahoo, new FredClient(gw, ""), finnhub, new RssNewsClient(gw)), 30, 600, 25);
        HistoryResult r = d.history("PETR4", 500);
        assertThat(r.source()).isEqualTo("brapi");
        assertThat(r.freshness()).isEqualTo(Freshness.END_OF_DAY);
        assertThat(gw.requested()).noneMatch(u -> u.contains("stooq"));
    }

    @Test void staleOnlyWhenTheSessionIsOpenAndTheQuoteIsTooOld() {
        long now = 10_000_000L;
        assertThat(Freshness.DELAYED.isStale(now - 1_900_000, now, true)).isTrue();
        assertThat(Freshness.DELAYED.isStale(now - 1_900_000, now, false)).isFalse();
        assertThat(Freshness.REAL_TIME.isStale(now - 60_000, now, true)).isFalse();
        assertThat(Freshness.END_OF_DAY.isStale(0, now, true)).isFalse();
    }
}
