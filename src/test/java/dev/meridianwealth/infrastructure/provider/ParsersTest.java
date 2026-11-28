package dev.meridianwealth.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Fundamentals;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParsersTest {
    @Test void yahooChartSkipsNullBarsAndReadsMeta() throws IOException {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":101.5,\"regularMarketTime\":1700000200},"
                + "\"timestamp\":[1700000000,1700086400,1700172800],\"indicators\":{\"quote\":[{"
                + "\"open\":[1,2,3],\"high\":[2,3,4],\"low\":[0.5,1.5,2.5],\"close\":[1.5,null,3.5],\"volume\":[10,20,30]}]}}]}}";
        YahooClient.Chart c = YahooClient.parseChart(json);
        assertThat(c.candles()).hasSize(2);
        assertThat(c.candles().get(1).close()).isEqualTo(3.5);
        assertThat(c.price()).isEqualTo(101.5);
        assertThat(c.tsMillis()).isEqualTo(1_700_000_200_000L);
    }

    @Test void yahooBarWithZeroOpenAndLowBeforeTheOpenUsesTheCloseInsteadOfAFalseZero() throws IOException {
        // medido em 2026-11-25: a barra do dia, antes do pregão, vinha com open 0.0 e low 0.0 (e o app calculava close/open - 1 = infinito)
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":5.66,\"regularMarketTime\":1700000200},"
                + "\"timestamp\":[1700000000,1700086400],\"indicators\":{\"quote\":[{"
                + "\"open\":[5.7,0.0],\"high\":[5.84,5.66],\"low\":[5.61,0.0],\"close\":[5.73,5.66],\"volume\":[286816,0]}]}}]}}";
        var last = YahooClient.parseChart(json).candles().get(1);
        assertThat(last.open()).isEqualTo(5.66);
        assertThat(last.low()).isEqualTo(5.66);
        assertThat(last.high()).isEqualTo(5.66);
        assertThat(last.close() / last.open() - 1).isZero();
    }
    @Test void yahooOpenDayBarWithoutARealOpenIsNotUsed() throws IOException {
        // barra do dia sem fechamento (pregão ainda não abriu) e com open 0.0 / low 0.0: não vira candle com mínima falsa
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":140.8,\"regularMarketTime\":1700086500,\"timezone\":\"UTC\",\"exchangeTimezoneName\":\"UTC\"},"
                + "\"timestamp\":[1700000000,1700086400],\"indicators\":{\"quote\":[{"
                + "\"open\":[141.8,0.0],\"high\":[141.8,140.8],\"low\":[139.7,0.0],\"close\":[140.69,null],\"volume\":[135647,0]}]}}]}}";
        var candles = YahooClient.parseChart(json).candles();
        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).low()).isEqualTo(139.7);
    }
    @Test void yahooErrorPayloadThrows() {
        assertThatThrownBy(() -> YahooClient.parseChart("{\"chart\":{\"result\":null,\"error\":{}}}")).isInstanceOf(IOException.class);
    }

    @Test void stooqHistoryAndQuote() throws IOException {
        List<Candle> c = StooqClient.parseHistory("Date,Open,High,Low,Close,Volume\n2024-01-02,10,11,9,10.5,1000\n2024-01-03,10.5,12,10,11.5,2000\n");
        assertThat(c).hasSize(2);
        assertThat(c.get(1).close()).isEqualTo(11.5);
        Quote q = StooqClient.parseQuote("SPY", "Symbol,Date,Time,Open,High,Low,Close,Volume\nSPY.US,2024-01-03,22:00:00,1,2,0.5,1.7,100\n");
        assertThat(q.price()).isEqualTo(1.7);
    }

    @Test void stooqRejectsKeyPageAndNoData() {
        assertThatThrownBy(() -> StooqClient.parseHistory("Get your apikey: ...")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> StooqClient.parseQuote("X", "Symbol,Date,Time,Open,High,Low,Close,Volume\nX,N/D,N/D,N/D,N/D,N/D,N/D,N/D\n"))
                .isInstanceOf(IOException.class);
    }

    @Test void frankfurterSeriesIsSortedByDate() throws IOException {
        List<Candle> c = FrankfurterClient.parseSeries("{\"rates\":{\"2024-01-03\":{\"USD\":1.1},\"2024-01-02\":{\"USD\":1.09}}}", "USD");
        assertThat(c).extracting(Candle::close).containsExactly(1.09, 1.1);
    }

    @Test void finnhubQuoteNewsAndFundamentals() throws IOException {
        assertThat(FinnhubClient.parseQuote("AAPL", "{\"c\":190.5,\"t\":1700000000}").price()).isEqualTo(190.5);
        assertThatThrownBy(() -> FinnhubClient.parseQuote("X", "{\"c\":0}")).isInstanceOf(IOException.class);
        assertThat(FinnhubClient.parseNews("[{\"headline\":\"War fears\",\"source\":\"Reuters\"}]")).hasSize(1);
        ObjectMapper m = new ObjectMapper();
        Fundamentals f = FinnhubClient.parseFundamentals("AAPL", m.readTree("{\"peTTM\":28.5,\"marketCapitalization\":3000}"),
                m.readTree("{\"finnhubIndustry\":\"Technology\"}"));
        assertThat(f.peRatio()).isEqualTo(28.5);
        assertThat(f.industry()).isEqualTo("Technology");
        assertThat(f.netMargin()).isNull();
    }

    @Test void fredSkipsMissingObservations() throws IOException {
        assertThat(FredClient.parseLatest("{\"observations\":[{\"value\":\".\"},{\"value\":\"4.21\"}]}")).isEqualTo(4.21);
    }

    @Test void rssItemsCarryTheLinkAndThePublicationTimeAndUnreadableTimesBecomeNullNeverInvented() throws Exception {
        String xml = "<rss xmlns:atom=\"http://www.w3.org/2005/Atom\"><channel><atom:link href=\"https://feed\"/><item><title>Oil jumps</title><link>https://www.bbc.com/news/1</link>"
                + "<pubDate>Fri, 27 Nov 2026 10:00:00 GMT</pubDate></item><item><title>Sem horário</title><link>https://exemplo.org/2</link><pubDate>ontem de manhã</pubDate></item>"
                + "<item><title>Sem nada</title></item></channel></rss>";
        List<Headline> h = RssNewsClient.parse(xml, "BBC");
        assertThat(h).hasSize(3);
        assertThat(h.get(0).link()).isEqualTo("https://www.bbc.com/news/1");
        assertThat(h.get(0).publishedAt()).isEqualTo(java.time.Instant.parse("2026-11-27T10:00:00Z"));
        assertThat(h.get(1).publishedAt()).as("horário ilegível").isNull();
        assertThat(h.get(1).link()).isEqualTo("https://exemplo.org/2");
        assertThat(h.get(2).link()).isNull();
        assertThat(h.get(2).publishedAt()).isNull();
    }

    @Test void finnhubNewsKeepTheUrlAndTheEpochTime() throws Exception {
        List<Headline> h = FinnhubClient.parseNews("[{\"headline\":\"War fears\",\"source\":\"Reuters\",\"url\":\"https://exemplo.org/w\",\"datetime\":1795000000}]");
        assertThat(h.get(0).link()).isEqualTo("https://exemplo.org/w");
        assertThat(h.get(0).publishedAt()).isEqualTo(java.time.Instant.ofEpochSecond(1795000000L));
    }

    @Test void yahooDayChangeUsesTheLastCloseOfAPreviousDayAndIsNullWithoutOne() {
        long day = 86_400_000L, base = java.time.Instant.parse("2026-11-25T15:00:00Z").toEpochMilli();
        var candles = List.of(new dev.meridianwealth.domain.Candle(base - day, 20, 21, 19, 20.0, 0), new dev.meridianwealth.domain.Candle(base, 20, 22, 19, 21.0, 0),
                new dev.meridianwealth.domain.Candle(base + day, 21, 22, 20, 21.5, 0));
        // preço de hoje (27/11, mesma data da última barra) sobre o fechamento da barra anterior (26/11): 24 / 21 - 1
        assertThat(YahooClient.dayChange(new YahooClient.Chart(candles, 24.0, base + day))).isCloseTo(24.0 / 21.0 - 1, org.assertj.core.data.Offset.offset(1e-12));
        // preço mais novo que a última barra: o fechamento da última barra é a referência
        assertThat(YahooClient.dayChange(new YahooClient.Chart(candles, 24.0, base + 3 * day))).isCloseTo(24.0 / 21.5 - 1, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(YahooClient.dayChange(new YahooClient.Chart(List.of(candles.get(2)), 24.0, base + day))).as("sem fechamento anterior").isNull();
        assertThat(YahooClient.dayChange(new YahooClient.Chart(List.of(), 24.0, base))).isNull();
    }

    @Test void rssTitlesAreParsedAndDoctypeIsRejected() throws Exception {
        List<Headline> h = RssNewsClient.parse("<rss><channel><item><title>Ceasefire talks</title></item></channel></rss>", "BBC");
        assertThat(h).extracting(Headline::title).containsExactly("Ceasefire talks");
        assertThatThrownBy(() -> RssNewsClient.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><rss/>", "x"))
                .isInstanceOf(Exception.class);
    }
}
