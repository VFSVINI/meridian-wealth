package dev.meridianwealth.infrastructure.bcb;

import dev.meridianwealth.application.port.BrazilRatesPort;
import dev.meridianwealth.domain.BrazilRates;
import dev.meridianwealth.domain.RateObservation;
import dev.meridianwealth.domain.RateSeries;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém o último retrato dos indicadores do BCB. Atualiza a cada 30 minutos; cada indicador é buscado
 * em separado, e o que falhar mantém o último valor bom (nunca é estimado nem copiado de outro). Um valor
 * mais antigo do que o já guardado é ignorado, para a série não andar para trás.
 */
@Component
public class LiveBrazilRates implements BrazilRatesPort {
    private static final Logger log = LoggerFactory.getLogger(LiveBrazilRates.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    /** Janelas de consulta: cobrem fins de semana e feriados (diárias), a publicação mensal e o PTAX. */
    static final int DAILY_WINDOW_DAYS = 45;
    static final int MONTHLY_WINDOW_DAYS = 400;
    static final int PTAX_LOOKBACK_DAYS = 10;

    private final BcbClient client;
    private final boolean enabled;
    private final Clock clock;
    private volatile BrazilRates current = BrazilRates.unavailable();

    @Autowired
    public LiveBrazilRates(BcbClient client, @Value("${meridianwealth.bcb.enabled:true}") boolean enabled) {
        this(client, enabled, Clock.system(BRASILIA));
    }

    LiveBrazilRates(BcbClient client, boolean enabled, Clock clock) {
        this.client = client;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Override
    public BrazilRates current() { return current; }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 5_000)
    public void refresh() {
        if (!enabled) return;
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        BrazilRates next = current;
        for (RateSeries series : RateSeries.values()) {
            if (series.sgsCode() == null) continue;
            int window = series.periodicity() == RateSeries.Periodicity.MONTHLY ? MONTHLY_WINDOW_DAYS : DAILY_WINDOW_DAYS;
            try {
                List<BcbClient.Point> points = client.series(series.sgsCode(), today.minusDays(window), today);
                BcbClient.Point last = points.get(points.size() - 1);
                next = merge(next, new RateObservation(series, last.value(), last.date()), now);
            } catch (RuntimeException e) {
                log.warn("BCB: série {} ({}) não atualizada ({}); mantendo o último valor", series.sgsCode(),
                        series.label(), e.getMessage());
            }
        }
        try {
            BcbClient.Ptax ptax = client.latestPtax(today, PTAX_LOOKBACK_DAYS);
            LocalDate date = ptax.time().toLocalDate();
            next = merge(next, new RateObservation(RateSeries.PTAX_BUY, ptax.buy(), date), now);
            next = merge(next, new RateObservation(RateSeries.PTAX_SELL, ptax.sell(), date), now);
        } catch (RuntimeException e) {
            log.warn("BCB: PTAX não atualizado ({}); mantendo o último valor", e.getMessage());
        }
        current = next;
    }

    private static BrazilRates merge(BrazilRates rates, RateObservation incoming, Instant now) {
        Optional<RateObservation> existing = rates.get(incoming.series());
        if (existing.isPresent() && existing.get().date().isAfter(incoming.date())) return rates;
        return rates.with(incoming.series(), incoming, now);
    }
}
