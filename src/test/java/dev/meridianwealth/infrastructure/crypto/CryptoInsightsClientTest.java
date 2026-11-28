package dev.meridianwealth.infrastructure.crypto;

import static dev.meridianwealth.testsupport.FixtureHttpGateway.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.CryptoInsightsPort;
import dev.meridianwealth.domain.crypto.FearGreed;
import dev.meridianwealth.domain.crypto.HourlyVolume.Bar;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Parsers contra respostas reais gravadas em 2026-11-28 (Coinbase, Kraken, Binance e alternative.me) e a ordem de tentativa entre as bolsas. */
class CryptoInsightsClientTest {
    @Test void coinbaseRealCandlesBecomeHourlyBarsWithVolumeInDollars() throws IOException {
        List<Bar> bars = CryptoInsightsClient.parseCoinbase(resource("/fixtures/crypto/coinbase-candles-1h.json"));
        assertThat(bars).hasSize(350);
        assertThat(bars).allSatisfy(b -> {
            assertThat(b.volumeUsd()).isPositive();
            assertThat(b.timeMillis() % 3_600_000L).as("barras começam na hora cheia").isZero();
        });
        assertThat(bars.get(0).volumeUsd()).as("152,57 BTC a cerca de 59,8 mil dólares").isBetween(8_000_000.0, 11_000_000.0);
    }

    @Test void krakenRealOhlcBecomesHourlyBarsWithVolumeInDollars() throws IOException {
        List<Bar> bars = CryptoInsightsClient.parseKraken(resource("/fixtures/crypto/kraken-ohlc-1h.json"));
        assertThat(bars).hasSize(721);
        assertThat(bars).allSatisfy(b -> assertThat(b.volumeUsd()).isPositive());
        assertThat(bars.get(0).timeMillis()).isEqualTo(1793253600L * 1000);
        assertThat(bars.get(0).volumeUsd()).as("17,83 BTC a cerca de 80 mil dólares").isBetween(1_300_000.0, 1_500_000.0);
    }

    @Test void binanceRealKlinesUseTheQuoteVolumeAlreadyInDollars() throws IOException {
        List<Bar> bars = CryptoInsightsClient.parseBinance(resource("/fixtures/crypto/binance-klines-1h.json"));
        assertThat(bars).hasSize(168);
        assertThat(bars).allSatisfy(b -> assertThat(b.volumeUsd()).isPositive());
        assertThat(bars.get(0).timeMillis() % 3_600_000L).isZero();
    }

    @Test void fearGreedRealResponseIsParsedAndAnOutOfRangeValueIsDropped() throws IOException {
        FearGreed f = CryptoInsightsClient.parseFearGreed(resource("/fixtures/crypto/fear-greed.json"));
        assertThat(f.value()).isEqualTo(10);
        assertThat(f.label()).isEqualTo("Medo extremo");
        assertThat(f.updatedAt().getEpochSecond()).isEqualTo(1795824000L);
        assertThat(CryptoInsightsClient.parseFearGreed("{\"data\":[{\"value\":\"250\",\"value_classification\":\"Greed\",\"timestamp\":\"1\"}]}")).isNull();
        assertThatThrownBy(() -> CryptoInsightsClient.parseFearGreed("{\"data\":[]}")).isInstanceOf(IOException.class);
    }

    @Test void unexpectedBodiesAreErrorsNotEmptyData() {
        assertThatThrownBy(() -> CryptoInsightsClient.parseCoinbase("{\"message\":\"NotFound\"}")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CryptoInsightsClient.parseKraken("{\"error\":[\"EQuery:Unknown asset pair\"],\"result\":{}}")).isInstanceOf(IOException.class).hasMessageContaining("Kraken");
        assertThatThrownBy(() -> CryptoInsightsClient.parseBinance("{\"code\":-1121}")).isInstanceOf(IOException.class);
    }

    private static HttpGateway only(Map<String, String> byPrefix) {
        return (url, headers) -> {
            for (var e : byPrefix.entrySet()) if (url.startsWith(e.getKey())) return e.getValue();
            throw new IOException("HTTP 451");
        };
    }

    @Test void theFirstVenueThatAnswersWinsAndABlockedOneFallsThrough() throws IOException {
        String kraken = resource("/fixtures/crypto/kraken-ohlc-1h.json"), binance = resource("/fixtures/crypto/binance-klines-1h.json");
        CryptoInsightsPort.Hourly h = new CryptoInsightsClient(only(Map.of("https://api.kraken.com", kraken, "https://api.binance.com", binance))).hourly("BTC").orElseThrow();
        assertThat(h.source()).as("a Coinbase foi barrada: segue a Kraken").isEqualTo("Kraken");
        String coinbase = resource("/fixtures/crypto/coinbase-candles-1h.json");
        assertThat(new CryptoInsightsClient(only(Map.of("https://api.exchange.coinbase.com", coinbase, "https://api.kraken.com", kraken))).hourly("BTC").orElseThrow().source()).isEqualTo("Coinbase");
        assertThat(new CryptoInsightsClient(only(Map.of("https://api.binance.com", binance))).hourly("BTC").orElseThrow().source()).isEqualTo("Binance");
        assertThat(new CryptoInsightsClient(only(Map.of())).hourly("BTC")).as("ninguém respondeu").isEmpty();
        assertThat(new CryptoInsightsClient(only(Map.of("https://api.exchange.coinbase.com", coinbase))).hourly("ETH")).as("só o Bitcoin é medido").isEmpty();
        assertThat(new CryptoInsightsClient(only(Map.of())).fearGreed()).isEmpty();
    }
}
