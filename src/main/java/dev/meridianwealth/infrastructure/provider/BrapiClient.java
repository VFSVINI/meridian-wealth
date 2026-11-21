package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * brapi.dev (B3): histórico diário de ações brasileiras. Sem token, a API responde para um conjunto
 * de tickers de teste (PETR4, VALE3, ITUB4, MGLU3); com {@code MW_BRAPI_TOKEN} cobre a B3 inteira.
 *
 * <p>Não é usado para cotação intradiária: observamos em 2026-11-20 (feriado na B3) que o campo
 * {@code regularMarketTime} trazia o horário da consulta, não o do último negócio. Para não exibir
 * um horário falso, as cotações da B3 vêm da Yahoo ({@code .SA}), que informa o horário real.
 */
@Component
public class BrapiClient implements ProviderStatusSource {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "https://brapi.dev/api/quote/";

    private final HttpGateway http;
    private final String token;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("brapi", 3, 0.5);

    public BrapiClient(HttpGateway http, @Value("${meridianwealth.brapi.token:}") String token) {
        this.http = http;
        this.token = token == null ? "" : token.strip();
    }

    public boolean hasToken() { return !token.isEmpty(); }

    public List<Candle> history(String ticker) {
        String url = BASE + URLEncoder.encode(ticker, StandardCharsets.UTF_8) + "?range=2y&interval=1d";
        return executor.callPatiently(() -> parseHistory(http.get(url, headers())), ResilientExecutor.BACKGROUND_WAIT);
    }

    private Map<String, String> headers() {
        return hasToken() ? Map.of("Authorization", "Bearer " + token) : Map.of();
    }

    static List<Candle> parseHistory(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (root.has("error") && root.path("error").asBoolean(false)) {
            throw new IOException("brapi: " + root.path("message").asText("erro"));
        }
        JsonNode bars = root.path("results").path(0).path("historicalDataPrice");
        if (!bars.isArray()) throw new IOException("brapi: resposta sem historicalDataPrice");
        List<Candle> out = new ArrayList<>();
        for (JsonNode b : bars) {
            JsonNode close = b.path("close");
            if (close.isNull() || close.isMissingNode()) continue;
            double c = close.asDouble();
            out.add(new Candle(b.path("date").asLong() * 1000, num(b, "open", c), num(b, "high", c), num(b, "low", c),
                    c, num(b, "volume", 0)));
        }
        out.sort((x, y) -> Long.compare(x.time(), y.time()));
        return out;
    }

    private static double num(JsonNode n, String field, double fallback) {
        JsonNode v = n.path(field);
        return v.isNull() || v.isMissingNode() ? fallback : v.asDouble();
    }

    @Override
    public ProviderStatus status() { return executor.stats(true); }
}
