package dev.meridianwealth.infrastructure.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.infrastructure.provider.Provider;
import dev.meridianwealth.infrastructure.provider.Symbols.CryptoPair;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Bolsas de cripto com WebSocket público, em ordem de preferência: Binance (1 s por par), Coinbase
 * Exchange e Kraken (a cada negócio). Cada uma sabe seu endereço, as mensagens de assinatura e como
 * ler uma mensagem de preço. Formatos conferidos com mensagens reais gravadas em 2026-11-21
 * ({@code src/test/resources/fixtures/crypto}).
 */
public enum CryptoVenue {
    /** Cotado em USDT; o miniTicker sai uma vez por segundo por par. */
    BINANCE("Binance", Provider.BINANCE_WS),
    /** Cotado em USD; o canal ticker sai a cada negócio. */
    COINBASE("Coinbase", Provider.COINBASE_WS),
    /** Cotado em USD; o canal ticker v2 sai a cada negócio. */
    KRAKEN("Kraken", Provider.KRAKEN_WS);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String label;
    private final Provider provider;

    CryptoVenue(String label, Provider provider) {
        this.label = label;
        this.provider = provider;
    }

    public String label() { return label; }

    public Provider provider() { return provider; }

    /** Código do par nesta bolsa (nulo quando a bolsa não lista o ativo). */
    public String pair(CryptoPair p) {
        return switch (this) {
            case BINANCE -> p.binance();
            case COINBASE -> p.coinbase();
            case KRAKEN -> p.kraken();
        };
    }

    public URI uri(List<String> pairs) {
        return switch (this) {
            case BINANCE -> URI.create("wss://stream.binance.com:9443/stream?streams="
                    + pairs.stream().map(p -> p.toLowerCase(Locale.ROOT) + "@miniTicker").collect(Collectors.joining("/")));
            case COINBASE -> URI.create("wss://ws-feed.exchange.coinbase.com");
            case KRAKEN -> URI.create("wss://ws.kraken.com/v2");
        };
    }

    /** Mensagens enviadas ao abrir a conexão (a Binance assina pela própria URL). */
    public List<String> subscribe(List<String> pairs) {
        String quoted = pairs.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
        return switch (this) {
            case BINANCE -> List.of();
            case COINBASE -> List.of("{\"type\":\"subscribe\",\"product_ids\":[" + quoted + "],\"channels\":[\"ticker\"]}");
            case KRAKEN -> List.of("{\"method\":\"subscribe\",\"params\":{\"channel\":\"ticker\",\"symbol\":[" + quoted + "]}}");
        };
    }

    /**
     * Lê uma mensagem: devolve os preços que ela traz (lista vazia para confirmações, status, heartbeat
     * e qualquer outro tipo) ou lança {@link IOException} quando a bolsa devolve um erro.
     */
    public List<CryptoTick> parse(String message) throws IOException {
        JsonNode n = JSON.readTree(message);
        if (n == null || !n.isObject()) return List.of();
        return switch (this) {
            case BINANCE -> parseBinance(n);
            case COINBASE -> parseCoinbase(n);
            case KRAKEN -> parseKraken(n);
        };
    }

    private static List<CryptoTick> parseBinance(JsonNode n) throws IOException {
        if (n.has("code") && n.has("msg")) throw new IOException("Binance: " + n.path("msg").asText());
        JsonNode d = n.has("data") ? n.get("data") : n;
        if (!"24hrMiniTicker".equals(d.path("e").asText())) return List.of();
        return one(d.path("s").asText(), d.path("c").asText(), d.path("E").asLong(0));
    }

    private static List<CryptoTick> parseCoinbase(JsonNode n) throws IOException {
        String type = n.path("type").asText();
        if ("error".equals(type)) throw new IOException("Coinbase: " + n.path("message").asText() + " " + n.path("reason").asText());
        if (!"ticker".equals(type)) return List.of();
        return one(n.path("product_id").asText(), n.path("price").asText(), isoMillis(n.path("time").asText()));
    }

    private static List<CryptoTick> parseKraken(JsonNode n) throws IOException {
        if ("subscribe".equals(n.path("method").asText()) && n.has("success") && !n.path("success").asBoolean(true)) {
            throw new IOException("Kraken: " + n.path("error").asText("assinatura recusada"));
        }
        if (!"ticker".equals(n.path("channel").asText())) return List.of();
        String type = n.path("type").asText();
        if (!"update".equals(type) && !"snapshot".equals(type)) return List.of();
        List<CryptoTick> out = new ArrayList<>();
        for (JsonNode d : n.path("data")) {
            out.addAll(one(d.path("symbol").asText(), d.path("last").asText(), isoMillis(d.path("timestamp").asText())));
        }
        return out;
    }

    private static List<CryptoTick> one(String pair, String price, long time) {
        if (pair.isEmpty() || time <= 0) return List.of();
        double p;
        try {
            p = Double.parseDouble(price);
        } catch (NumberFormatException e) {
            return List.of();
        }
        return Double.isFinite(p) && p > 0 ? List.of(new CryptoTick(pair, p, time)) : List.of();
    }

    private static long isoMillis(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
