package dev.meridianwealth.infrastructure.crypto;

import static dev.meridianwealth.testsupport.FixtureHttpGateway.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsers das três bolsas contra mensagens reais gravadas em 2026-11-21 (dívida 7, estendida à cripto). */
class CryptoVenueTest {
    private static List<CryptoTick> parseAll(CryptoVenue v, String fixture) throws IOException {
        List<CryptoTick> out = new ArrayList<>();
        for (String line : resource("/fixtures/crypto/" + fixture).split("\\R")) {
            if (!line.isBlank()) out.addAll(v.parse(line));
        }
        return out;
    }

    @Test void binanceMiniTickerRealMessagesYieldOnePricePerPairWithEventTime() throws IOException {
        List<CryptoTick> ticks = parseAll(CryptoVenue.BINANCE, "binance-miniticker.jsonl");
        assertThat(ticks).hasSize(12);
        assertThat(ticks.get(0)).isEqualTo(new CryptoTick("BTCUSDT", 59008.11, 1795225436018L));
        assertThat(ticks).extracting(CryptoTick::pair).containsOnly("BTCUSDT", "ETHUSDT");
        assertThat(ticks).allSatisfy(t -> assertThat(t.price()).isPositive());
    }

    @Test void coinbaseTickerRealMessagesCarryPriceAndIsoTime() throws IOException {
        List<CryptoTick> ticks = parseAll(CryptoVenue.COINBASE, "coinbase-ticker.jsonl");
        assertThat(ticks).isNotEmpty();
        CryptoTick first = ticks.get(0);
        assertThat(first.pair()).isEqualTo("BTC-USD");
        assertThat(first.price()).isEqualTo(58877.42);
        assertThat(first.timeMillis()).isEqualTo(Instant.parse("2026-11-21T01:44:08.734550Z").toEpochMilli());
        assertThat(CryptoVenue.COINBASE.parse("{\"type\":\"subscriptions\",\"channels\":[]}"))
                .as("a confirmação de assinatura não é preço").isEmpty();
    }

    @Test void krakenTickerRealMessagesIgnoreStatusAndSubscriptionAcks() throws IOException {
        List<CryptoTick> ticks = parseAll(CryptoVenue.KRAKEN, "kraken-ticker.jsonl");
        assertThat(ticks).isNotEmpty();
        assertThat(ticks).extracting(CryptoTick::pair).containsOnly("BTC/USD", "ETH/USD");
        CryptoTick eth = ticks.stream().filter(t -> t.pair().equals("ETH/USD") && t.price() == 1620.03).findFirst().orElseThrow();
        assertThat(eth.timeMillis()).isEqualTo(Instant.parse("2026-11-21T01:44:22.698782Z").toEpochMilli());
        assertThat(CryptoVenue.KRAKEN.parse("{\"channel\":\"heartbeat\"}")).isEmpty();
    }

    @Test void exchangeErrorsSurfaceAsExceptions() {
        assertThatThrownBy(() -> CryptoVenue.BINANCE.parse("{\"code\":2,\"msg\":\"Invalid request\"}")).hasMessageContaining("Invalid request");
        assertThatThrownBy(() -> CryptoVenue.COINBASE.parse("{\"type\":\"error\",\"message\":\"Failed to subscribe\",\"reason\":\"BTC-XXX is not a valid product\"}"))
                .hasMessageContaining("not a valid product");
        assertThatThrownBy(() -> CryptoVenue.KRAKEN.parse("{\"method\":\"subscribe\",\"success\":false,\"error\":\"Currency pair not supported\"}"))
                .hasMessageContaining("Currency pair not supported");
    }

    @Test void garbagePricesAndTimesAreDropped() throws IOException {
        assertThat(CryptoVenue.COINBASE.parse("{\"type\":\"ticker\",\"product_id\":\"BTC-USD\",\"price\":\"abc\",\"time\":\"2026-11-21T01:44:08Z\"}")).isEmpty();
        assertThat(CryptoVenue.COINBASE.parse("{\"type\":\"ticker\",\"product_id\":\"BTC-USD\",\"price\":\"-5\",\"time\":\"2026-11-21T01:44:08Z\"}")).isEmpty();
        assertThat(CryptoVenue.COINBASE.parse("{\"type\":\"ticker\",\"product_id\":\"BTC-USD\",\"price\":\"100\",\"time\":\"ontem\"}")).isEmpty();
        assertThat(CryptoVenue.BINANCE.parse("[1,2,3]")).isEmpty();
    }

    @Test void subscriptionsAndUrlsFollowEachExchangeProtocol() {
        List<String> pairs = List.of("BTCUSDT", "ETHUSDT");
        assertThat(CryptoVenue.BINANCE.uri(pairs).toString())
                .isEqualTo("wss://stream.binance.com:9443/stream?streams=btcusdt@miniTicker/ethusdt@miniTicker");
        assertThat(CryptoVenue.BINANCE.subscribe(pairs)).as("a Binance assina pela própria URL").isEmpty();
        assertThat(CryptoVenue.COINBASE.subscribe(List.of("BTC-USD")))
                .containsExactly("{\"type\":\"subscribe\",\"product_ids\":[\"BTC-USD\"],\"channels\":[\"ticker\"]}");
        assertThat(CryptoVenue.KRAKEN.subscribe(List.of("BTC/USD")))
                .containsExactly("{\"method\":\"subscribe\",\"params\":{\"channel\":\"ticker\",\"symbol\":[\"BTC/USD\"]}}");
        assertThat(CryptoVenue.KRAKEN.uri(pairs).toString()).isEqualTo("wss://ws.kraken.com/v2");
    }
}
