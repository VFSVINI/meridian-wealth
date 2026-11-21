package dev.meridianwealth.infrastructure.provider;

import static dev.meridianwealth.testsupport.FixtureHttpGateway.resource;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.meridianwealth.application.HistoryResult;
import dev.meridianwealth.application.LiveUpdate;
import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.infrastructure.cache.InMemoryCacheStore;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Integridade das barras com o dia em andamento. Fixture real gravada em 2026-11-20: a barra de 20/11
 * vem com open/high/low reais e close nulo, e o meta traz o preço de 20/11 (689,81 às 21:00 UTC).
 */
class OpenDayIntegrityTest {
    private static final String OPEN_DAY = "/fixtures/yahoo/SPY-2y-dia-aberto.json";
    private static final long NOV19 = 1_795_098_600_000L;
    private static final long NOV20 = 1_795_185_000_000L;

    private static LiveMarketData market(String chartJson) {
        HttpGateway gw = (url, h) -> {
            if (url.contains("finance.yahoo.com") && url.contains("range=2y")) return chartJson;
            throw new IOException("sem rede");
        };
        CandleStore none = new CandleStore() {
            @Override public void saveAll(String symbol, String source, List<Candle> candles) { }

            @Override public List<Candle> load(String symbol, int limit) { return List.of(); }
        };
        YahooClient yahoo = new YahooClient(gw);
        FinnhubClient finnhub = new FinnhubClient(gw, "");
        return new LiveMarketData(yahoo, new StooqClient(gw, false), new BrapiClient(gw, ""), new FrankfurterClient(gw),
                finnhub, new TwelveDataFeed(gw, "none", "", "DELAYED"), none, new InMemoryCacheStore(),
                new LiveMacroData(yahoo, new FredClient(gw, ""), finnhub, new RssNewsClient(gw)), 30, 600, 25);
    }

    private static Candle at(List<Candle> c, long time) {
        return c.stream().filter(x -> x.time() == time).findFirst().orElseThrow();
    }

    @Test void parserKeepsTheOpenDayBarWithRealOhlcAndLeavesYesterdayIntact() throws IOException {
        List<Candle> c = YahooClient.parseChart(resource(OPEN_DAY)).candles();
        Candle today = c.get(c.size() - 1);
        assertThat(today.time()).isEqualTo(NOV20);
        assertThat(today.open()).isEqualTo(713.5599975585938);
        assertThat(today.close()).isEqualTo(689.81);
        assertThat(today.low()).isEqualTo(686.7520141601562);
        assertThat(today.high()).isEqualTo(718.2899780273438);
        assertThat(at(c, NOV19).close()).isEqualTo(715.8699951171875);
    }

    @Test void liveQuoteNeverRewritesYesterdaysBar() {
        LiveMarketData d = market(resource(OPEN_DAY));
        HistoryResult h = d.history("SPY", 500);
        LiveUpdate u = d.next("SPY").orElseThrow();
        assertThat(u.newBar()).isFalse();
        assertThat(u.candle().time()).isEqualTo(NOV20);
        assertThat(u.candle().close()).isEqualTo(689.81);
        assertThat(at(h.candles(), NOV19).close()).isEqualTo(715.8699951171875);
    }

    @Test void quoteFromALaterDayOpensANewBarAtTheObservedPrice() throws IOException {
        // Mesma resposta real sem a barra de 20/11: a última barra é 19/11, mas o meta traz o preço de 20/11.
        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(resource(OPEN_DAY));
        ObjectNode result = (ObjectNode) root.path("chart").path("result").path(0);
        ArrayNode ts = (ArrayNode) result.path("timestamp");
        ts.remove(ts.size() - 1);
        ObjectNode quote = (ObjectNode) result.path("indicators").path("quote").path(0);
        for (String f : List.of("open", "high", "low", "close", "volume")) {
            ArrayNode a = (ArrayNode) quote.path(f);
            a.remove(a.size() - 1);
        }
        LiveMarketData d = market(root.toString());
        HistoryResult h = d.history("SPY", 500);
        LiveUpdate u = d.next("SPY").orElseThrow();

        assertThat(u.newBar()).isTrue();
        assertThat(u.candle().open()).as("abertura é o primeiro preço observado, não o fechamento anterior").isEqualTo(689.81);
        assertThat(u.candle().close()).isEqualTo(689.81);
        assertThat(h.candles().get(h.candles().size() - 1).close()).isEqualTo(715.8699951171875);
    }
}
