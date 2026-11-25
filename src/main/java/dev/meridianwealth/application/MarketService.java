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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
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
 * {@code dev.meridianwealth.config}.
 *
 * <p><b>Concorrência (dívida 3):</b> há um único escritor por vez ({@code writeLock}: atualização por tick e carga de histórico) que muda o
 * estado mutável e, ao fim, <b>publica um snapshot imutável</b> ({@link View} por ativo, num mapa trocado atomicamente por um campo
 * {@code volatile}). Todas as leituras da API ({@link #overview()}, {@link #candles}, {@link #assessment}, {@link #micro},
 * {@link #sources()}, {@link #backtest}) usam só o snapshot e não tomam monitor nenhum: uma atualização lenta não faz nenhuma requisição
 * esperar. I/O (histórico, banco, cache, backtest) acontece fora do lock de escrita.
 */
public class MarketService {
    private static final System.Logger LOG = System.getLogger(MarketService.class.getName());
    private static final Duration BACKTEST_TTL = Duration.ofMinutes(10);
    private static final int HISTORY_BARS = 500;
    private static final int MAX_BARS = 1500;
    /** Ativos do catálogo acompanhados sob demanda (além do universo curado): limite de memória e de chamadas aos provedores. */
    static final int MAX_TRACKED = 40;

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

    /** Estado de um ativo num instante: imutável, com as barras copiadas e a avaliação já calculada (nula se não há barras suficientes). */
    private record View(Instrument instrument, DataStatus status, List<Candle> candles, String source, Freshness freshness, long quoteTime,
                        long nextUpdateAt, boolean open, Assessment assessment) {
        boolean assessable() { return assessment != null; }
    }

    private final MarketDataPort data;
    private final SignalStore signals;
    private final BacktestCache backtestCache;
    private final FundamentalsPort fundamentals;
    private final BacktestService backtests;
    private final TimingEngine engine = new TimingEngine();
    /** Estado mutável: só o escritor (sob {@code writeLock}) o toca. */
    private final Map<String, Series> series = new LinkedHashMap<>();
    private final Object writeLock = new Object();
    /** Símbolos acompanhados sob demanda, do mais antigo ao mais recente. Só o escritor (sob {@code writeLock}) o toca. */
    private final java.util.LinkedHashSet<String> tracked = new java.util.LinkedHashSet<>();
    /** Snapshot imutável publicado atomicamente; é o que as leituras enxergam. */
    private volatile Map<String, View> views = Map.of();
    private volatile MacroSnapshot macro = MacroSnapshot.unavailable();

    /** Cria o serviço com todos os ativos em {@link DataStatus#UNAVAILABLE}; chame {@link #loadHistories()}. */
    public MarketService(MarketDataPort data, SignalStore signals, BacktestCache backtestCache,
                         FundamentalsPort fundamentals, BacktestService backtests) {
        this.data = data;
        this.signals = signals;
        this.backtestCache = backtestCache;
        this.fundamentals = fundamentals;
        this.backtests = backtests;
        for (Instrument i : data.universe()) series.put(i.symbol(), new Series(i));
        synchronized (writeLock) {
            publish();
        }
    }

    /** Busca o histórico real de todos os ativos em paralelo (I/O fora do lock). */
    public void loadHistories() {
        fetchAndInstall(List.copyOf(series.keySet()));
        macro = data.macro();
    }

    /** Tenta de novo os ativos que continuam sem dado real (provedor fora do ar na partida, por exemplo). */
    public void retryMissing() {
        List<String> missing = views.entrySet().stream()
                .filter(e -> e.getValue().status() == DataStatus.UNAVAILABLE)
                .map(Map.Entry::getKey).toList();
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
        synchronized (writeLock) {
            results.forEach((symbol, h) -> {
                if (!h.available()) {
                    LOG.log(Level.WARNING, "{0}: sem dado em tempo real e sem dado gravado", symbol);
                    return;
                }
                install(series.get(symbol), symbol, h);
            });
            publish();
        }
    }

    private void install(Series s, String symbol, HistoryResult h) {
        s.candles.clear();
        s.candles.addAll(h.candles());
        s.source = h.source();
        s.freshness = h.freshness();
        s.quoteTime = h.quoteTime();
        s.open = data.marketOpen(symbol);
        LOG.log(Level.INFO, "{0}: {1} barras reais via {2} ({3})", symbol, h.candles().size(), h.source(), h.freshness().label());
    }

    /** O universo curado e, depois dele, os ativos do catálogo que alguém abriu e seguem acompanhados (até {@value #MAX_TRACKED}). */
    public List<Instrument> universe() { return views.values().stream().map(View::instrument).toList(); }

    /**
     * Acompanha sob demanda um ativo negociado do catálogo (item #33): busca o histórico real, passa a atualizá-lo a cada ciclo e, com barras
     * suficientes, o motor já calcula o sinal dele como o dos demais. O histórico é buscado fora do lock; sem dado real o ativo NÃO entra
     * (nada é inventado). Passando de {@value #MAX_TRACKED} ativos sob demanda, sai o que foi aberto há mais tempo (o curado nunca sai).
     *
     * @throws NoSuchElementException se o símbolo não existe no catálogo
     * @throws DataUnavailableException se nenhum provedor devolveu dado real
     */
    public Instrument track(String symbol) {
        View known = views.get(symbol);
        if (known != null) {
            synchronized (writeLock) {
                if (tracked.remove(symbol)) tracked.add(symbol);   /* aberto de novo: passa a ser o mais recente */
            }
            return known.instrument();
        }
        Instrument inst = data.find(symbol).orElseThrow(() -> new NoSuchElementException(symbol));
        HistoryResult h = data.history(symbol, HISTORY_BARS);
        if (!h.available()) {
            throw new DataUnavailableException(symbol, DataStatus.UNAVAILABLE,
                    "Sem dado em tempo real para " + symbol + ": nenhum provedor respondeu e não há dado real gravado.");
        }
        synchronized (writeLock) {
            if (!series.containsKey(symbol)) {
                Series s = new Series(inst);
                install(s, symbol, h);
                series.put(symbol, s);
                tracked.add(symbol);
                while (tracked.size() > MAX_TRACKED) {
                    String oldest = tracked.iterator().next();
                    tracked.remove(oldest);
                    series.remove(oldest);
                }
                publish();
            }
        }
        return inst;
    }

    /** Busca por símbolo ou nome no catálogo de ativos negociados; {@code tracked} diz o que já está acompanhado. */
    public List<Instrument> search(String query, int limit) { return data.search(query, limit); }

    public boolean isTracked(String symbol) { return views.containsKey(symbol); }

    /** Último radar macro obtido (indisponível quando nenhum provedor respondeu). */
    public MacroSnapshot macro() { return macro; }

    /** Aplica a última cotação real de cada ativo à barra viva, publica o novo snapshot e devolve o lote para o stream. */
    public TickBatch advance() {
        MacroSnapshot m = data.macro();
        List<SignalRecord> changed = new ArrayList<>();
        Map<String, Boolean> newBars = new LinkedHashMap<>();
        Map<String, View> published;
        synchronized (writeLock) {
            macro = m;
            for (var e : series.entrySet()) {
                Series s = e.getValue();
                boolean newBar = false;
                Optional<LiveUpdate> update = s.candles.isEmpty() ? Optional.empty() : data.next(e.getKey());
                if (update.isPresent()) {
                    newBar = apply(s, update.get());
                } else {
                    s.open = data.marketOpen(e.getKey());
                }
                newBars.put(e.getKey(), newBar);
            }
            published = publish();
            for (var e : published.entrySet()) {
                View v = e.getValue();
                if (v.assessment() != null) signalChange(series.get(e.getKey()), v.assessment()).ifPresent(changed::add);
            }
        }
        changed.forEach(this::saveSignal);
        List<TickBatch.Tick> ticks = new ArrayList<>();
        published.forEach((sym, v) -> ticks.add(tick(sym, v, newBars.getOrDefault(sym, false))));
        return batch(ticks);
    }

    /** Constrói e publica o snapshot imutável a partir do estado mutável. Só o escritor chama, com {@code writeLock}. */
    private Map<String, View> publish() {
        Map<String, View> next = new LinkedHashMap<>();
        for (var e : series.entrySet()) next.put(e.getKey(), view(e.getKey(), e.getValue()));
        Map<String, View> frozen = java.util.Collections.unmodifiableMap(next);
        views = frozen;
        return frozen;
    }

    private View view(String symbol, Series s) {
        Assessment a = s.assessable() ? assess(symbol, s) : null;
        return new View(s.instrument, s.status(), List.copyOf(s.candles), s.source, s.freshness, s.quoteTime, s.nextUpdateAt, s.open, a);
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

    public TickBatch overview() {
        List<TickBatch.Tick> ticks = new ArrayList<>();
        views.forEach((sym, v) -> ticks.add(tick(sym, v, false)));
        return batch(ticks);
    }

    private TickBatch batch(List<TickBatch.Tick> ticks) {
        long live = ticks.stream().filter(t -> t.status() == DataStatus.LIVE).count();
        String mode = live == ticks.size() ? "REAL" : live == 0 ? "SEM_DADOS" : "PARCIAL";
        return new TickBatch(System.currentTimeMillis(), mode, macro, ticks);
    }

    private TickBatch.Tick tick(String symbol, View v, boolean newBar) {
        Candle last = v.candles().isEmpty() ? null : v.candles().get(v.candles().size() - 1);
        boolean stale = v.freshness() != null && v.freshness().isStale(v.quoteTime(), System.currentTimeMillis(), v.open());
        return new TickBatch.Tick(symbol, v.status(), last, v.assessment(), newBar, v.source(), v.quoteTime(), v.freshness(),
                v.nextUpdateAt(), v.open(), stale);
    }

    private Assessment assess(String symbol, Series s) {
        return engine.assess(symbol, s.candles, macro.riskFor(s.instrument.sector()), benchmark(s),
                s.instrument.periodsPerYear());
    }

    private double[] benchmark(Series s) {
        String bench = s.instrument.benchmark();
        if (bench == null) return null;
        Series b = series.get(bench);
        if (b == null || b == s || b.candles.isEmpty()) return null;
        return alignedCloses(s, b);
    }

    /**
     * Fechamentos do benchmark alinhados, um a um, às barras do ativo pela data local de cada bolsa.
     * Dia sem barra no benchmark (feriado de uma das bolsas) herda o último fechamento anterior; datas
     * anteriores à primeira barra do benchmark usam o primeiro fechamento disponível.
     */
    static double[] alignedCloses(Series asset, Series bench) {
        TreeMap<LocalDate, Double> byDay = new TreeMap<>();
        ZoneId benchZone = bench.instrument.exchange().zone();
        for (Candle c : bench.candles) byDay.put(localDate(c.time(), benchZone), c.close());
        ZoneId assetZone = asset.instrument.exchange().zone();
        double first = bench.candles.get(0).close();
        double[] out = new double[asset.candles.size()];
        for (int i = 0; i < out.length; i++) {
            Map.Entry<LocalDate, Double> e = byDay.floorEntry(localDate(asset.candles.get(i).time(), assetZone));
            out[i] = e == null ? first : e.getValue();
        }
        return out;
    }

    private static LocalDate localDate(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
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

    public List<Candle> candles(String symbol, int limit) {
        List<Candle> h = require(symbol).candles();
        return new ArrayList<>(h.subList(Math.max(0, h.size() - limit), h.size()));
    }

    public Assessment assessment(String symbol) {
        return requireAssessable(symbol).assessment();
    }

    public MicroView micro(String symbol) {
        View v = requireAssessable(symbol);
        Assessment a = v.assessment();
        String bench = v.instrument().benchmark();
        return new MicroView(symbol, v.instrument().sector(), bench == null ? "—" : bench, a.relStrength(),
                a.relVolume(), fundamentals.fundamentals(symbol).orElse(null));
    }

    public List<SignalRecord> signals(String symbol, int limit) {
        require(symbol);
        return signals.recent(symbol, limit);
    }

    public List<SourceView> sources() {
        return views.entrySet().stream()
                .map(e -> {
                    View v = e.getValue();
                    return new SourceView(e.getKey(), v.status(), v.source(), v.freshness(), v.quoteTime(), v.open(),
                            v.candles().size());
                })
                .toList();
    }

    public BacktestService.Result backtest(String symbol, int buyScore, int sellScore, int bars) {
        View v = requireAssessable(symbol);
        int from = Math.max(0, v.candles().size() - bars);
        List<Candle> window = new ArrayList<>(v.candles().subList(from, v.candles().size()));
        double risk = macro.riskFor(v.instrument().sector());
        int perYear = v.instrument().periodsPerYear();
        String key = backtestKey(symbol, buyScore, sellScore, bars, window, risk);
        Optional<BacktestService.Result> hit = backtestCache.get(key);
        if (hit.isPresent()) return hit.get();
        BacktestService.Result r = backtests.run(window, buyScore, sellScore, risk, perYear);
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

    private View require(String symbol) {
        View v = views.get(symbol);
        if (v == null) throw new NoSuchElementException(symbol);
        return v;
    }

    private View requireAssessable(String symbol) {
        View v = require(symbol);
        if (!v.assessable()) {
            String msg = v.status() == DataStatus.UNAVAILABLE
                    ? "Sem dado em tempo real para " + symbol + ": nenhum provedor respondeu e não há dado real gravado."
                    : "Dados reais insuficientes para " + symbol + ": " + v.candles().size() + " de "
                            + TimingEngine.MIN_BARS + " barras necessárias.";
            throw new DataUnavailableException(symbol, v.status(), msg);
        }
        return v;
    }
}
