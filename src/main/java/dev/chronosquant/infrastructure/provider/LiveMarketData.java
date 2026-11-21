package dev.chronosquant.infrastructure.provider;

import dev.chronosquant.application.HistoryResult;
import dev.chronosquant.application.LiveUpdate;
import dev.chronosquant.application.Quote;
import dev.chronosquant.application.StreamSettings;
import dev.chronosquant.application.port.CacheStore;
import dev.chronosquant.application.port.CandleStore;
import dev.chronosquant.application.port.MarketDataPort;
import dev.chronosquant.domain.Candle;
import dev.chronosquant.domain.Instrument;
import dev.chronosquant.domain.MacroSnapshot;
import dev.chronosquant.domain.MarketHours;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dados de mercado reais com failover (Yahoo, Stooq, Frankfurter, banco de dados) e fallback simulado.
 * Polling inteligente: intervalo curto com pregão aberto, longo com pregão fechado, cache compartilhado e
 * respeito aos rate limits de cada provedor. Entre cotações, o fluxo simulado (opcional) mantém o painel vivo.
 */
@Component
public class LiveMarketData implements MarketDataPort {
    private static final Logger log = LoggerFactory.getLogger(LiveMarketData.class);
    private static final int MIN_BARS = 120;
    private static final long NEW_BAR_GAP_MS = 23L * 3_600_000L;

    private static final class Real {
        volatile Quote quote;
        Candle bar;
        double micro;
        boolean pendingNewBar;
        volatile long nextPoll;
        final String source;
        final Random rnd = new Random();

        Real(Quote quote, Candle bar, String source) {
            this.quote = quote;
            this.bar = bar;
            this.source = source;
        }
    }

    private record Source(String name, Supplier<List<Candle>> fetch) {}

    private final boolean simulatedOnly;
    private final long pollOpenMs;
    private final long pollClosedMs;
    private final Duration quoteTtl;
    private final SimulatedFlow sim;
    private final YahooClient yahoo;
    private final StooqClient stooq;
    private final FrankfurterClient frankfurter;
    private final FinnhubClient finnhub;
    private final CandleStore store;
    private final CacheStore cache;
    private final StreamSettings settings;
    private final LiveMacroData macro;
    private final Map<String, Real> real = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public LiveMarketData(SimulatedFlow sim, YahooClient yahoo, StooqClient stooq, FrankfurterClient frankfurter,
                          FinnhubClient finnhub, CandleStore store, CacheStore cache, StreamSettings settings,
                          LiveMacroData macro,
                          @Value("${chronosquant.data.mode:auto}") String mode,
                          @Value("${chronosquant.data.poll-open-seconds:30}") long pollOpen,
                          @Value("${chronosquant.data.poll-closed-seconds:600}") long pollClosed,
                          @Value("${chronosquant.cache.quote-ttl-seconds:25}") long quoteTtl) {
        this.sim = sim;
        this.yahoo = yahoo;
        this.stooq = stooq;
        this.frankfurter = frankfurter;
        this.finnhub = finnhub;
        this.store = store;
        this.cache = cache;
        this.settings = settings;
        this.macro = macro;
        this.simulatedOnly = "simulated".equals(mode);
        this.pollOpenMs = pollOpen * 1000;
        this.pollClosedMs = pollClosed * 1000;
        this.quoteTtl = Duration.ofSeconds(quoteTtl);
    }

    @Override
    public List<Instrument> universe() { return Universe.instruments(); }

    @Override
    public MacroSnapshot nextMacro() { return macro.current(); }

    @Override
    public HistoryResult history(String symbol, int bars) {
        if (!simulatedOnly) {
            Symbols sy = Universe.entry(symbol).symbols();
            List<Source> sources = new ArrayList<>();
            if (sy.yahoo() != null) sources.add(new Source("Yahoo", () -> yahoo.history(sy.yahoo())));
            if (sy.stooq() != null) sources.add(new Source("Stooq", () -> stooq.history(sy.stooq())));
            if (sy.fxBase() != null) sources.add(new Source("Frankfurter", () -> frankfurter.history(sy.fxBase(), sy.fxQuote())));
            for (Source s : sources) {
                try {
                    List<Candle> got = s.fetch().get();
                    if (got.size() >= MIN_BARS) return realHistory(symbol, trim(got, bars), s.name(), true);
                } catch (RuntimeException e) {
                    log.warn("{}: histórico de {} falhou ({})", s.name(), symbol, e.getMessage());
                }
            }
            try {
                List<Candle> db = store.load(symbol, bars);
                if (db.size() >= MIN_BARS) return realHistory(symbol, db, "DB", false);
            } catch (RuntimeException e) {
                log.warn("Banco: leitura de {} falhou ({})", symbol, e.getMessage());
            }
            log.warn("{}: sem dados reais, usando fluxo simulado", symbol);
        }
        return new HistoryResult(sim.history(symbol, bars), "SIMULADO", true);
    }

    private HistoryResult realHistory(String symbol, List<Candle> candles, String source, boolean persist) {
        Candle last = candles.get(candles.size() - 1);
        real.put(symbol, new Real(new Quote(symbol, last.close(), last.time(), source), last, source));
        if (persist) {
            try {
                store.saveAll(symbol, source, candles);
            } catch (RuntimeException e) {
                log.warn("Banco: gravação de {} falhou ({})", symbol, e.getMessage());
            }
        }
        return new HistoryResult(candles, source, false);
    }

    private static List<Candle> trim(List<Candle> c, int bars) {
        return c.size() > bars ? new ArrayList<>(c.subList(c.size() - bars, c.size())) : c;
    }

    @Override
    public LiveUpdate next(String symbol) {
        Instrument ins = Universe.entry(symbol).instrument();
        boolean open = MarketHours.isOpen(ins.region(), ins.assetClass(), Instant.now());
        Real r = real.get(symbol);
        if (r == null) return new LiveUpdate(sim.next(symbol), true, "SIMULADO", true, open);
        synchronized (r) {
            Quote q = r.quote;
            boolean newBar = false;
            if (r.pendingNewBar) {
                r.bar = new Candle(q.tsMillis(), r.bar.close(), q.price(), q.price(), q.price(), 0);
                r.pendingNewBar = false;
                r.micro = 0;
                newBar = true;
            }
            double px = q.price();
            if (settings.flow()) {
                double sigma = ins.annVol() / 2428.0 * 5;
                r.micro = Math.max(-0.004, Math.min(0.004, r.micro * 0.97 + r.rnd.nextGaussian() * sigma));
                px = q.price() * (1 + r.micro);
            }
            Candle b = r.bar;
            r.bar = new Candle(b.time(), b.open(), Math.max(b.high(), px), Math.min(b.low(), px), px, b.volume());
            return new LiveUpdate(r.bar, newBar, r.source, false, open);
        }
    }

    /** Polling escalonado por instrumento: só consulta quem está "vencido" e respeita o cache compartilhado. */
    @Scheduled(fixedDelay = 5_000, initialDelay = 20_000)
    public void refreshQuotes() {
        if (simulatedOnly) return;
        long now = System.currentTimeMillis();
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        real.forEach((symbol, r) -> {
            if (now >= r.nextPoll) jobs.add(CompletableFuture.runAsync(() -> poll(symbol, r), pool));
        });
        jobs.forEach(CompletableFuture::join);
    }

    private void poll(String symbol, Real r) {
        Instrument ins = Universe.entry(symbol).instrument();
        boolean open = MarketHours.isOpen(ins.region(), ins.assetClass(), Instant.now());
        r.nextPoll = System.currentTimeMillis() + (open ? pollOpenMs : pollClosedMs);
        String key = "quote:" + symbol;
        Optional<String> cached = cache.get(key);
        if (cached.isPresent()) {
            apply(r, decode(symbol, cached.get()));
            return;
        }
        Symbols sy = Universe.entry(symbol).symbols();
        List<Supplier<Quote>> chain = new ArrayList<>();
        if (finnhub.enabled() && sy.finnhub() != null) chain.add(() -> finnhub.quote(symbol, sy.finnhub()));
        if (sy.yahoo() != null) chain.add(() -> yahoo.quote(symbol, sy.yahoo()));
        if (sy.stooq() != null) chain.add(() -> stooq.quote(symbol, sy.stooq()));
        if (sy.fxBase() != null) chain.add(() -> frankfurter.latest(symbol, sy.fxBase(), sy.fxQuote()));
        for (Supplier<Quote> s : chain) {
            try {
                Quote q = s.get();
                cache.put(key, q.price() + ";" + q.tsMillis() + ";" + q.source(), quoteTtl);
                apply(r, q);
                return;
            } catch (RuntimeException e) {
                log.debug("Cotação de {} falhou: {}", symbol, e.getMessage());
            }
        }
    }

    private static Quote decode(String symbol, String raw) {
        String[] p = raw.split(";");
        return new Quote(symbol, Double.parseDouble(p[0]), Long.parseLong(p[1]), p[2] + "+cache");
    }

    private static void apply(Real r, Quote q) {
        synchronized (r) {
            if (q.tsMillis() < r.quote.tsMillis()) return;
            if (q.tsMillis() - r.bar.time() >= NEW_BAR_GAP_MS) r.pendingNewBar = true;
            r.quote = q;
        }
    }
}
