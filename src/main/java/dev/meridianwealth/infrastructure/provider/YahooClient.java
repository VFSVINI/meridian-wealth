package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Yahoo Finance (endpoint público de gráfico): histórico diário, cotação atrasada, VIX, dólar e juros. */
@Component
public class YahooClient implements ProviderStatusSource {
    public record Chart(List<Candle> candles, double price, long tsMillis) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpGateway http;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("Yahoo", 5, 2.0);

    public YahooClient(HttpGateway http) { this.http = http; }

    /** Gráfico diário para cotação (falha rápida no rate limit local). */
    public Chart chart(String symbol, String range) {
        return executor.call(() -> parseChart(http.get(url(symbol, range), Map.of())));
    }

    /** Histórico de 2 anos em segundo plano: aguarda o próximo token em vez de descartar a carga. */
    public Chart historyChart(String symbol) {
        return executor.callPatiently(() -> parseChart(http.get(url(symbol, "2y"), Map.of())),
                ResilientExecutor.BACKGROUND_WAIT);
    }

    public List<Candle> history(String symbol) { return historyChart(symbol).candles(); }

    private static String url(String symbol, String range) {
        return "https://query1.finance.yahoo.com/v8/finance/chart/"
                + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "?range=" + range + "&interval=1d";
    }

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
        double metaPrice = meta.path("regularMarketPrice").asDouble(Double.NaN);
        long metaTime = meta.path("regularMarketTime").asLong(0) * 1000;
        ZoneId zone = zoneOf(meta);
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            long barTime = ts.get(i).asLong() * 1000;
            JsonNode close = q.path("close").path(i);
            if (close.isNull() || close.isMissingNode()) {
                // Barra do dia ainda aberta: a Yahoo manda open/high/low reais e close nulo. Só a última
                // barra é aproveitada, com o close do meta, e apenas se a cotação for do mesmo dia da barra.
                boolean lastBar = i == ts.size() - 1;
                boolean sameDay = metaTime > 0 && sameLocalDay(barTime, metaTime, zone);
                /* antes do pregão a Yahoo manda a barra do dia com abertura e mínima 0 (medido em FIIs): sem abertura real a barra não é aproveitada */
                if (lastBar && sameDay && !Double.isNaN(metaPrice) && price(q, "open", i, 0) > 0) {
                    double o = price(q, "open", i, 0);
                    double hi = Math.max(price(q, "high", i, o), metaPrice);
                    double lo = Math.min(price(q, "low", i, o), metaPrice);
                    out.add(new Candle(barTime, o, hi, lo, metaPrice, num(q, "volume", i, 0)));
                }
                continue;
            }
            double c = close.asDouble();
            out.add(new Candle(barTime, price(q, "open", i, c), price(q, "high", i, c),
                    price(q, "low", i, c), c, num(q, "volume", i, 0)));
        }
        double price = metaPrice;
        long t = metaTime;
        if (Double.isNaN(price) && !out.isEmpty()) price = out.get(out.size() - 1).close();
        if (t == 0 && !out.isEmpty()) t = out.get(out.size() - 1).time();
        if (Double.isNaN(price)) throw new IOException("Yahoo: sem preço");
        return new Chart(out, price, t);
    }

    private static ZoneId zoneOf(JsonNode meta) {
        try {
            return ZoneId.of(meta.path("exchangeTimezoneName").asText("UTC"));
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static boolean sameLocalDay(long a, long b, ZoneId zone) {
        return Instant.ofEpochMilli(a).atZone(zone).toLocalDate().equals(Instant.ofEpochMilli(b).atZone(zone).toLocalDate());
    }

    private static boolean hasNumber(JsonNode q, String field, int i) {
        JsonNode n = q.path(field).path(i);
        return !n.isNull() && !n.isMissingNode();
    }

    /**
     * Preço de uma barra: nulo ou não positivo vira o fechamento. Antes da abertura a Yahoo manda a barra do dia com abertura e mínima 0 (medido
     * em FIIs como HGLG11 e XPSF11): aceitar o zero dava variação infinita na tela e uma mínima falsa no gráfico.
     */
    private static double price(JsonNode q, String field, int i, double fallback) {
        double v = num(q, field, i, fallback);
        return v > 0 ? v : fallback;
    }

    private static double num(JsonNode q, String field, int i, double fallback) {
        JsonNode n = q.path(field).path(i);
        return n.isNull() || n.isMissingNode() ? fallback : n.asDouble();
    }

    @Override
    public ProviderStatus status() { return executor.stats(true); }
}
