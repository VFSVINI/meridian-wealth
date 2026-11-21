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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Radar macro: VIX, dólar e juros (Yahoo/FRED) e manchetes (RSS/Finnhub). Cada componente fica
 * {@code null} até um provedor responder; um valor real obtido antes é mantido se a fonte cair.
 */
@Component
public class LiveMacroData {
    private static final Logger log = LoggerFactory.getLogger(LiveMacroData.class);
    private static final long NEWS_EVERY_MS = 300_000;

    private final YahooClient yahoo;
    private final FredClient fred;
    private final FinnhubClient finnhub;
    private final RssNewsClient rss;

    private volatile MacroSnapshot snapshot = MacroSnapshot.unavailable();
    private Double vix;
    private Double usd;
    private Double y10;
    private Double geo;
    private List<NewsItem> news = List.of();
    private Map<String, Double> sectors = Map.of();
    private long lastNews;

    public LiveMacroData(YahooClient yahoo, FredClient fred, FinnhubClient finnhub, RssNewsClient rss) {
        this.yahoo = yahoo;
        this.fred = fred;
        this.finnhub = finnhub;
        this.rss = rss;
    }

    public MacroSnapshot current() { return snapshot; }

    @Scheduled(fixedDelay = 60_000, initialDelay = 8_000)
    public synchronized void refresh() {
        List<String> got = new ArrayList<>();
        try { vix = yahoo.lastPrice("^VIX"); got.add("VIX"); } catch (RuntimeException e) { log.debug("VIX: {}", e.getMessage()); }
        try { usd = yahoo.lastPrice("DX-Y.NYB"); got.add("DXY"); } catch (RuntimeException e) { log.debug("DXY: {}", e.getMessage()); }
        try {
            y10 = fred.enabled() ? fred.latest("DGS10") : yahoo.lastPrice("^TNX");
            got.add(fred.enabled() ? "Juros 10a (FRED)" : "Juros 10a (Yahoo)");
        } catch (RuntimeException e) { log.debug("Juros: {}", e.getMessage()); }
        long now = System.currentTimeMillis();
        if (now - lastNews > NEWS_EVERY_MS) {
            refreshNews();
            lastNews = now;
        }
        if (!news.isEmpty()) got.add("Notícias");
        String top = news.isEmpty() ? "Sem manchetes disponíveis" : news.get(0).title();
        String label = got.isEmpty() ? "SEM DADOS NESTA RODADA" : "REAL: " + String.join(", ", got);
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
