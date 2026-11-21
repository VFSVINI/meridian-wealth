package dev.meridianwealth.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.Quote;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Failover, bloqueio regional, silêncio e recuperação, com conector, relógio e sockets controlados pelo teste. */
class CryptoStreamManagerTest {
    private static final long MIN = 60_000L;

    static final class FakeSocket implements WebSocket {
        final List<String> sent = new ArrayList<>();
        boolean closed;
        boolean aborted;

        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }

        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }

        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }

        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closed = true;
            return CompletableFuture.completedFuture(this);
        }

        @Override public void request(long n) { }

        @Override public String getSubprotocol() { return ""; }

        @Override public boolean isOutputClosed() { return closed; }

        @Override public boolean isInputClosed() { return closed; }

        @Override public void abort() { aborted = true; }
    }

    /** Conector programável: por bolsa, aceita, recusa com um HTTP ou falha de rede. */
    static final class ScriptedConnector implements CryptoStreamManager.WsConnector {
        final Map<String, Object> behavior = new HashMap<>();   // host → Integer (HTTP) | "net" | "ok"
        final List<String> attempts = new ArrayList<>();
        final Map<String, WebSocket.Listener> listeners = new HashMap<>();
        final Map<String, FakeSocket> sockets = new HashMap<>();
        final List<FakeSocket> created = new ArrayList<>();

        long openSockets() {
            return created.stream().filter(w -> !w.closed && !w.aborted).count();
        }

        @Override public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
            String host = uri.getHost();
            attempts.add(host);
            Object b = behavior.getOrDefault(host, "ok");
            if (b instanceof Integer status) {
                HttpResponse<?> resp = (HttpResponse<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {HttpResponse.class}, (p, m, a) -> m.getName().equals("statusCode") ? status : null);
                return CompletableFuture.failedFuture(new CompletionException(new WebSocketHandshakeException(resp)));
            }
            if ("net".equals(b)) return CompletableFuture.failedFuture(new java.net.ConnectException("sem rota"));
            FakeSocket ws = new FakeSocket();
            listeners.put(host, listener);
            sockets.put(host, ws);
            created.add(ws);
            listener.onOpen(ws);
            if ("flap".equals(b)) {
                ws.closed = true;
                listener.onClose(ws, 1008, "excesso de conexões");
            } else if ("subscription-error".equals(b)) {
                String error = host.equals(BINANCE)
                        ? "{\"code\":2,\"msg\":\"Invalid request\"}"
                        : "{\"type\":\"error\",\"message\":\"Failed to subscribe\",\"reason\":\"BTC-XXX is not a valid product\"}";
                listener.onText(ws, error, true);
            }
            return CompletableFuture.completedFuture(ws);
        }

        void send(String host, String text) { listeners.get(host).onText(sockets.get(host), text, true); }
    }

    private static final String BINANCE = "stream.binance.com";
    private static final String COINBASE = "ws-feed.exchange.coinbase.com";
    private static final String KRAKEN = "ws.kraken.com";

    private final AtomicLong now = new AtomicLong(10_000_000L);
    private final ScriptedConnector net = new ScriptedConnector();
    private final List<Quote> quotes = new ArrayList<>();
    private CryptoStreamManager manager;

    @BeforeEach
    void setUp() {
        manager = new CryptoStreamManager(quotes::add, List.of(CryptoVenue.values()), net, now::get, true);
    }

    private void tick(long advanceMs) {
        now.addAndGet(advanceMs);
        manager.tick();
    }

    /** Avança o relógio em passos de 10 s, com a bolsa em uso mandando preços (como uma bolsa real faria). */
    private void advanceWithTraffic(long totalMs, String ignored) {
        long left = totalMs;
        while (left > 0) {
            long step = Math.min(10_000, left);
            left -= step;
            now.addAndGet(step);
            CryptoVenue active = manager.activeVenue();
            if (active != null) {
                String host = switch (active) {
                    case BINANCE -> BINANCE;
                    case COINBASE -> COINBASE;
                    case KRAKEN -> KRAKEN;
                };
                String msg = switch (active) {
                    case BINANCE -> "{\"stream\":\"btcusdt@miniTicker\",\"data\":{\"e\":\"24hrMiniTicker\",\"E\":1795225436018,\"s\":\"BTCUSDT\",\"c\":\"59008.11\"}}";
                    case COINBASE -> coinbaseTicker("BTC-USD", 59_000, "2026-11-21T01:44:08Z");
                    case KRAKEN -> "{\"channel\":\"ticker\",\"type\":\"update\",\"data\":[{\"symbol\":\"BTC/USD\",\"last\":59000.0,\"timestamp\":\"2026-11-21T01:44:08.000000Z\"}]}";
                };
                net.listeners.get(host).onText(net.sockets.get(host), msg, true);
            }
            manager.tick();
        }
    }

    private static String coinbaseTicker(String product, double price, String time) {
        return "{\"type\":\"ticker\",\"product_id\":\"" + product + "\",\"price\":\"" + price + "\",\"time\":\"" + time + "\"}";
    }

    @Test void connectsToBinanceFirstAndRoutesPricesToTheRightInstrument() {
        tick(0);
        assertThat(net.attempts).containsExactly(BINANCE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.BINANCE);
        assertThat(net.sockets.get(BINANCE).sent).as("a Binance assina pela URL").isEmpty();

        net.send(BINANCE, "{\"stream\":\"btcusdt@miniTicker\",\"data\":{\"e\":\"24hrMiniTicker\",\"E\":1795225436018,\"s\":\"BTCUSDT\",\"c\":\"59008.11\"}}");
        net.send(BINANCE, "{\"stream\":\"xrpusdt@miniTicker\",\"data\":{\"e\":\"24hrMiniTicker\",\"E\":1795225437000,\"s\":\"XRPUSDT\",\"c\":\"0.9063\"}}");
        net.send(BINANCE, "{\"stream\":\"dogeusdt@miniTicker\",\"data\":{\"e\":\"24hrMiniTicker\",\"E\":1795225437000,\"s\":\"DOGEUSDT\",\"c\":\"0.1\"}}");

        assertThat(quotes).containsExactly(new Quote("BTC", 59008.11, 1795225436018L, "Binance WS"),
                new Quote("XRP", 0.9063, 1795225437000L, "Binance WS"));
        ProviderStatus s = manager.status();
        assertThat(s.circuit()).isEqualTo("CONECTADO");
        assertThat(s.name()).isEqualTo("Cripto WS (Binance WS)");
        assertThat(s.successes()).isEqualTo(2);
    }

    @Test void regionalBlockHttp451IsolatesBinanceAndFailsOverToCoinbaseWithItsSubscription() {
        net.behavior.put(BINANCE, 451);
        tick(0);
        assertThat(manager.activeVenue()).as("a recusa isola a bolsa, a conexão ainda não existe").isNull();
        tick(0);

        assertThat(net.attempts).containsExactly(BINANCE, COINBASE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.COINBASE);
        assertThat(net.sockets.get(COINBASE).sent).hasSize(1);
        assertThat(net.sockets.get(COINBASE).sent.get(0)).contains("\"channels\":[\"ticker\"]").contains("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD");

        net.send(COINBASE, coinbaseTicker("ETH-USD", 1620.03, "2026-11-21T01:44:08.734550Z"));
        assertThat(quotes).extracting(Quote::source).containsExactly("Coinbase WS");
        assertThat(quotes.get(0).symbol()).isEqualTo("ETH");

        ProviderStatus s = manager.status();
        assertThat(s.rateLimited()).as("bloqueios regionais").isEqualTo(1);
        assertThat(s.shortCircuited()).as("trocas de bolsa").isZero();
        assertThat(s.detail()).contains("Binance: isolada: bloqueio regional (HTTP 451)", "Coinbase: conectada");
    }

    @Test void http403IsAlsoTreatedAsRegionalBlock() {
        net.behavior.put(BINANCE, 403);
        net.behavior.put(COINBASE, 403);
        tick(0);
        tick(0);
        tick(0);
        assertThat(net.attempts).containsExactly(BINANCE, COINBASE, KRAKEN);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.KRAKEN);
        assertThat(net.sockets.get(KRAKEN).sent.get(0)).contains("\"channel\":\"ticker\"").contains("BTC/USD");
    }

    @Test void networkFailuresBackOffExponentiallyThenFailOverAfterThreeInARow() {
        net.behavior.put(BINANCE, "net");
        tick(0);
        assertThat(net.attempts).hasSize(1);
        tick(500);
        assertThat(net.attempts).as("ainda dentro do backoff de 1 s").hasSize(1);
        tick(600);
        assertThat(net.attempts).hasSize(2);
        tick(1500);
        assertThat(net.attempts).as("backoff de 2 s").hasSize(2);
        tick(600);
        assertThat(net.attempts).containsExactly(BINANCE, BINANCE, BINANCE);

        tick(0);
        assertThat(net.attempts).as("três falhas seguidas isolam a Binance e o stream segue para a Coinbase")
                .containsExactly(BINANCE, BINANCE, BINANCE, COINBASE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.COINBASE);
        assertThat(manager.status().detail()).contains("Binance: isolada: 3 falhas seguidas");
    }

    @Test void silentConnectionIsDroppedAndCountsAsAFailure() {
        tick(0);
        FakeSocket first = net.sockets.get(BINANCE);
        net.send(BINANCE, "{\"stream\":\"btcusdt@miniTicker\",\"data\":{\"e\":\"24hrMiniTicker\",\"E\":1,\"s\":\"BTCUSDT\",\"c\":\"1\"}}");
        tick(15_000);
        assertThat(manager.activeVenue()).as("15 s de silêncio ainda é tolerado").isEqualTo(CryptoVenue.BINANCE);

        tick(6_000);
        assertThat(first.closed).isTrue();
        assertThat(manager.activeVenue()).isNull();
        assertThat(manager.stalls()).isEqualTo(1);

        tick(1_100);
        assertThat(net.attempts).containsExactly(BINANCE, BINANCE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.BINANCE);
        assertThat(net.sockets.get(BINANCE)).isNotSameAs(first);
    }

    @Test void serverCloseTriggersReconnectWithBackoff() {
        tick(0);
        net.listeners.get(BINANCE).onClose(net.sockets.get(BINANCE), 1006, "");
        assertThat(manager.activeVenue()).isNull();
        tick(500);
        assertThat(net.attempts).hasSize(1);
        tick(600);
        assertThat(net.attempts).containsExactly(BINANCE, BINANCE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.BINANCE);
    }

    @Test void whenBinanceComesBackTheNewConnectionOpensBeforeTheFallbackIsClosed() {
        net.behavior.put(BINANCE, 451);
        tick(0);
        tick(0);
        FakeSocket coinbase = net.sockets.get(COINBASE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.COINBASE);

        net.behavior.put(BINANCE, "ok");
        advanceWithTraffic(29 * MIN, COINBASE);
        assertThat(net.attempts).as("ainda isolada por 30 min").containsExactly(BINANCE, COINBASE);
        assertThat(coinbase.closed).isFalse();
        quotes.clear();

        advanceWithTraffic(2 * MIN, COINBASE);
        assertThat(net.attempts).containsExactly(BINANCE, COINBASE, BINANCE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.BINANCE);
        assertThat(coinbase.closed).as("a antiga fecha só depois de a nova abrir").isTrue();
        assertThat(manager.status().shortCircuited()).as("duas trocas de bolsa").isEqualTo(1);

        int before = quotes.size();
        net.send(COINBASE, coinbaseTicker("BTC-USD", 1, "2026-11-21T01:44:08Z"));
        assertThat(quotes).as("mensagens tardias da conexão antiga são descartadas").hasSize(before);
    }

    @Test void aFailedProbeKeepsTheFallbackConnectedAndIsolatesBinanceAgain() {
        net.behavior.put(BINANCE, 451);
        tick(0);
        tick(0);
        advanceWithTraffic(31 * MIN, COINBASE);
        assertThat(net.attempts).containsExactly(BINANCE, COINBASE, BINANCE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.COINBASE);
        assertThat(net.sockets.get(COINBASE).closed).isFalse();

        advanceWithTraffic(10 * MIN, COINBASE);
        assertThat(net.attempts).as("isolada de novo por mais 30 min").hasSize(3);
        assertThat(manager.status().rateLimited()).isEqualTo(2);
    }

    @Test void providerErrorMessageDropsTheConnectionAndCountsAsAFailure() {
        tick(0);
        net.send(BINANCE, "{\"code\":2,\"msg\":\"Invalid request\"}");
        assertThat(manager.activeVenue()).isNull();
        assertThat(manager.status().failures()).isEqualTo(1);
    }

    @Test void aVenueThatOpensAndDiesAtOnceIsBackedOffThenIsolatedInsteadOfHammeredEverySecond() {
        net.behavior.put(BINANCE, "flap");
        tick(0);
        tick(1_100);
        tick(2_100);
        assertThat(net.attempts).containsExactly(BINANCE, BINANCE, BINANCE);

        tick(0);
        assertThat(net.attempts).as("na terceira falha seguida a bolsa é isolada e o stream segue para a próxima")
                .containsExactly(BINANCE, BINANCE, BINANCE, COINBASE);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.COINBASE);

        advanceWithTraffic(2 * MIN, COINBASE);
        assertThat(net.attempts).as("sem reconexões a cada segundo").hasSize(4);
        assertThat(manager.status().detail()).contains("Binance: isolada: 3 falhas seguidas");
    }

    @Test void providerErrorClosesTheSocketInsteadOfLeavingItOpen() {
        net.behavior.put(BINANCE, "subscription-error");
        tick(0);
        FakeSocket first = net.sockets.get(BINANCE);
        assertThat(manager.activeVenue()).isNull();
        assertThat(first.closed).as("o socket da conexão derrubada precisa ser fechado").isTrue();
        assertThat(manager.status().failures()).isEqualTo(1);
    }

    @Test void repeatedProviderErrorsNeverAccumulateOpenSockets() {
        net.behavior.put(BINANCE, "subscription-error");
        for (int i = 0; i < 12; i++) tick(i < 3 ? 2_100 : 0);
        assertThat(net.attempts.stream().filter(h -> h.equals(BINANCE)).count()).isEqualTo(3);
        assertThat(net.openSockets()).as("no máximo a conexão ativa fica aberta").isLessThanOrEqualTo(1);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.COINBASE);
    }

    @Test void theFirstValidPriceProvesTheConnectionAndResetsTheBackoff() {
        net.behavior.put(BINANCE, "net");
        tick(0);
        tick(1_100);
        assertThat(net.attempts).hasSize(2);
        net.behavior.put(BINANCE, "ok");
        tick(2_100);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.BINANCE);

        net.send(BINANCE, "{\"stream\":\"btcusdt@miniTicker\",\"data\":{\"e\":\"24hrMiniTicker\",\"E\":1795225436018,\"s\":\"BTCUSDT\",\"c\":\"59008.11\"}}");
        net.listeners.get(BINANCE).onClose(net.sockets.get(BINANCE), 1006, "");
        tick(1_100);
        assertThat(net.attempts).as("a conexão provou que funciona: o backoff recomeça em 1 s, sem isolamento")
                .hasSize(4);
        assertThat(manager.activeVenue()).isEqualTo(CryptoVenue.BINANCE);
    }

    @Test void disabledManagerNeverConnects() {
        CryptoStreamManager off = new CryptoStreamManager(quotes::add, List.of(CryptoVenue.values()), net, now::get, false);
        off.tick();
        assertThat(net.attempts).isEmpty();
        assertThat(off.status().circuit()).isEqualTo("DESLIGADO");
        assertThat(off.status().detail()).contains("MW_CRYPTO_STREAM=false");
    }

    @Test void handshakeStatusIsFoundAnywhereInTheCauseChain() {
        HttpResponse<?> resp = (HttpResponse<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {HttpResponse.class}, (p, m, a) -> m.getName().equals("statusCode") ? 451 : null);
        Throwable wrapped = new CompletionException(new RuntimeException("x", new WebSocketHandshakeException(resp)));
        assertThat(CryptoStreamManager.statusOf(wrapped)).isEqualTo(451);
        assertThat(CryptoStreamManager.statusOf(new java.net.ConnectException("x"))).isZero();
    }
}
