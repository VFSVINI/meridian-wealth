package dev.meridianwealth.infrastructure.observability.health;

import dev.meridianwealth.application.DataStatus;
import dev.meridianwealth.application.MarketService;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Quantos ativos têm dado real agora. {@code UP} se todos; {@code DEGRADED} se algum está sem cotação ao vivo. */
@Component("marketData")
public class MarketDataHealthIndicator implements HealthIndicator {
    private final MarketService market;

    public MarketDataHealthIndicator(MarketService market) { this.market = market; }

    @Override
    public Health health() {
        Map<DataStatus, Integer> counts = new EnumMap<>(DataStatus.class);
        for (MarketService.SourceView v : market.sources()) counts.merge(v.status(), 1, Integer::sum);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int live = counts.getOrDefault(DataStatus.LIVE, 0);
        Health.Builder b = live == total && total > 0 ? Health.up() : Health.status(Degraded.STATUS);
        return b.withDetail("assets", total).withDetail("live", live).withDetail("stored", counts.getOrDefault(DataStatus.STORED, 0))
                .withDetail("insufficient", counts.getOrDefault(DataStatus.INSUFFICIENT, 0))
                .withDetail("unavailable", counts.getOrDefault(DataStatus.UNAVAILABLE, 0)).build();
    }
}
