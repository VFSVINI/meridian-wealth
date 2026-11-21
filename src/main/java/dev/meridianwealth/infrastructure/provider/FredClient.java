package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** FRED (St. Louis Fed, chave gratuita): último valor de séries macro como DGS10. */
@Component
public class FredClient implements ProviderStatusSource {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpGateway http;
    private final String apiKey;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("FRED", 5, 1.0);

    public FredClient(HttpGateway http, @Value("${meridianwealth.fred.api-key:}") String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
    }

    public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }

    public double latest(String seriesId) {
        String url = "https://api.stlouisfed.org/fred/series/observations?file_type=json&sort_order=desc&limit=10"
                + "&series_id=" + seriesId + "&api_key=" + apiKey;
        return executor.call(() -> parseLatest(http.get(url, Map.of())));
    }

    static double parseLatest(String json) throws IOException {
        for (JsonNode o : JSON.readTree(json).path("observations")) {
            String v = o.path("value").asText(".");
            if (!".".equals(v)) return Double.parseDouble(v);
        }
        throw new IOException("FRED: sem observações válidas");
    }

    @Override
    public ProviderStatus status() { return executor.stats(enabled()); }
}
