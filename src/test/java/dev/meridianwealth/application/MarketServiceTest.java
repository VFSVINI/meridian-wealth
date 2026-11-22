package dev.meridianwealth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.BacktestCache;
import dev.meridianwealth.application.port.MarketDataPort;
import dev.meridianwealth.application.port.SignalStore;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Exchange;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.testsupport.SyntheticCandles;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketServiceTest {
    private static final List<Instrument> UNIVERSE = List.of(
            Instrument.of("SPY", "S&P 500 ETF", Exchange.NYSE, "ETF", "amplo", "USD", null),
            Instrument.of("AAPL", "Apple", Exchange.NYSE, "Ação", "tecnologia", "USD", "SPY"),
            Instrument.of("MSFT", "Microsoft", Exchange.NYSE, "Ação", "tecnologia", "USD", "SPY"));

    /** Porta de dados em memória: devolve exatamente o que o teste configurar, nada além. */
    static final class InMemoryMarketData implements MarketDataPort {
        final Map<String, HistoryResult> histories = new HashMap<>();
        final Map<String, LiveUpdate> updates = new HashMap<>();

        @Override public List<Instrument> universe() { return UNIVERSE; }

        @Override public HistoryResult history(String symbol, int bars) {
            return histories.getOrDefault(symbol, HistoryResult.empty());
        }

        /** Atraso artificial de {@code next()}, para simular uma atualização lenta. */
        volatile long nextDelayMillis;

        @Override public Optional<LiveUpdate> next(String symbol) {
            if (nextDelayMillis > 0) {
                try {
                    Thread.sleep(nextDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return Optional.ofNullable(updates.get(symbol));
        }

        @Override public boolean marketOpen(String symbol) { return true; }

        @Override public MacroSnapshot macro() { return MacroSnapshot.unavailable(); }
    }

    static final class CountingCache implements BacktestCache {
        final Map<String, BacktestService.Result> map = new HashMap<>();
        final AtomicInteger puts = new AtomicInteger();

        @Override public Optional<BacktestService.Result> get(String key) { return Optional.ofNullable(map.get(key)); }

        @Override public void put(String key, BacktestService.Result result, Duration ttl) {
            puts.incrementAndGet();
            map.put(key, result);
        }
    }

    private final InMemoryMarketData data = new InMemoryMarketData();
    private final CountingCache cache = new CountingCache();
    private final List<SignalRecord> saved = new ArrayList<>();
    private MarketService service;

    @BeforeEach
    void setUp() {
        SignalStore store = new SignalStore() {
            @Override public void save(SignalRecord record) { saved.add(record); }

            @Override public List<SignalRecord> recent(String symbol, int limit) { return List.copyOf(saved); }
        };
        service = new MarketService(data, store, cache, symbol -> Optional.empty(), new BacktestService());
    }

    private static HistoryResult real(long seed, String source, Freshness freshness) {
        List<Candle> c = SyntheticCandles.series(seed, 300, 100, 0.25);
        return new HistoryResult(c, source, c.get(c.size() - 1).time(), freshness);
    }

    private static TickBatch.Tick tick(TickBatch b, String symbol) {
        return b.ticks().stream().filter(t -> t.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    @Test void everythingStartsWithoutDataAndNothingIsInvented() {
        TickBatch b = service.overview();
        assertThat(b.mode()).isEqualTo("SEM_DADOS");
        assertThat(b.ticks()).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(DataStatus.UNAVAILABLE);
            assertThat(t.candle()).isNull();
            assertThat(t.assessment()).isNull();
        });
        assertThatThrownBy(() -> service.assessment("SPY"))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("Sem dado em tempo real");
        assertThat(service.candles("SPY", 100)).isEmpty();
    }

    @Test void mixedAvailabilityIsReportedPerInstrument() {
        data.histories.put("SPY", real(1, "Yahoo", Freshness.DELAYED));
        data.histories.put("AAPL", real(2, "Banco", Freshness.STORED));
        service.loadHistories();

        TickBatch b = service.overview();
        assertThat(b.mode()).isEqualTo("PARCIAL");
        assertThat(tick(b, "SPY").status()).isEqualTo(DataStatus.LIVE);
        assertThat(tick(b, "SPY").assessment()).isNotNull();
        assertThat(tick(b, "AAPL").status()).isEqualTo(DataStatus.STORED);
        assertThat(tick(b, "AAPL").freshness()).isEqualTo(Freshness.STORED);
        assertThat(tick(b, "MSFT").status()).isEqualTo(DataStatus.UNAVAILABLE);
    }

    @Test void readsNeverWaitForASlowUpdate() throws Exception {
        data.histories.put("SPY", real(1, "Yahoo", Freshness.DELAYED));
        data.histories.put("AAPL", real(2, "Yahoo", Freshness.DELAYED));
        service.loadHistories();
        data.nextDelayMillis = 150; // duas séries com barras: a atualização leva cerca de 300 ms
        long[] advanceMillis = new long[1];
        Thread writer = new Thread(() -> {
            long t0 = System.nanoTime();
            service.advance();
            advanceMillis[0] = (System.nanoTime() - t0) / 1_000_000;
        });
        writer.start();
        Thread.sleep(40);
        long worst = 0;
        int reads = 0;
        while (writer.isAlive()) {
            long t0 = System.nanoTime();
            service.overview();
            service.candles("SPY", 50);
            service.assessment("SPY");
            service.micro("AAPL");
            service.sources();
            service.macro();
            worst = Math.max(worst, System.nanoTime() - t0);
            reads++;
        }
        writer.join();
        assertThat(advanceMillis[0]).as("a atualização realmente foi lenta").isGreaterThanOrEqualTo(250);
        assertThat(reads).as("houve leituras durante a atualização").isGreaterThan(5);
        assertThat(worst / 1_000_000).as("nenhuma leitura esperou a atualização lenta (antes: o monitor único as segurava por ~300 ms)").isLessThan(100);
    }

    @Test void aPublishedSnapshotNeverChangesUnderTheReader() {
        data.histories.put("SPY", real(1, "Yahoo", Freshness.DELAYED));
        service.loadHistories();
        List<Candle> before = service.candles("SPY", 1000);
        Candle last = data.histories.get("SPY").candles().get(299);
        Candle next = new Candle(last.time() + 86_400_000L, last.close(), last.close() + 2, last.close() - 1, last.close() + 1, last.volume());
        data.updates.put("SPY", new LiveUpdate(next, true, new QuoteMeta("Finnhub", next.time(), Freshness.REAL_TIME, next.time() + 30_000L), true));
        service.advance();
        assertThat(before).as("a lista já entregue não mudou").hasSize(300);
        assertThat(service.candles("SPY", 1000)).hasSize(301);
        assertThat(service.overview().ticks().get(0).candle().time()).isEqualTo(next.time());
    }

    @Test void tooFewRealBarsAreInsufficientNotPadded() {
        List<Candle> few = SyntheticCandles.series(3, 50, 100, 0.2);
        data.histories.put("SPY", new HistoryResult(few, "Yahoo", few.get(49).time(), Freshness.DELAYED));
        service.loadHistories();
        TickBatch.Tick t = tick(service.overview(), "SPY");
        assertThat(t.status()).isEqualTo(DataStatus.INSUFFICIENT);
        assertThat(t.candle()).isNotNull();
        assertThat(t.assessment()).isNull();
        assertThatThrownBy(() -> service.assessment("SPY")).hasMessageContaining("insuficientes");
    }

    @Test void liveUpdateCarriesProvenanceIntoTheTick() {
        data.histories.put("SPY", real(1, "Yahoo", Freshness.DELAYED));
        service.loadHistories();
        Candle last = data.histories.get("SPY").candles().get(299);
        Candle moved = new Candle(last.time(), last.open(), last.high() + 1, last.low(), last.close() + 1, last.volume());
        data.updates.put("SPY", new LiveUpdate(moved, false,
                new QuoteMeta("Finnhub", 1_795_000_000_000L, Freshness.REAL_TIME, 1_795_000_030_000L), true));

        TickBatch.Tick t = tick(service.advance(), "SPY");
        assertThat(t.source()).isEqualTo("Finnhub");
        assertThat(t.freshness()).isEqualTo(Freshness.REAL_TIME);
        assertThat(t.quoteTime()).isEqualTo(1_795_000_000_000L);
        assertThat(t.nextUpdateAt()).isEqualTo(1_795_000_030_000L);
        assertThat(t.candle().close()).isEqualTo(last.close() + 1);
    }

    @Test void retryRecoversInstrumentsThatHadNoData() {
        service.loadHistories();
        assertThat(tick(service.overview(), "MSFT").status()).isEqualTo(DataStatus.UNAVAILABLE);
        data.histories.put("MSFT", real(5, "Stooq", Freshness.DELAYED));
        service.retryMissing();
        assertThat(tick(service.overview(), "MSFT").status()).isEqualTo(DataStatus.LIVE);
    }

    @Test void signalChangesArePersistedOncePerChange() {
        data.histories.put("SPY", real(1, "Yahoo", Freshness.DELAYED));
        service.loadHistories();
        service.advance();
        service.advance();
        service.advance();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).symbol()).isEqualTo("SPY");
    }

    @Test void backtestIsCachedByDataVersionNotByClock() {
        data.histories.put("SPY", real(1, "Yahoo", Freshness.DELAYED));
        service.loadHistories();
        BacktestService.Result first = service.backtest("SPY", 35, 30, 250);
        BacktestService.Result second = service.backtest("SPY", 35, 30, 250);
        assertThat(second).isSameAs(first);
        assertThat(cache.puts).hasValue(1);

        Candle last = data.histories.get("SPY").candles().get(299);
        data.updates.put("SPY", new LiveUpdate(
                new Candle(last.time(), last.open(), last.high(), last.low(), last.close() * 1.01, last.volume()), false,
                new QuoteMeta("Yahoo", last.time() + 60_000, Freshness.DELAYED, 0), true));
        service.advance();
        service.backtest("SPY", 35, 30, 250);
        assertThat(cache.puts).hasValue(2);
    }

    @Test void backtestOnInstrumentWithoutDataIsRefused() {
        assertThatThrownBy(() -> service.backtest("MSFT", 35, 30, 250)).isInstanceOf(DataUnavailableException.class);
    }
}
