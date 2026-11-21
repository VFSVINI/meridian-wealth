package dev.chronosquant.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chronosquant.application.port.CacheStore;
import dev.chronosquant.application.port.FundamentalsPort;
import dev.chronosquant.application.port.MarketDataPort;
import dev.chronosquant.application.port.SignalStore;
import dev.chronosquant.domain.Assessment;
import dev.chronosquant.domain.Candle;
import dev.chronosquant.domain.Instrument;
import dev.chronosquant.domain.MacroSnapshot;
import dev.chronosquant.domain.Signal;
import dev.chronosquant.domain.TimingEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketService {
    private static final Logger log = LoggerFactory.getLogger(MarketService.class);
    private static final int HISTORY_BARS = 500;
    private static final int MAX_BARS = 1500;

    private static final class Series {
        final Instrument instrument;
        final List<Candle> candles = new ArrayList<>();
        String source = "SIMULADO";
        boolean simulated = true;
        boolean open;
        Signal lastSignal;

        Series(Instrument instrument) { this.instrument = instrument; }
    }

    public record SourceView(String symbol, String source, boolean simulated, boolean marketOpen, int bars) {}

    private final MarketDataPort data;
    private final SignalStore signals;
    private final CacheStore cache;
    private final ObjectMapper mapper;
    private final StreamSettings settings;
    private final FundamentalsPort fundamentals;
    private final BacktestService backtests;
    private final TimingEngine engine = new TimingEngine();
    private final Map<String, Series> series = new LinkedHashMap<>();
    private MacroSnapshot macro;

    public MarketService(MarketDataPort data, SignalStore signals, CacheStore cache, ObjectMapper mapper,
                         StreamSettings settings, FundamentalsPort fundamentals, BacktestService backtests) {
        this.data = data;
        this.signals = signals;
        this.cache = cache;
        this.mapper = mapper;
        this.settings = settings;
        this.fundamentals = fundamentals;
        this.backtests = backtests;
        loadHistories();
        this.macro = data.nextMacro();
    }

    private void loadHistories() {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<Instrument, Future<HistoryResult>> jobs = new LinkedHashMap<>();
            for (Instrument i : data.universe()) jobs.put(i, pool.submit(() -> data.history(i.symbol(), HISTORY_BARS)));
            for (var e : jobs.entrySet()) {
                Series s = new Series(e.getKey());
                HistoryResult h = e.getValue().get();
                s.candles.addAll(h.candles());
                s.source = h.source();
                s.simulated = h.simulated();
                series.put(e.getKey().symbol(), s);
                log.info("{}: {} barras via {}", e.getKey().symbol(), h.candles().size(), h.source());
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Falha ao carregar históricos", e);
        }
    }

    public List<Instrument> universe() { return data.universe(); }

    /** Avança o relógio: nova barra (simulado) ou atualização da barra viva (dados reais). */
    public synchronized TickBatch advance() {
        macro = data.nextMacro();
        List<TickBatch.Tick> ticks = new ArrayList<>();
        for (var e : series.entrySet()) {
            Series s = e.getValue();
            LiveUpdate u = data.next(e.getKey());
            if (u.newBar()) {
                s.candles.add(u.candle());
                if (s.candles.size() > MAX_BARS) s.candles.remove(0);
            } else {
                s.candles.set(s.candles.size() - 1, u.candle());
            }
            s.source = u.source();
            s.simulated = u.simulated();
            s.open = u.marketOpen();
            TickBatch.Tick t = tick(e.getKey(), s);
            recordSignal(s, t.assessment());
            ticks.add(t);
        }
        return batch(ticks);
    }

    public synchronized TickBatch overview() {
        List<TickBatch.Tick> ticks = new ArrayList<>();
        series.forEach((sym, s) -> ticks.add(tick(sym, s)));
        return batch(ticks);
    }

    private TickBatch batch(List<TickBatch.Tick> ticks) {
        long sims = ticks.stream().filter(TickBatch.Tick::simulated).count();
        String mode = sims == 0 ? "REAL" : sims == ticks.size() ? "SIMULADO" : "MISTO";
        return new TickBatch(System.currentTimeMillis(), mode, settings.flow(), macro, ticks);
    }

    private TickBatch.Tick tick(String symbol, Series s) {
        Candle last = s.candles.get(s.candles.size() - 1);
        return new TickBatch.Tick(symbol, last, assess(symbol, s), false, s.source, s.simulated, s.open);
    }

    private Assessment assess(String symbol, Series s) {
        return engine.assess(symbol, s.candles, macro.riskFor(s.instrument.sector()), benchmark(s));
    }

    private double[] benchmark(Series s) {
        String bench = "Europa".equals(s.instrument.region()) ? "STOXX50" : "SPY";
        Series b = series.get(bench);
        if (b == null || b == s) return null;
        int from = Math.max(0, b.candles.size() - 500);
        return b.candles.subList(from, b.candles.size()).stream().mapToDouble(Candle::close).toArray();
    }

    private void recordSignal(Series s, Assessment a) {
        if (a.signal() == s.lastSignal) return;
        s.lastSignal = a.signal();
        try {
            signals.save(new SignalRecord(a.symbol(), System.currentTimeMillis(), a.score(), a.signal().name(),
                    a.phase().name(), a.regime().name(), a.confidence(), a.risk().name(), a.price()));
        } catch (RuntimeException e) {
            log.warn("Banco: gravação do sinal de {} falhou ({})", a.symbol(), e.getMessage());
        }
    }

    public synchronized List<Candle> candles(String symbol, int limit) {
        List<Candle> h = require(symbol).candles;
        return new ArrayList<>(h.subList(Math.max(0, h.size() - limit), h.size()));
    }

    public synchronized Assessment assessment(String symbol) { return assess(symbol, require(symbol)); }

    public synchronized MicroView micro(String symbol) {
        Series s = require(symbol);
        Assessment a = assess(symbol, s);
        String bench = "Europa".equals(s.instrument.region()) ? "STOXX50" : "SPY";
        return new MicroView(symbol, s.instrument.sector(), bench.equals(symbol) ? "—" : bench, a.relStrength(),
                a.relVolume(), fundamentals.fundamentals(symbol).orElse(null));
    }

    public List<SignalRecord> signals(String symbol, int limit) {
        require(symbol);
        return signals.recent(symbol, limit);
    }

    public synchronized List<SourceView> sources() {
        return series.entrySet().stream()
                .map(e -> new SourceView(e.getKey(), e.getValue().source, e.getValue().simulated,
                        e.getValue().open, e.getValue().candles.size()))
                .toList();
    }

    public synchronized BacktestService.Result backtest(String symbol, int buyScore, int sellScore, int bars) {
        Series s = require(symbol);
        String key = "bt:" + symbol + ":" + buyScore + ":" + sellScore + ":" + bars + ":" + s.candles.size()
                + ":" + System.currentTimeMillis() / 30_000;
        Optional<String> hit = cache.get(key);
        if (hit.isPresent()) {
            try {
                return mapper.readValue(hit.get(), BacktestService.Result.class);
            } catch (Exception e) {
                log.debug("Cache de backtest inválido: {}", e.getMessage());
            }
        }
        int from = Math.max(0, s.candles.size() - bars);
        BacktestService.Result r = backtests.run(new ArrayList<>(s.candles.subList(from, s.candles.size())),
                buyScore, sellScore, macro.riskFor(s.instrument.sector()));
        try {
            cache.put(key, mapper.writeValueAsString(r), Duration.ofSeconds(60));
        } catch (Exception e) {
            log.debug("Falha ao cachear backtest: {}", e.getMessage());
        }
        return r;
    }

    public StreamSettings settings() { return settings; }

    private Series require(String symbol) {
        Series s = series.get(symbol);
        if (s == null) throw new NoSuchElementException(symbol);
        return s;
    }
}
