package dev.meridianwealth.infrastructure.crypto;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.provider.LiveMarketData;
import dev.meridianwealth.infrastructure.provider.Symbols.CryptoPair;
import dev.meridianwealth.infrastructure.provider.Universe;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stream de preços dos criptoativos com failover automático entre bolsas: Binance, depois Coinbase
 * Exchange e Kraken.
 *
 * <p>Regras (todas dirigidas por {@link #tick()}, uma vez por segundo):
 * <ul>
 *   <li><b>Bloqueio regional:</b> HTTP 451 ou 403 no handshake (servidor em nuvem de região bloqueada)
 *       isola a bolsa por 30 minutos e a conexão cai para a próxima da lista.</li>
 *   <li><b>Falhas:</b> reconexão com backoff exponencial (1 s a 60 s); após 3 falhas seguidas a bolsa fica
 *       isolada por 5 minutos e o stream passa para a próxima.</li>
 *   <li><b>Silêncio:</b> conexão aberta sem nenhuma mensagem por 20 s é derrubada e conta como falha.</li>
 *   <li><b>Retorno:</b> quando a bolsa preferida volta a ficar disponível, abre-se a nova conexão antes de
 *       fechar a antiga (sem lacuna de preços).</li>
 * </ul>
 * Cada preço vai para o {@code sink} com a fonte da bolsa que o gerou (ex.: "Coinbase WS"); a bolsa em uso
 * aparece no status. Binance cota em USDT; Coinbase e Kraken, em USD.
 */
@Component
public class CryptoStreamManager implements ProviderStatusSource {
    private static final Logger log = LoggerFactory.getLogger(CryptoStreamManager.class);

    static final long STALL_MS = 20_000;
    static final long REGION_BLOCK_MS = 30 * 60_000L;
    static final long FAILURE_COOLDOWN_MS = 5 * 60_000L;
    static final long MAX_BACKOFF_MS = 60_000;
    static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** Abre um WebSocket; separado para os testes simularem bloqueio, queda e silêncio sem rede. */
    interface WsConnector {
        CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
    }

    private static final class VenueState {
        final CryptoVenue venue;
        final List<String> pairs = new ArrayList<>();
        final Map<String, String> symbolByPair = new LinkedHashMap<>();
        long blockedUntil;
        long retryAt;
        int failures;
        String blockReason;
        String lastError;

        VenueState(CryptoVenue venue) { this.venue = venue; }
    }

    /** Uma conexão (ou tentativa) com uma bolsa. */
    private static final class Link {
        final VenueState state;
        final boolean probe;
        volatile WebSocket socket;
        volatile long lastMessageAt;
        boolean finished;

        Link(VenueState state, boolean probe) {
            this.state = state;
            this.probe = probe;
        }
    }

    private final Object lock = new Object();
    private final Consumer<Quote> sink;
    private final WsConnector connector;
    private final LongSupplier clock;
    private final boolean enabled;
    private final List<VenueState> venues = new ArrayList<>();
    private final Map<CryptoVenue, AtomicLong> tickCount = new EnumMap<>(CryptoVenue.class);
    private final Map<CryptoVenue, AtomicLong> failureCount = new EnumMap<>(CryptoVenue.class);
    private Link current;
    private Link connecting;
    private Link probing;
    private CryptoVenue lastActive;
    private long failovers;
    private long regionBlocks;
    private long stalls;

    @Autowired
    public CryptoStreamManager(LiveMarketData market,
                               @Value("${meridianwealth.crypto.stream.enabled:true}") boolean enabled) {
        this(market::onStreamQuote, List.of(CryptoVenue.values()), JdkConnector.INSTANCE, System::currentTimeMillis,
                enabled);
    }

    CryptoStreamManager(Consumer<Quote> sink, List<CryptoVenue> order, WsConnector connector, LongSupplier clock,
                        boolean enabled) {
        this.sink = sink;
        this.connector = connector;
        this.clock = clock;
        this.enabled = enabled;
        for (CryptoVenue v : order) {
            VenueState s = new VenueState(v);
            for (Universe.Entry e : Universe.all()) {
                CryptoPair p = e.symbols().crypto();
                String pair = p == null ? null : v.pair(p);
                if (pair != null) {
                    s.pairs.add(pair);
                    s.symbolByPair.put(pair.toUpperCase(Locale.ROOT), e.instrument().symbol());
                }
            }
            venues.add(s);
            tickCount.put(v, new AtomicLong());
            failureCount.put(v, new AtomicLong());
        }
    }

    public boolean enabled() { return enabled; }

    /** Avança a máquina de estados: detecta silêncio, escolhe a bolsa e abre conexão ou sondagem. */
    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void tick() {
        if (!enabled) return;
        long now = clock.getAsLong();
        synchronized (lock) {
            if (current != null && now - current.lastMessageAt > STALL_MS) {
                stalls++;
                Link dead = current;
                current = null;
                close(dead, "sem mensagens por " + (now - dead.lastMessageAt) / 1000 + " s");
                markFailure(dead.state, now, "silêncio de " + (now - dead.lastMessageAt) / 1000 + " s");
            }
            int preferred = preferredAvailable(now);
            if (preferred < 0) return;
            VenueState target = venues.get(preferred);
            if (target.retryAt > now || connecting != null || probing != null) return;
            if (current == null) {
                open(target, false);
            } else if (preferred < venues.indexOf(current.state)) {
                open(target, true);
            }
        }
    }

    /** Primeira bolsa da ordem de preferência que não está isolada. */
    private int preferredAvailable(long now) {
        for (int i = 0; i < venues.size(); i++) {
            if (venues.get(i).blockedUntil <= now) return i;
        }
        return -1;
    }

    private void open(VenueState vs, boolean probe) {
        Link link = new Link(vs, probe);
        if (probe) probing = link;
        else connecting = link;
        URI uri = vs.venue.uri(vs.pairs);
        connector.connect(uri, listener(link)).whenComplete((ws, err) -> {
            if (err != null) connectFailed(link, err);
        });
    }

    private void connectFailed(Link link, Throwable err) {
        long now = clock.getAsLong();
        synchronized (lock) {
            if (link.finished) return;
            link.finished = true;
            if (link == connecting) connecting = null;
            if (link == probing) probing = null;
            int status = statusOf(err);
            if (status == 451 || status == 403) {
                blockRegion(link.state, now, status);
            } else {
                markFailure(link.state, now, describe(err));
            }
        }
    }

    private void opened(Link link, WebSocket ws) {
        long now = clock.getAsLong();
        Link old = null;
        synchronized (lock) {
            if (link.finished) {
                ws.abort();
                return;
            }
            link.socket = ws;
            link.lastMessageAt = now;
            VenueState vs = link.state;
            vs.failures = 0;
            vs.retryAt = 0;
            vs.blockReason = null;
            if (link == connecting) connecting = null;
            if (link == probing) probing = null;
            old = current;
            current = link;
            if (lastActive != null && lastActive != vs.venue) failovers++;
            lastActive = vs.venue;
            log.info("Stream cripto: {} conectada ({} pares)", vs.venue.provider().label(), vs.pairs.size());
        }
        if (old != null) close(old, "substituída por " + link.state.venue.label());
        for (String message : link.state.venue.subscribe(link.state.pairs)) ws.sendText(message, true);
        ws.request(1);
    }

    private void lost(Link link, String reason) {
        long now = clock.getAsLong();
        synchronized (lock) {
            if (link.finished) return;
            link.finished = true;
            if (link == current) current = null;
            if (link == connecting) connecting = null;
            if (link == probing) probing = null;
            markFailure(link.state, now, reason);
        }
    }

    private void blockRegion(VenueState vs, long now, int status) {
        vs.blockedUntil = now + REGION_BLOCK_MS;
        vs.blockReason = "bloqueio regional (HTTP " + status + ")";
        vs.failures = 0;
        vs.lastError = "HTTP " + status;
        regionBlocks++;
        log.warn("Stream cripto: {} recusou a conexão com HTTP {}; isolada por {} min, passando para a próxima",
                vs.venue.label(), status, REGION_BLOCK_MS / 60_000);
    }

    private void markFailure(VenueState vs, long now, String reason) {
        vs.failures++;
        vs.lastError = reason;
        failureCount.get(vs.venue).incrementAndGet();
        if (vs.failures >= MAX_CONSECUTIVE_FAILURES) {
            vs.blockedUntil = now + FAILURE_COOLDOWN_MS;
            vs.blockReason = vs.failures + " falhas seguidas";
            vs.failures = 0;
            vs.retryAt = vs.blockedUntil;
            log.warn("Stream cripto: {} isolada por {} min após falhas seguidas ({})", vs.venue.label(),
                    FAILURE_COOLDOWN_MS / 60_000, reason);
        } else {
            vs.retryAt = now + Math.min(MAX_BACKOFF_MS, 1000L << (vs.failures - 1));
            log.warn("Stream cripto: {} falhou ({}); nova tentativa em {} s", vs.venue.label(), reason,
                    (vs.retryAt - now) / 1000);
        }
    }

    private void close(Link link, String reason) {
        link.finished = true;
        WebSocket ws = link.socket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            } catch (RuntimeException e) {
                ws.abort();
            }
        }
    }

    /** Código HTTP do handshake recusado, procurado em toda a cadeia de causas (0 se não houver). */
    static int statusOf(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof WebSocketHandshakeException h && h.getResponse() != null) return h.getResponse().statusCode();
            if (c.getCause() == c) break;
        }
        return 0;
    }

    private static String describe(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    private WebSocket.Listener listener(Link link) {
        return new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) { opened(link, ws); }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String message = buffer.toString();
                    buffer.setLength(0);
                    handle(link, message);
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
                lost(link, "conexão fechada (" + statusCode + (reason == null || reason.isEmpty() ? "" : " " + reason) + ")");
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket ws, Throwable error) { lost(link, describe(error)); }
        };
    }

    private void handle(Link link, String message) {
        List<CryptoTick> ticks;
        try {
            ticks = link.state.venue.parse(message);
        } catch (IOException e) {
            lost(link, e.getMessage());
            return;
        }
        long now = clock.getAsLong();
        synchronized (lock) {
            if (link != current) return;
            link.lastMessageAt = now;
        }
        for (CryptoTick t : ticks) {
            String symbol = link.state.symbolByPair.get(t.pair().toUpperCase(Locale.ROOT));
            if (symbol == null) continue;
            tickCount.get(link.state.venue).incrementAndGet();
            sink.accept(new Quote(symbol, t.price(), t.timeMillis(), link.state.venue.provider().label()));
        }
    }

    /** Bolsa em uso agora (nulo quando desconectado). */
    public CryptoVenue activeVenue() {
        synchronized (lock) {
            return current == null ? null : current.state.venue;
        }
    }

    @PreDestroy
    public void stop() {
        synchronized (lock) {
            for (Link l : new Link[] {current, connecting, probing}) {
                if (l != null) {
                    l.finished = true;
                    if (l.socket != null) l.socket.abort();
                }
            }
            current = null;
            connecting = null;
            probing = null;
        }
    }

    @Override
    public ProviderStatus status() {
        long now = clock.getAsLong();
        synchronized (lock) {
            CryptoVenue active = current == null ? null : current.state.venue;
            String circuit = !enabled ? "DESLIGADO" : active != null ? "CONECTADO" : "RECONECTANDO";
            String name = active == null ? "Cripto WS" : "Cripto WS (" + active.provider().label() + ")";
            long ticks = 0;
            long errors = 0;
            List<String> parts = new ArrayList<>();
            for (VenueState vs : venues) {
                ticks += tickCount.get(vs.venue).get();
                errors += failureCount.get(vs.venue).get();
                String state;
                if (current != null && current.state == vs) state = "conectada";
                else if (vs.blockedUntil > now) {
                    state = "isolada: " + vs.blockReason + ", volta em " + Math.max(1, (vs.blockedUntil - now + 59_999) / 60_000) + " min";
                } else state = vs.failures > 0 ? "instável (" + vs.lastError + ")" : "em espera";
                parts.add(vs.venue.label() + ": " + state);
            }
            String detail = enabled ? String.join(" | ", parts) : "stream de cripto desligado (MW_CRYPTO_STREAM=false)";
            return new ProviderStatus(name, enabled, circuit, ticks, errors, failovers, regionBlocks, detail);
        }
    }

    long stalls() { return stalls; }

    /** Conector real: um único HttpClient compartilhado por todas as conexões e reconexões. */
    private static final class JdkConnector implements WsConnector {
        static final JdkConnector INSTANCE = new JdkConnector();
        private final HttpClient http = HttpClient.newHttpClient();

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
            return http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(uri, listener);
        }
    }
}
