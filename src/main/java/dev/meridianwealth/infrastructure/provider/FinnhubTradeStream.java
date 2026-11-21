package dev.meridianwealth.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.application.port.ProviderStatusSource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * WebSocket de negócios (trades) da Finnhub para as ações dos EUA do universo.
 *
 * <p>Só conecta com {@code FINNHUB_API_KEY} definida (e {@code MW_FINNHUB_WS} diferente de false). Cada
 * mensagem pode trazer vários negócios; vale o mais recente por ativo, entregue ao {@link LiveMarketData}
 * com a fonte "Finnhub WS". Queda de conexão reconecta com backoff exponencial (1 s a 60 s).
 */
@Component
public class FinnhubTradeStream implements ProviderStatusSource {
    private static final Logger log = LoggerFactory.getLogger(FinnhubTradeStream.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_BACKOFF_MS = 60_000;

    /** Negócio da Finnhub: símbolo da Finnhub, preço, horário (ms) e volume. */
    public record Trade(String finnhubSymbol, double price, long time, double volume) {}

    private final String apiKey;
    private final boolean wanted;
    private final Consumer<Quote> sink;
    private final Map<String, String> bySymbol = new LinkedHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "finnhub-ws-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicLong trades = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private volatile WebSocket socket;

    @Autowired
    public FinnhubTradeStream(LiveMarketData market,
                              @Value("${meridianwealth.finnhub.api-key:}") String apiKey,
                              @Value("${meridianwealth.finnhub.websocket:true}") boolean wanted) {
        this(apiKey, wanted, market::onStreamQuote);
    }

    FinnhubTradeStream(String apiKey, boolean wanted, Consumer<Quote> sink) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.wanted = wanted;
        this.sink = sink;
        for (Universe.Entry e : Universe.all()) {
            if (e.symbols().finnhub() != null) bySymbol.put(e.symbols().finnhub(), e.instrument().symbol());
        }
    }

    public boolean enabled() { return wanted && !apiKey.isEmpty(); }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled()) {
            log.info("Finnhub WS desligado (sem FINNHUB_API_KEY); ações dos EUA seguem pelo polling REST");
            return;
        }
        connect();
    }

    private void connect() {
        if (stopped.get()) return;
        HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create("wss://ws.finnhub.io?token=" + apiKey), listener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        errors.incrementAndGet();
                        log.warn("Finnhub WS: falha ao conectar ({})", err.getMessage());
                        scheduleReconnect();
                    }
                });
    }

    void scheduleReconnect() {
        connected.set(false);
        if (stopped.get()) return;
        long delay = Math.min(MAX_BACKOFF_MS, 1000L << Math.min(6, attempts.getAndIncrement()));
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    WebSocket.Listener listener() {
        return new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                socket = ws;
                connected.set(true);
                attempts.set(0);
                for (String s : bySymbol.keySet()) ws.sendText("{\"type\":\"subscribe\",\"symbol\":\"" + s + "\"}", true);
                log.info("Finnhub WS conectado; {} ativos assinados", bySymbol.size());
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String msg = buffer.toString();
                    buffer.setLength(0);
                    handle(msg);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                log.warn("Finnhub WS fechado ({} {}); reconectando", statusCode, reason);
                scheduleReconnect();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                errors.incrementAndGet();
                log.warn("Finnhub WS com erro ({}); reconectando", error.getMessage());
                scheduleReconnect();
            }
        };
    }

    void handle(String message) {
        try {
            Map<String, Trade> latest = new LinkedHashMap<>();
            for (Trade t : parseTrades(message)) {
                Trade prev = latest.get(t.finnhubSymbol());
                if (prev == null || t.time() >= prev.time()) latest.put(t.finnhubSymbol(), t);
            }
            latest.values().forEach(t -> {
                String symbol = bySymbol.get(t.finnhubSymbol());
                if (symbol == null) return;
                trades.incrementAndGet();
                sink.accept(new Quote(symbol, t.price(), t.time(), Provider.FINNHUB_WS.label()));
            });
        } catch (IOException e) {
            errors.incrementAndGet();
            log.debug("Finnhub WS: mensagem ilegível ({})", e.getMessage());
        }
    }

    /** Lê mensagens {@code {"type":"trade","data":[{"s","p","t","v"}]}}; ping e outros tipos viram lista vazia. */
    static List<Trade> parseTrades(String json) throws IOException {
        JsonNode n = JSON.readTree(json);
        String type = n.path("type").asText();
        if ("error".equals(type)) throw new IOException("Finnhub WS: " + n.path("msg").asText("erro"));
        if (!"trade".equals(type)) return List.of();
        List<Trade> out = new ArrayList<>();
        for (JsonNode d : n.path("data")) {
            if (!d.hasNonNull("s") || !d.hasNonNull("p") || !d.hasNonNull("t")) continue;
            out.add(new Trade(d.get("s").asText(), d.get("p").asDouble(), d.get("t").asLong(), d.path("v").asDouble(0)));
        }
        return out;
    }

    @PreDestroy
    public void stop() {
        stopped.set(true);
        scheduler.shutdownNow();
        WebSocket ws = socket;
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "encerrando");
    }

    boolean connected() { return connected.get(); }

    @Override
    public ProviderStatus status() {
        String state = !enabled() ? "DESLIGADO" : connected.get() ? "CONECTADO" : "RECONECTANDO";
        return new ProviderStatus("Finnhub WS", enabled(), state, trades.get(), errors.get(), 0, 0);
    }
}
