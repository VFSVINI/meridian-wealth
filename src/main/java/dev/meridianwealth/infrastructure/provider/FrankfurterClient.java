package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Frankfurter (câmbio do BCE, sem chave): série diária e última taxa. Sem OHLC nem volume. */
@Component
public class FrankfurterClient implements ProviderStatusSource {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpGateway http;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("Frankfurter", 5, 2.0);

    public FrankfurterClient(HttpGateway http) { this.http = http; }

    public List<Candle> history(String base, String quote) {
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        String url = "https://api.frankfurter.app/" + end.minusDays(730) + ".." + end + "?from=" + base + "&to=" + quote;
        return executor.call(() -> parseSeries(http.get(url, Map.of()), quote));
    }

    public Quote latest(String symbol, String base, String quote) {
        String url = "https://api.frankfurter.app/latest?from=" + base + "&to=" + quote;
        return executor.call(() -> {
            JsonNode n = JSON.readTree(http.get(url, Map.of()));
            double rate = n.path("rates").path(quote).asDouble(Double.NaN);
            if (Double.isNaN(rate)) throw new IOException("Frankfurter: sem taxa");
            long t = LocalDate.parse(n.path("date").asText()).atTime(16, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
            return new Quote(symbol, rate, t, "Frankfurter");
        });
    }

    static List<Candle> parseSeries(String json, String quote) throws IOException {
        JsonNode rates = JSON.readTree(json).path("rates");
        if (!rates.isObject()) throw new IOException("Frankfurter: formato inesperado");
        TreeMap<String, Double> sorted = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = rates.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            sorted.put(e.getKey(), e.getValue().path(quote).asDouble());
        }
        List<Candle> out = new ArrayList<>();
        sorted.forEach((d, v) -> {
            long t = LocalDate.parse(d).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            out.add(new Candle(t, v, v, v, v, 0));
        });
        return out;
    }

    @Override
    public ProviderStatus status() { return executor.stats(true); }
}
