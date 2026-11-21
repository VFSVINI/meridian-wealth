package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Fundamentals;
import dev.meridianwealth.infrastructure.resilience.ProviderStats;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Finnhub (plano gratuito, 60 req/min, exige chave): cotação, fundamentos e notícias gerais. */
@Component
public class FinnhubClient implements ProviderInfo {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "https://finnhub.io/api/v1";

    private final HttpGateway http;
    private final String apiKey;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("Finnhub", 10, 1.0);

    public FinnhubClient(HttpGateway http, @Value("${meridianwealth.finnhub.api-key:}") String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
    }

    public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }

    private Map<String, String> auth() { return Map.of("X-Finnhub-Token", apiKey); }

    public Quote quote(String symbol, String finnhubSymbol) {
        String url = BASE + "/quote?symbol=" + URLEncoder.encode(finnhubSymbol, StandardCharsets.UTF_8);
        return executor.call(() -> parseQuote(symbol, http.get(url, auth())));
    }

    public Fundamentals fundamentals(String symbol, String finnhubSymbol) {
        String enc = URLEncoder.encode(finnhubSymbol, StandardCharsets.UTF_8);
        return executor.call(() -> {
            JsonNode m = JSON.readTree(http.get(BASE + "/stock/metric?metric=all&symbol=" + enc, auth())).path("metric");
            JsonNode p = JSON.readTree(http.get(BASE + "/stock/profile2?symbol=" + enc, auth()));
            return parseFundamentals(symbol, m, p);
        });
    }

    public List<Headline> news() {
        return executor.call(() -> parseNews(http.get(BASE + "/news?category=general", auth())));
    }

    static Quote parseQuote(String symbol, String json) throws IOException {
        JsonNode n = JSON.readTree(json);
        double c = n.path("c").asDouble(0);
        if (c <= 0) throw new IOException("Finnhub: cotação vazia");
        long t = n.path("t").asLong(0) * 1000;
        return new Quote(symbol, c, t == 0 ? System.currentTimeMillis() : t, "Finnhub");
    }

    static Fundamentals parseFundamentals(String symbol, JsonNode metric, JsonNode profile) {
        return new Fundamentals(symbol, text(profile, "finnhubIndustry"),
                dbl(metric, "marketCapitalization") != null ? dbl(metric, "marketCapitalization") * 1_000_000 : null,
                dbl(metric, "peTTM"), dbl(metric, "netProfitMarginTTM"), dbl(metric, "revenueGrowthTTMYoy"), "Finnhub");
    }

    static List<Headline> parseNews(String json) throws IOException {
        JsonNode arr = JSON.readTree(json);
        List<Headline> out = new ArrayList<>();
        for (JsonNode n : arr) {
            String h = n.path("headline").asText("");
            if (!h.isBlank()) out.add(new Headline(h, "Finnhub/" + n.path("source").asText("")));
            if (out.size() >= 25) break;
        }
        return out;
    }

    private static Double dbl(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isNumber() ? v.asDouble() : null;
    }

    private static String text(JsonNode n, String f) {
        String s = n.path(f).asText("");
        return s.isBlank() ? null : s;
    }

    @Override
    public ProviderStats stats() { return executor.stats(enabled()); }
}
