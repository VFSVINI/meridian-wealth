package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.domain.GeoRiskScorer;
import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.domain.NewsItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Radar macro: VIX, dólar e juros (Yahoo/FRED), manchetes (RSS/Finnhub) com fallback simulado por componente. */
@Component
public class LiveMacroData {
    private static final Logger log = LoggerFactory.getLogger(LiveMacroData.class);
    private static final long NEWS_EVERY_MS = 300_000;

    private final SimulatedFlow sim;
    private final YahooClient yahoo;
    private final FredClient fred;
    private final FinnhubClient finnhub;
    private final RssNewsClient rss;
    private final boolean simulatedOnly;

    private volatile MacroSnapshot snapshot;
    private volatile boolean anyReal;
    private double vix;
    private double usd;
    private double y10;
    private double geo;
    private List<NewsItem> news = List.of();
    private Map<String, Double> sectors = Map.of();
    private long lastNews;

    public LiveMacroData(SimulatedFlow sim, YahooClient yahoo, FredClient fred, FinnhubClient finnhub,
                         RssNewsClient rss, @Value("${meridianwealth.data.mode:auto}") String mode) {
        this.sim = sim;
        this.yahoo = yahoo;
        this.fred = fred;
        this.finnhub = finnhub;
        this.rss = rss;
        this.simulatedOnly = "simulated".equals(mode);
        MacroSnapshot s = sim.nextMacro();
        this.snapshot = s;
        this.vix = s.vix();
        this.usd = s.dollarIndex();
        this.y10 = s.yield10y();
        this.geo = s.geoRisk();
    }

    public MacroSnapshot current() { return simulatedOnly || !anyReal ? sim.nextMacro() : snapshot; }

    @Scheduled(fixedDelay = 60_000, initialDelay = 8_000)
    public synchronized void refresh() {
        if (simulatedOnly) return;
        List<String> real = new ArrayList<>();
        try { vix = yahoo.lastPrice("^VIX"); real.add("VIX"); } catch (RuntimeException e) { log.debug("VIX: {}", e.getMessage()); }
        try { usd = yahoo.lastPrice("DX-Y.NYB"); real.add("DXY"); } catch (RuntimeException e) { log.debug("DXY: {}", e.getMessage()); }
        try {
            y10 = fred.enabled() ? fred.latest("DGS10") : yahoo.lastPrice("^TNX");
            real.add("Juros 10a");
        } catch (RuntimeException e) { log.debug("Juros: {}", e.getMessage()); }
        long now = System.currentTimeMillis();
        if (now - lastNews > NEWS_EVERY_MS) {
            refreshNews();
            lastNews = now;
        }
        if (!news.isEmpty()) real.add("Notícias");
        anyReal = !real.isEmpty();
        String top = news.isEmpty() ? "Sem manchetes disponíveis" : news.get(0).title();
        String label = real.isEmpty() ? "SIMULADO" : "REAL: " + String.join(", ", real);
        snapshot = MacroSnapshot.of(vix, usd, y10, geo, top, label, news, sectors);
    }

    private void refreshNews() {
        List<Headline> heads = new ArrayList<>(rss.fetch());
        if (finnhub.enabled()) {
            try { heads.addAll(finnhub.news()); } catch (RuntimeException e) { log.debug("Notícias Finnhub: {}", e.getMessage()); }
        }
        if (heads.isEmpty()) return;
        List<NewsItem> scored = heads.stream().map(h -> GeoRiskScorer.score(h.title(), h.source())).toList();
        geo = GeoRiskScorer.aggregate(scored);
        sectors = GeoRiskScorer.sectorImpact(scored);
        news = scored.stream().sorted(Comparator.comparingDouble(NewsItem::risk).reversed()).limit(8).toList();
    }
}
