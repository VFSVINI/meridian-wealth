package dev.meridianwealth.application;

import dev.meridianwealth.application.port.BrazilHistoryPort;
import dev.meridianwealth.domain.HistorySeries;
import dev.meridianwealth.domain.RateHistory;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caso de uso da página Brasil (item #50): histórico de 12 meses das séries oficiais, as mudanças da meta Selic em 24 meses (a linha do tempo) e a
 * comparação de juro real. Só dado do Banco Central: o que não chegou fica de fora e a lista {@code unavailable} diz o quê. O histórico muda devagar,
 * então fica em cache por {@value #TTL_MINUTES} minutos; se o Banco Central falha, vale o último dado bom (marcado como desatualizado depois do prazo).
 */
public class BrazilHistoryService {
    static final long TTL_MINUTES = 30;
    private static final Duration TTL = Duration.ofMinutes(TTL_MINUTES);
    /** Depois de uma falha, só tenta o Banco Central de novo depois disto: sem isso cada visita à página consultaria de novo a série que está fora do ar. */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(2);
    private static final int CHART_MONTHS = 12;
    private static final int DECISION_MONTHS = 24;
    /** Acima disto a poupança rende 0,5% ao mês mais a TR; até isto, 70% da Selic mais a TR (Lei 12.703/2012). */
    static final BigDecimal SAVINGS_SELIC_THRESHOLD = new BigDecimal("8.5");
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);

    /** Uma série pronta para o gráfico; {@code stale} quando a última consulta bem-sucedida passou do prazo. */
    public record Series(HistorySeries series, List<RateHistory.Point> points, Instant fetchedAt, boolean stale) {
        public RateHistory.Point last() { return points.get(points.size() - 1); }
    }

    /** Uma mudança da meta Selic: de quanto para quanto, e a data em que a nova meta passou a valer. */
    public record Decision(LocalDate date, BigDecimal from, BigDecimal to) {
        public String direction() { return to.compareTo(from) > 0 ? "ALTA" : "QUEDA"; }
    }

    /** Uma linha da comparação de juro real: a taxa nominal ao ano, o que sobra da inflação e a regra usada. */
    public record Comparison(String key, String label, BigDecimal nominal, BigDecimal real, String rule) {}

    public record View(Instant asOf, Map<HistorySeries, Series> series, List<HistorySeries> unavailable, List<Decision> decisions, List<Comparison> comparison) {}

    private record Cached(RateHistory history, Instant loadedAt) {}

    private final BrazilHistoryPort port;
    private final Clock clock;
    private final Map<HistorySeries, Cached> cache = new EnumMap<>(HistorySeries.class);
    private final Map<HistorySeries, Instant> lastFailure = new EnumMap<>(HistorySeries.class);

    public BrazilHistoryService(BrazilHistoryPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public synchronized View view() {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        Map<HistorySeries, Series> out = new EnumMap<>(HistorySeries.class);
        List<HistorySeries> missing = new ArrayList<>();
        List<Decision> decisions = List.of();
        for (HistorySeries s : HistorySeries.values()) {
            int months = s == HistorySeries.SELIC_TARGET ? DECISION_MONTHS : CHART_MONTHS + 1;
            Optional<Cached> c = load(s, today.minusMonths(months), today, now);
            if (c.isEmpty()) { missing.add(s); continue; }
            RateHistory h = c.get().history();
            if (s == HistorySeries.SELIC_TARGET) decisions = decisions(h);
            List<RateHistory.Point> pts = h.since(today.minusMonths(CHART_MONTHS));
            if (pts.isEmpty()) { missing.add(s); continue; }
            out.put(s, new Series(s, pts, h.fetchedAt(), Duration.between(c.get().loadedAt(), now).compareTo(TTL.multipliedBy(2)) > 0));
        }
        return new View(now, out, List.copyOf(missing), decisions, comparison(out));
    }

    private Optional<Cached> load(HistorySeries s, LocalDate from, LocalDate to, Instant now) {
        Cached c = cache.get(s);
        if (c != null && Duration.between(c.loadedAt(), now).compareTo(TTL) < 0) return Optional.of(c);
        Instant failed = lastFailure.get(s);
        if (failed != null && Duration.between(failed, now).compareTo(RETRY_AFTER_FAILURE) < 0) return Optional.ofNullable(c);
        Optional<RateHistory> fresh = port.history(s, from, to);
        if (fresh.isPresent()) {
            c = new Cached(fresh.get(), now);
            cache.put(s, c);
            lastFailure.remove(s);
            return Optional.of(c);
        }
        lastFailure.put(s, now);
        return Optional.ofNullable(c);   // falhou: vale o último dado bom, que a tela marca como desatualizado depois do prazo
    }

    /** Mudanças da meta Selic na janela, da mais recente para a mais antiga; o primeiro ponto só fixa o valor inicial. */
    static List<Decision> decisions(RateHistory h) {
        List<Decision> out = new ArrayList<>();
        BigDecimal prev = null;
        for (RateHistory.Point p : h.points()) {
            if (prev != null && p.value().compareTo(prev) != 0) out.add(new Decision(p.date(), prev, p.value()));
            prev = p.value();
        }
        java.util.Collections.reverse(out);
        return out;
    }

    private static List<Comparison> comparison(Map<HistorySeries, Series> s) {
        Series ipca = s.get(HistorySeries.IPCA_12M);
        if (ipca == null) return List.of();
        BigDecimal infl = ipca.last().value();
        List<Comparison> out = new ArrayList<>();
        Series selic = s.get(HistorySeries.SELIC_TARGET), cdi = s.get(HistorySeries.CDI_ANNUAL);
        if (selic != null) {
            BigDecimal sv = selic.last().value();
            BigDecimal savings = sv.compareTo(SAVINGS_SELIC_THRESHOLD) > 0 ? new BigDecimal("1.005").pow(12).subtract(BigDecimal.ONE).movePointRight(2) : sv.multiply(new BigDecimal("0.70"));
            out.add(new Comparison("savings", "Poupança", scale(savings), real(savings, infl),
                    sv.compareTo(SAVINGS_SELIC_THRESHOLD) > 0 ? "0,5% ao mês, mais a TR (não incluída), porque a Selic está acima de 8,5%" : "70% da Selic, mais a TR (não incluída)"));
            out.add(new Comparison("treasurySelic", "Tesouro Selic", scale(sv), real(sv, infl), "acompanha a meta Selic, antes de imposto e taxas"));
        }
        if (cdi != null) {
            BigDecimal cv = cdi.last().value();
            out.add(new Comparison("cdb100", "CDB 100% do CDI", scale(cv), real(cv, infl), "100% do CDI anualizado, antes de imposto"));
        }
        out.add(new Comparison("ipca", "Inflação (IPCA, 12 meses)", scale(infl), BigDecimal.ZERO.setScale(2), "o que os preços subiram"));
        return List.copyOf(out);
    }

    /** Juro real: (1 + nominal) / (1 + inflação) - 1, em pontos percentuais com duas casas. */
    static BigDecimal real(BigDecimal nominalPct, BigDecimal inflationPct) {
        BigDecimal n = BigDecimal.ONE.add(nominalPct.movePointLeft(2)), i = BigDecimal.ONE.add(inflationPct.movePointLeft(2));
        return scale(n.divide(i, MC).subtract(BigDecimal.ONE).movePointRight(2));
    }

    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
