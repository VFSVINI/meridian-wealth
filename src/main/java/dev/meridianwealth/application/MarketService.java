package dev.meridianwealth.application;

import dev.meridianwealth.application.port.BacktestCache;
import dev.meridianwealth.application.port.FundamentalsPort;
import dev.meridianwealth.application.port.MarketDataPort;
import dev.meridianwealth.application.port.SignalStore;
import dev.meridianwealth.domain.Assessment;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.domain.Signal;
import dev.meridianwealth.domain.TimingEngine;
import java.lang.System.Logger.Level;
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

/**
 * Estado de mercado por ativo, construído apenas com dados reais.
 *
 * <p>Cada ativo começa {@link DataStatus#UNAVAILABLE} e só passa a ter preço quando um provedor
 * (ou o banco, com o último dado real gravado) devolve histórico. Nenhum valor é interpolado ou
 * animado: a barra viva reflete exatamente a última cotação recebida.
 *
 * <p>POJO sem framework (ADR 001): dependências chegam por construtor e a composição é feita em
 * {@code dev.meridianwealth.config}. Leitura e escrita de estado ficam sob o monitor do objeto;
 * I/O (histórico, banco, cache, backtest) acontece fora dele.
 */
public class MarketService {
    private static final System.Logger LOG = System.getLogger(MarketService.class.getName());
    private static final Duration BACKTEST_TTL = Duration.ofMinutes(10);
    private static final int HISTORY_BARS = 500;
    private static final int MAX_BARS = 1500;

    private static final class Series {
        final Instrument instrument;
        final List<Candle> candles = new ArrayList<>();
        String source;
        Freshness freshness;
        long quoteTime;
        long nextUpdateAt;
        boolean open;
        Signal lastSignal;

        Series(Instrument instrument) { this.instrument = instrument; }

        DataStatus status() {
            if (candles.isEmpty()) return DataStatus.UNAVAILABLE;
            if (candles.size() < TimingEngine.MIN_BARS) return DataStatus.INSUFFICIENT;
            return freshness == Freshness.STORED ? DataStatus.STORED : DataStatus.LIVE;
        }

        boolean assessable() { return candles.size() >= TimingEngine.MIN_BARS; }
    }

    public record SourceView(String symbol, DataStatus status, String source, Freshness freshness, long quoteTime,
                             boolean marketOpen, int bars) {}

    private final MarketDataPort data;
    private final SignalStore signals;
    private final BacktestCache backtestCache;
    private final FundamentalsPort fundamentals;
    private final BacktestService backtests;
    private final TimingEngine engine = new TimingEngine();
    private final Map<String, Series> series = new LinkedHashMap<>();
    private MacroSnapshot macro = MacroSnapshot.unavailable();

    /** Cria o serviço com todos os ativos em {@link DataStatus#UNAVAILABLE}; chame {@link #loadHistories()}. */
    public MarketService(MarketDataPort data, SignalStore signals, BacktestCache backtestCache,
                         FundamentalsPort fundamentals, BacktestService backtests) {
        this.data = data;
        this.signals = signals;
        this.backtestCache = backtestCache;
        this.fundamentals = fundamentals;
        this.backtests = backtests;
        for (Instrument i : data.universe()) series.put(i.symbol(), new Series(i));
    }

    /** Busca o histórico real de todos os ativos em paralelo (I/O fora do lock). */
    public void loadHistories() {
        fetchAndInstall(List.copyOf(series.keySet()));
        MacroSnapshot m = data.macro();
        synchronized (this) {
            macro = m;
        }
    }

    /** Tenta de novo os ativos que continuam sem dado real (provedor fora do ar na partida, por exemplo). */
    public void retryMissing() {
        List<String> missing;
        synchronized (this) {
            missing = series.entrySet().stream()
                    .filter(e -> e.getValue().status() == DataStatus.UNAVAILABLE)
                    .map(Map.Entry::getKey).toList();
        }
        if (!missing.isEmpty()) fetchAndInstall(missing);
    }

    private void fetchAndInstall(List<String> symbols) {
        Map<String, HistoryResult> results = new LinkedHashMap<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<HistoryResult>> jobs = new LinkedHashMap<>();
            for (String s : symbols) jobs.put(s, pool.submit(() -> data.history(s, HISTORY_BARS)));
            for (var e : jobs.entrySet()) results.put(e.getKey(), e.getValue().get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Carga de históricos interrompida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falha ao carregar históricos", e.getCause());
        }
        synchronized (this) {
            results.forEach((symbol, h) -> {
                if (!h.available()) {
                    LOG.log(Level.WARNING, "{0}: sem dado em tempo real e sem dado gravado", symbol);
                    return;
                }
                Series s = series.get(symbol);
                s.candles.clear();
                s.candles.addAll(h.candles());
                s.source = h.source();
                s.freshness = h.freshness();
                s.quoteTime = h.quoteTime();
                s.open = data.marketOpen(symbol);
                LOG.log(Level.INFO, "{0}: {1} barras reais via {2} ({3})", symbol, h.candles().size(), h.source(),
                        h.freshness().label());
            });
        }
    }

    public List<Instrument> universe() { return data.universe(); }

    /** Aplica a última cotação real de cada ativo à barra viva e devolve o lote para o stream. */
    public TickBatch advance() {
        MacroSnapshot m = data.macro();
        List<SignalRecord> changed = new ArrayList<>();
        TickBatch batch;
        synchronized (this) {
            macro = m;
            List<TickBatch.Tick> ticks = new ArrayList<>();
            for (var e : series.entrySet()) {
                Series s = e.getValue();
                boolean newBar = false;
                Optional<LiveUpdate> update = s.candles.isEmpty() ? Optional.empty() : data.next(e.getKey());
                if (update.isPresent()) {
                    newBar = apply(s, update.get());
                } else {
                    s.open = data.marketOpen(e.getKey());
                }
                TickBatch.Tick t = tick(e.getKey(), s, newBar);
                if (t.assessment() != null) signalChange(s, t.assessment()).ifPresent(changed::add);
                ticks.add(t);
            }
            batch = batch(ticks);
        }
        changed.forEach(this::saveSignal);
        return batch;
    }

    private static boolean apply(Series s, LiveUpdate u) {
        if (u.newBar()) {
            s.candles.add(u.candle());
            if (s.candles.size() > MAX_BARS) s.candles.remove(0);
        } else {
            s.candles.set(s.candles.size() - 1, u.candle());
        }
        QuoteMeta q = u.quote();
        s.source = q.source();
        s.freshness = q.freshness();
        s.quoteTime = q.quoteTime();
        s.nextUpdateAt = q.nextUpdateAt();
        s.open = u.marketOpen();
        return u.newBar();
    }

    public synchronized TickBatch overview() {
        List<TickBatch.Tick> ticks = new ArrayList<>();
        series.forEach((sym, s) -> ticks.add(tick(sym, s, false)));
        return batch(ticks);
    }

    private TickBatch batch(List<TickBatch.Tick> ticks) {
        long live = ticks.stream().filter(t -> t.status() == DataStatus.LIVE).count();
        String mode = live == ticks.size() ? "REAL" : live == 0 ? "SEM_DADOS" : "PARCIAL";
        return new TickBatch(System.currentTimeMillis(), mode, macro, ticks);
    }

    private TickBatch.Tick tick(String symbol, Series s, boolean newBar) {
        Candle last = s.candles.isEmpty() ? null : s.candles.get(s.candles.size() - 1);
        Assessment a = s.assessable() ? assess(symbol, s) : null;
        return new TickBatch.Tick(symbol, s.status(), last, a, newBar, s.source, s.quoteTime, s.freshness,
                s.nextUpdateAt, s.open);
    }

    private Assessment assess(String symbol, Series s) {
        return engine.assess(symbol, s.candles, macro.riskFor(s.instrument.sector()), benchmark(s));
    }

    private double[] benchmark(Series s) {
        String bench = s.instrument.benchmark();
        if (bench == null) return null;
        Series b = series.get(bench);
        if (b == null || b == s || b.candles.isEmpty()) return null;
        int from = Math.max(0, b.candles.size() - 500);
        return b.candles.subList(from, b.candles.size()).stream().mapToDouble(Candle::close).toArray();
    }

    private static Optional<SignalRecord> signalChange(Series s, Assessment a) {
        if (a.signal() == s.lastSignal) return Optional.empty();
        s.lastSignal = a.signal();
        return Optional.of(new SignalRecord(a.symbol(), System.currentTimeMillis(), a.score(), a.signal().name(),
                a.phase().name(), a.regime().name(), a.confidence(), a.risk().name(), a.price()));
    }

    private void saveSignal(SignalRecord r) {
        try {
            signals.save(r);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Banco: gravação do sinal de {0} falhou ({1})", r.symbol(), e.getMessage());
        }
    }

    public synchronized List<Candle> candles(String symbol, int limit) {
        List<Candle> h = require(symbol).candles;
        return new ArrayList<>(h.subList(Math.max(0, h.size() - limit), h.size()));
    }

    public synchronized Assessment assessment(String symbol) {
        return assess(symbol, requireAssessable(symbol));
    }

    public synchronized MicroView micro(String symbol) {
        Series s = requireAssessable(symbol);
        Assessment a = assess(symbol, s);
        String bench = s.instrument.benchmark();
        return new MicroView(symbol, s.instrument.sector(), bench == null ? "—" : bench, a.relStrength(),
                a.relVolume(), fundamentals.fundamentals(symbol).orElse(null));
    }

    public List<SignalRecord> signals(String symbol, int limit) {
        synchronized (this) {
            require(symbol);
        }
        return signals.recent(symbol, limit);
    }

    public synchronized List<SourceView> sources() {
        return series.entrySet().stream()
                .map(e -> {
                    Series s = e.getValue();
                    return new SourceView(e.getKey(), s.status(), s.source, s.freshness, s.quoteTime, s.open,
                            s.candles.size());
                })
                .toList();
    }

    public BacktestService.Result backtest(String symbol, int buyScore, int sellScore, int bars) {
        List<Candle> window;
        double risk;
        String key;
        synchronized (this) {
            Series s = requireAssessable(symbol);
            int from = Math.max(0, s.candles.size() - bars);
            window = new ArrayList<>(s.candles.subList(from, s.candles.size()));
            risk = macro.riskFor(s.instrument.sector());
            key = backtestKey(symbol, buyScore, sellScore, bars, window, risk);
        }
        Optional<BacktestService.Result> hit = backtestCache.get(key);
        if (hit.isPresent()) return hit.get();
        BacktestService.Result r = backtests.run(window, buyScore, sellScore, risk);
        backtestCache.put(key, r, BACKTEST_TTL);
        return r;
    }

    /**
     * Chave pela versão dos dados (dívida 4): parâmetros + tamanho da janela + horário e valor exato
     * da última barra + risco macro aplicado. Muda somente quando o resultado pode mudar, em vez de
     * expirar a cada 30 s e gerar recomputações em rajada.
     */
    static String backtestKey(String symbol, int buyScore, int sellScore, int bars, List<Candle> window,
                              double risk) {
        Candle last = window.get(window.size() - 1);
        return "bt:" + symbol + ":" + buyScore + ":" + sellScore + ":" + bars + ":" + window.size() + ":"
                + last.time() + ":" + Long.toHexString(Double.doubleToLongBits(last.close())) + ":"
                + Long.toHexString(Double.doubleToLongBits(risk));
    }

    private Series require(String symbol) {
        Series s = series.get(symbol);
        if (s == null) throw new NoSuchElementException(symbol);
        return s;
    }

    private Series requireAssessable(String symbol) {
        Series s = require(symbol);
        if (!s.assessable()) {
            String msg = s.status() == DataStatus.UNAVAILABLE
                    ? "Sem dado em tempo real para " + symbol + ": nenhum provedor respondeu e não há dado real gravado."
                    : "Dados reais insuficientes para " + symbol + ": " + s.candles.size() + " de "
                            + TimingEngine.MIN_BARS + " barras necessárias.";
            throw new DataUnavailableException(symbol, s.status(), msg);
        }
        return s;
    }
}
