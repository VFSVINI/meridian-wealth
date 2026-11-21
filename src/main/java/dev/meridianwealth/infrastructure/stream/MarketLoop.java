package dev.meridianwealth.infrastructure.stream;

import dev.meridianwealth.application.MarketService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketLoop {
    private final MarketService market;
    private final StreamHub hub;

    public MarketLoop(MarketService market, StreamHub hub) {
        this.market = market;
        this.hub = hub;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        hub.publish(market.advance());
    }
}
