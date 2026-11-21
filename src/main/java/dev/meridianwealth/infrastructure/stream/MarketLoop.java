package dev.meridianwealth.infrastructure.stream;

import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.StreamService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agendador (adaptador de entrada): dispara os casos de uso de stream e de nova tentativa de histórico. */
@Component
public class MarketLoop {
    private final StreamService stream;
    private final MarketService market;

    public MarketLoop(StreamService stream, MarketService market) {
        this.stream = stream;
        this.market = market;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() { stream.publishNext(); }

    /** Ativos sem dado real na partida (provedor fora do ar, rate limit) são tentados de novo. */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void retryMissing() { market.retryMissing(); }
}
