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

    @Test void rssTitlesAreParsedAndDoctypeIsRejected() throws Exception {
        List<Headline> h = RssNewsClient.parse("<rss><channel><item><title>Ceasefire talks</title></item></channel></rss>", "BBC");
        assertThat(h).extracting(Headline::title).containsExactly("Ceasefire talks");
        assertThatThrownBy(() -> RssNewsClient.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><rss/>", "x"))
                .isInstanceOf(Exception.class);
    }
}
