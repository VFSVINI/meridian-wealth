package dev.meridianwealth.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Freshness;
import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Finnhub WS e feed pago sem rede. As mensagens seguem o formato da documentação pública da Finnhub
 * (https://finnhub.io/docs/api/websocket-trades): não há fixture gravada porque o projeto não tem
 * chave da Finnhub; a validação ao vivo fica registrada como pendente em docs/mercados.md.
 */
class StreamingAndPaidFeedTest {
    /** WebSocket mínimo que só registra o que foi enviado. */
    static final class RecordingSocket implements WebSocket {
        final List<String> sent = new ArrayList<>();

        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }

        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }

        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }

        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) { return CompletableFuture.completedFuture(this); }

        @Override public void request(long n) { }

        @Override public String getSubprotocol() { return ""; }

        @Override public boolean isOutputClosed() { return false; }

        @Override public boolean isInputClosed() { return false; }

        @Override public void abort() { }
    }

    @Test void parsesTradesAndIgnoresPings() throws IOException {
        String doc = "{\"data\":[{\"p\":7296.89,\"s\":\"BINANCE:BTCUSDT\",\"t\":1575526691134,\"v\":0.011467}],\"type\":\"trade\"}";
        assertThat(FinnhubTradeStream.parseTrades(doc))
                .containsExactly(new FinnhubTradeStream.Trade("BINANCE:BTCUSDT", 7296.89, 1575526691134L, 0.011467));
        assertThat(FinnhubTradeStream.parseTrades("{\"type\":\"ping\"}")).isEmpty();
        assertThatThrownBy(() -> FinnhubTradeStream.parseTrades("{\"type\":\"error\",\"msg\":\"Invalid API key\"}"))
                .hasMessageContaining("Invalid API key");
    }

    @Test void subscribesUsStocksAndDeliversTheLatestTradePerSymbol() {
        List<Quote> delivered = new ArrayList<>();
        FinnhubTradeStream stream = new FinnhubTradeStream("chave-de-teste", true, delivered::add);
        RecordingSocket ws = new RecordingSocket();
        WebSocket.Listener l = stream.listener();

        l.onOpen(ws);
        assertThat(stream.connected()).isTrue();
        assertThat(ws.sent).contains("{\"type\":\"subscribe\",\"symbol\":\"AAPL\"}", "{\"type\":\"subscribe\",\"symbol\":\"SPY\"}");
        assertThat(ws.sent).noneMatch(m -> m.contains("PETR4"));

        // Mensagem fragmentada em dois frames, com dois negócios de AAPL e um de símbolo fora do universo.
        l.onText(ws, "{\"type\":\"trade\",\"data\":[{\"s\":\"AAPL\",\"p\":230.10,\"t\":1000,\"v\":5},", false);
        l.onText(ws, "{\"s\":\"AAPL\",\"p\":230.25,\"t\":2000,\"v\":1},{\"s\":\"ZZZZ\",\"p\":1,\"t\":3000,\"v\":1}]}", true);

        assertThat(delivered).containsExactly(new Quote("AAPL", 230.25, 2000, "Finnhub WS"));
        assertThat(stream.status().successes()).isEqualTo(1);
        stream.stop();
    }

    @Test void withoutKeyTheStreamStaysOffAndSaysSo() {
        FinnhubTradeStream off = new FinnhubTradeStream("", true, q -> { });
        assertThat(off.enabled()).isFalse();
        assertThat(off.status().circuit()).isEqualTo("DESLIGADO");
        off.start();
        assertThat(off.connected()).isFalse();
        off.stop();
    }

    @Test void paidFeedIsOffByDefaultAndDeclaresItsSeal() throws IOException {
        TwelveDataFeed off = new TwelveDataFeed((u, h) -> "{}", "none", "", "DELAYED");
        assertThat(off.enabled()).isFalse();
        assertThat(off.status().enabled()).isFalse();
        assertThatThrownBy(() -> off.quote("AAPL", Symbols.us("AAPL", null))).isInstanceOf(IllegalStateException.class);

        TwelveDataFeed on = new TwelveDataFeed((u, h) -> "{\"symbol\":\"AAPL\",\"close\":\"230.5\",\"timestamp\":1795000000}",
                "twelvedata", "k", "REAL_TIME");
        assertThat(on.enabled()).isTrue();
        assertThat(on.freshness()).isEqualTo(Freshness.REAL_TIME);
        assertThat(on.covers(Symbols.b3("PETR4"))).isFalse();
        Quote q = on.quote("AAPL", Symbols.us("AAPL", null));
        assertThat(q.price()).isEqualTo(230.5);
        assertThat(q.tsMillis()).isEqualTo(1_795_000_000_000L);
        assertThatThrownBy(() -> TwelveDataFeed.parseQuote("AAPL", "{\"status\":\"error\",\"message\":\"plano não cobre\"}"))
                .hasMessageContaining("plano não cobre");
    }
}
