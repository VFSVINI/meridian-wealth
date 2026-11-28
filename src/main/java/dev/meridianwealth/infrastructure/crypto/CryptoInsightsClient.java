package dev.meridianwealth.infrastructure.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.port.CryptoInsightsPort;
import dev.meridianwealth.domain.crypto.FearGreed;
import dev.meridianwealth.domain.crypto.HourlyVolume;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/**
 * Fontes públicas e sem chave do relógio de 24 horas e do medo e ganância. As barras horárias do Bitcoin vêm da Coinbase, depois da Kraken e da Binance (a primeira que responder:
 * servidores em nuvem de algumas regiões são barrados em uma delas); o volume é convertido para dólares. O índice vem da alternative.me. A Yahoo não serve: o volume horário dela
 * vem zerado em mais da metade das barras do Bitcoin (medido em 2026-11-28).
 */
@Component
public class CryptoInsightsClient implements CryptoInsightsPort {
    private static final Logger LOG = Logger.getLogger(CryptoInsightsClient.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> HEADERS = Map.of("Accept", "application/json");

    private final HttpGateway http;

    public CryptoInsightsClient(HttpGateway http) { this.http = http; }

    @Override
    public Optional<Hourly> hourly(String symbol) {
        if (!"BTC".equals(symbol)) return Optional.empty();
        String[][] venues = {{"Coinbase", "https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=3600"},
                {"Kraken", "https://api.kraken.com/0/public/OHLC?pair=XBTUSD&interval=60"}, {"Binance", "https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1h&limit=168"}};
        for (String[] v : venues) {
            try {
                String body = http.get(v[1], HEADERS);
                List<HourlyVolume.Bar> bars = switch (v[0]) {
                    case "Coinbase" -> parseCoinbase(body);
                    case "Kraken" -> parseKraken(body);
                    default -> parseBinance(body);
                };
                if (!bars.isEmpty()) return Optional.of(new Hourly(v[0], bars));
            } catch (IOException | RuntimeException e) {
                LOG.fine("Barras horárias da " + v[0] + " indisponíveis: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<FearGreed> fearGreed() {
        try {
            return Optional.ofNullable(parseFearGreed(http.get("https://api.alternative.me/fng/?limit=1", HEADERS)));
        } catch (IOException | RuntimeException e) {
            LOG.fine("Medo e ganância indisponível: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Coinbase: {@code [tempo(s), mínima, máxima, abertura, fechamento, volume em BTC]}; o volume vira dólares pelo fechamento da barra. */
    static List<HourlyVolume.Bar> parseCoinbase(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (!root.isArray()) throw new IOException("Coinbase: resposta inesperada");
        List<HourlyVolume.Bar> out = new ArrayList<>();
        for (JsonNode c : root) {
            if (c.size() < 6) continue;
            out.add(new HourlyVolume.Bar(c.get(0).asLong() * 1000, c.get(5).asDouble() * c.get(4).asDouble()));
        }
        return out;
    }

    /** Kraken: {@code [tempo(s), abertura, máxima, mínima, fechamento, vwap, volume em BTC, negócios]} (textos); o volume vira dólares pelo vwap. A última barra é a em andamento. */
    static List<HourlyVolume.Bar> parseKraken(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (root.path("error").size() > 0) throw new IOException("Kraken: " + root.path("error").get(0).asText());
        List<HourlyVolume.Bar> out = new ArrayList<>();
        for (JsonNode pair : root.path("result")) {
            if (!pair.isArray()) continue;
            for (JsonNode c : pair) {
                if (c.size() < 7) continue;
                out.add(new HourlyVolume.Bar(c.get(0).asLong() * 1000, Double.parseDouble(c.get(6).asText()) * Double.parseDouble(c.get(5).asText())));
            }
        }
        return out;
    }

    /** Binance: {@code [abertura(ms), ..., volume em BTC, fechamento(ms), volume em USDT, ...]}; o volume em USDT já está em dólares. */
    static List<HourlyVolume.Bar> parseBinance(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (!root.isArray()) throw new IOException("Binance: resposta inesperada");
        List<HourlyVolume.Bar> out = new ArrayList<>();
        for (JsonNode c : root) {
            if (c.size() < 8) continue;
            out.add(new HourlyVolume.Bar(c.get(0).asLong(), Double.parseDouble(c.get(7).asText())));
        }
        return out;
    }

    /** alternative.me: {@code {"data":[{"value":"10","value_classification":"Extreme Fear","timestamp":"1795824000"}]}}; nulo quando o valor não é um número de 0 a 100. */
    static FearGreed parseFearGreed(String json) throws IOException {
        JsonNode d = JSON.readTree(json).path("data").path(0);
        if (d.isMissingNode()) throw new IOException("alternative.me: resposta sem dados");
        try {
            return FearGreed.of(Integer.parseInt(d.path("value").asText()), d.path("value_classification").asText(null), Long.parseLong(d.path("timestamp").asText()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
