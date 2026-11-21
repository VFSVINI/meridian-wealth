package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter do feed pago Twelve Data (endpoint {@code /quote}), desligado por padrão.
 *
 * <p>Liga com {@code MW_PAID_FEED=twelvedata} e {@code MW_PAID_FEED_KEY}. O selo de atraso vem de
 * {@code MW_PAID_FEED_FRESHNESS} (padrão {@code DELAYED}, o mais conservador). Cobre os ativos que têm
 * código nos EUA; outros mercados exigem mapear códigos de bolsa do plano contratado. Não foi validado
 * contra a API real neste projeto (sem assinatura).
 */
@Component
public class TwelveDataFeed implements PaidQuoteFeed {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpGateway http;
    private final boolean enabled;
    private final String apiKey;
    private final Freshness freshness;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("Feed pago (Twelve Data)", 8, 8.0);

    public TwelveDataFeed(HttpGateway http,
                          @Value("${meridianwealth.paid-feed.provider:none}") String provider,
                          @Value("${meridianwealth.paid-feed.api-key:}") String apiKey,
                          @Value("${meridianwealth.paid-feed.freshness:DELAYED}") String freshness) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.enabled = "twelvedata".equalsIgnoreCase(provider) && !this.apiKey.isEmpty();
        this.freshness = Freshness.valueOf(freshness.strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public String label() { return "Twelve Data"; }

    @Override
    public boolean enabled() { return enabled; }

    @Override
    public Freshness freshness() { return freshness; }

    @Override
    public boolean covers(Symbols symbols) { return symbols.finnhub() != null; }

    @Override
    public Quote quote(String symbol, Symbols symbols) {
        if (!enabled) throw new IllegalStateException("Feed pago desligado");
        String url = "https://api.twelvedata.com/quote?symbol=" + URLEncoder.encode(symbols.finnhub(), StandardCharsets.UTF_8);
        return executor.call(() -> parseQuote(symbol, http.get(url, Map.of("Authorization", "apikey " + apiKey))));
    }

    static Quote parseQuote(String symbol, String json) throws IOException {
        JsonNode n = JSON.readTree(json);
        if ("error".equals(n.path("status").asText())) {
            throw new IOException("Twelve Data: " + n.path("message").asText("erro"));
        }
        double price = n.path("close").asDouble(Double.NaN);
        long ts = n.path("timestamp").asLong(0) * 1000;
        if (Double.isNaN(price) || ts == 0) throw new IOException("Twelve Data: resposta sem preço ou horário");
        return new Quote(symbol, price, ts, "Twelve Data");
    }

    @Override
    public ProviderStatus status() { return executor.stats(enabled); }
}
