package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.infrastructure.resilience.ProviderStats;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Yahoo Finance (endpoint público de gráfico): histórico diário, cotação atrasada, VIX, dólar e juros. */
@Component
public class YahooClient implements ProviderInfo {
    public record Chart(List<Candle> candles, double price, long tsMillis) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpGateway http;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("Yahoo", 5, 2.0);

    public YahooClient(HttpGateway http) { this.http = http; }

    public Chart chart(String symbol, String range) {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/"
                + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "?range=" + range + "&interval=1d";
        return executor.call(() -> parseChart(http.get(url, Map.of())));
    }

    public List<Candle> history(String symbol) { return chart(symbol, "2y").candles(); }

    public double lastPrice(String symbol) { return chart(symbol, "5d").price(); }

    public Quote quote(String symbol, String yahooSymbol) {
        Chart c = chart(yahooSymbol, "5d");
        return new Quote(symbol, c.price(), c.tsMillis(), "Yahoo");
    }

    static Chart parseChart(String json) throws IOException {
        JsonNode res = JSON.readTree(json).path("chart").path("result").path(0);
        if (res.isMissingNode() || res.isNull()) throw new IOException("Yahoo: resposta sem resultado");
        JsonNode meta = res.path("meta");
        JsonNode ts = res.path("timestamp");
        JsonNode q = res.path("indicators").path("quote").path(0);
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            JsonNode close = q.path("close").path(i);
            if (close.isNull() || close.isMissingNode()) continue;
            double c = close.asDouble();
            out.add(new Candle(ts.get(i).asLong() * 1000, num(q, "open", i, c), num(q, "high", i, c),
                    num(q, "low", i, c), c, num(q, "volume", i, 0)));
        }
        double price = meta.path("regularMarketPrice").asDouble(Double.NaN);
        long t = meta.path("regularMarketTime").asLong(0) * 1000;
        if (Double.isNaN(price) && !out.isEmpty()) price = out.get(out.size() - 1).close();
        if (t == 0 && !out.isEmpty()) t = out.get(out.size() - 1).time();
        if (Double.isNaN(price)) throw new IOException("Yahoo: sem preço");
        return new Chart(out, price, t);
    }

    private static double num(JsonNode q, String field, int i, double fallback) {
        JsonNode n = q.path(field).path(i);
        return n.isNull() || n.isMissingNode() ? fallback : n.asDouble();
    }

    @Override
    public ProviderStats stats() { return executor.stats(true); }
}
