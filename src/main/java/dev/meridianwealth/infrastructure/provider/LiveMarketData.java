package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.application.HistoryResult;
import dev.meridianwealth.application.LiveUpdate;
import dev.meridianwealth.application.Quote;
import dev.meridianwealth.application.QuoteMeta;
import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.application.port.CandleStore;
import dev.meridianwealth.application.port.MarketDataPort;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.domain.MarketHours;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Dados de mercado reais com failover: Yahoo, Stooq e Frankfurter para histórico; Finnhub, Yahoo,
 * Stooq e Frankfurter para cotações; banco de dados com o último dado real gravado como último
 * recurso. Quando nada responde, o ativo fica sem dado (nunca é preenchido com valores sintéticos).
 *
 * <p>Polling inteligente: intervalo curto com pregão aberto, longo com pregão fechado, cache
 * compartilhado entre instâncias e respeito aos rate limits de cada provedor.
 */
@Component
public class LiveMarketData implements MarketDataPort {
    static final String STORED_SOURCE = "Banco";
    private static final Logger log = LoggerFactory.getLogger(LiveMarketData.class);
    private static final int MIN_BARS = 120;
    private static final long NEW_BAR_GAP_MS = 23L * 3_600_000L;
    private static final long FIRST_POLL_DELAY_MS = 20_000;

    private static final class Real {
        Quote quote;
        Candle bar;
        boolean pendingNewBar;
        volatile long nextPoll;

        Real(Quote quote, Candle bar, long nextPoll) {
            this.quote = quote;
            this.bar = bar;
            this.nextPoll = nextPoll;
        }
    }

    /** Histórico de uma fonte; {@code lastQuote} traz o horário real da última cotação quando a fonte informa. */
    private record Fetched(List<Candle> candles, Quote lastQuote) {}

    private record Source(String name, Supplier<Fetched> fetch) {}

    private final long pollOpenMs;
    private final long pollClosedMs;
    private final Duration quoteTtl;
    private final YahooClient yahoo;
    private final StooqClient stooq;
    private final BrapiClient brapi;
    private final FrankfurterClient frankfurter;
    private final FinnhubClient finnhub;
    private final CandleStore store;
    private final CacheStore cache;
    private final LiveMacroData macro;
    private final Map<String, Real> real = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public LiveMarketData(YahooClient yahoo, StooqClient stooq, BrapiClient brapi, FrankfurterClient frankfurter,
                          FinnhubClient finnhub, CandleStore store, CacheStore cache, LiveMacroData macro,
                          @Value("${meridianwealth.data.poll-open-seconds:30}") long pollOpen,
                          @Value("${meridianwealth.data.poll-closed-seconds:600}") long pollClosed,
                          @Value("${meridianwealth.cache.quote-ttl-seconds:25}") long quoteTtl) {
        this.yahoo = yahoo;
        this.stooq = stooq;
        this.brapi = brapi;
        this.frankfurter = frankfurter;
        this.finnhub = finnhub;
        this.store = store;
        this.cache = cache;
        this.macro = macro;
        this.pollOpenMs = pollOpen * 1000;
        this.pollClosedMs = pollClosed * 1000;
        this.quoteTtl = Duration.ofSeconds(quoteTtl);
    }

    @Override
    public List<Instrument> universe() { return Universe.instruments(); }

    @Override
    public MacroSnapshot macro() { return macro.current(); }

    @Override
    public boolean marketOpen(String symbol) {
        return MarketHours.isOpen(Universe.entry(symbol).instrument().exchange(), Instant.now());
    }

    /**
     * Selo de frescor pelo atraso nominal da fonte ({@link Provider}) e pelo pregão: com o pregão
     * fechado, toda cotação é fechamento; o último dado gravado é sempre "gravado". Fonte desconhecida
     * recebe o selo mais conservador (atrasado).
     */
    static Freshness freshness(String source, boolean open) {
        Freshness nominal = Provider.byLabel(source).map(Provider::nominal).orElse(Freshness.DELAYED);
        if (nominal == Freshness.STORED) return Freshness.STORED;
        return open ? nominal : Freshness.END_OF_DAY;
    }

    @Override
    public HistoryResult history(String symbol, int bars) {
        Symbols sy = Universe.entry(symbol).symbols();
        List<Source> sources = new ArrayList<>();
        if (sy.brapi() != null) sources.add(new Source(Provider.BRAPI.label(), () -> new Fetched(brapi.history(sy.brapi()), null)));
        if (sy.yahoo() != null) {
            sources.add(new Source("Yahoo", () -> {
                YahooClient.Chart c = yahoo.historyChart(sy.yahoo());
                return new Fetched(c.candles(), new Quote(symbol, c.price(), c.tsMillis(), "Yahoo"));
            }));
        }
        if (sy.stooq() != null && stooq.enabled()) sources.add(new Source("Stooq", () -> new Fetched(stooq.history(sy.stooq()), null)));
        if (sy.fxBase() != null) {
            sources.add(new Source("Frankfurter",
                    () -> new Fetched(frankfurter.history(sy.fxBase(), sy.fxQuote()), null)));
        }
        for (Source s : sources) {
            try {
                Fetched got = s.fetch().get();
                if (got.candles().size() >= MIN_BARS) {
                    return realHistory(symbol, trim(got.candles(), bars), s.name(), got.lastQuote(), true);
                }
                log.warn("{}: histórico de {} com só {} barras", s.name(), symbol, got.candles().size());
            } catch (RuntimeException e) {
                log.warn("{}: histórico de {} falhou ({})", s.name(), symbol, e.getMessage());
            }
        }
        try {
            List<Candle> db = store.load(symbol, bars);
            if (db.size() >= MIN_BARS) return realHistory(symbol, db, STORED_SOURCE, null, false);
        } catch (RuntimeException e) {
            log.warn("Banco: leitura de {} falhou ({})", symbol, e.getMessage());
        }
        return HistoryResult.empty();
    }

    private HistoryResult realHistory(String symbol, List<Candle> candles, String source, Quote lastQuote,
                                      boolean persist) {
        Candle last = candles.get(candles.size() - 1);
        Quote q = lastQuote != null ? lastQuote : new Quote(symbol, last.close(), last.time(), source);
        real.put(symbol, new Real(q, last, System.currentTimeMillis() + FIRST_POLL_DELAY_MS));
        if (persist) {
            try {
                store.saveAll(symbol, source, candles);
            } catch (RuntimeException e) {
                log.warn("Banco: gravação de {} falhou ({})", symbol, e.getMessage());
            }
        }
        return new HistoryResult(candles, source, q.tsMillis(), freshness(source, marketOpen(symbol)));
    }

    private static List<Candle> trim(List<Candle> c, int bars) {
        return c.size() > bars ? new ArrayList<>(c.subList(c.size() - bars, c.size())) : c;
    }

    @Override
    public Optional<LiveUpdate> next(String symbol) {
        Real r = real.get(symbol);
        if (r == null) return Optional.empty();
        boolean open = marketOpen(symbol);
        synchronized (r) {
            Quote q = r.quote;
            boolean newBar = false;
            if (r.pendingNewBar) {
                r.bar = new Candle(q.tsMillis(), r.bar.close(), q.price(), q.price(), q.price(), 0);
                r.pendingNewBar = false;
                newBar = true;
            }
            Candle b = r.bar;
            double px = q.price();
            r.bar = new Candle(b.time(), b.open(), Math.max(b.high(), px), Math.min(b.low(), px), px, b.volume());
            QuoteMeta meta = new QuoteMeta(q.source(), q.tsMillis(), freshness(q.source(), open), r.nextPoll);
            return Optional.of(new LiveUpdate(r.bar, newBar, meta, open));
        }
    }

    /** Polling escalonado por instrumento: só consulta quem está "vencido" e respeita o cache compartilhado. */
    @Scheduled(fixedDelay = 5_000, initialDelay = FIRST_POLL_DELAY_MS)
    public void refreshQuotes() {
        long now = System.currentTimeMillis();
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        real.forEach((symbol, r) -> {
            if (now >= r.nextPoll) jobs.add(CompletableFuture.runAsync(() -> poll(symbol, r), pool));
        });
        jobs.forEach(CompletableFuture::join);
    }

    private void poll(String symbol, Real r) {
        r.nextPoll = System.currentTimeMillis() + (marketOpen(symbol) ? pollOpenMs : pollClosedMs);
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
        if (sy.stooq() != null && stooq.enabled()) chain.add(() -> stooq.quote(symbol, sy.stooq()));
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
        log.debug("{}: nenhuma cotação nova; mantendo a última real de {}", symbol, r.quote.source());
    }

    /** Atualiza a cotação de um ativo já carregado (usado pelo polling e pelos testes). */
    void refresh(String symbol) {
        Real r = real.get(symbol);
        if (r != null) poll(symbol, r);
    }

    private static Quote decode(String symbol, String raw) {
        String[] p = raw.split(";");
        return new Quote(symbol, Double.parseDouble(p[0]), Long.parseLong(p[1]), p[2]);
    }

    private static void apply(Real r, Quote q) {
        synchronized (r) {
            boolean upgradeFromStored = STORED_SOURCE.equals(r.quote.source());
            if (!upgradeFromStored && q.tsMillis() < r.quote.tsMillis()) return;
            if (q.tsMillis() - r.bar.time() >= NEW_BAR_GAP_MS) r.pendingNewBar = true;
            r.quote = q;
        }
    }
}
