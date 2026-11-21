package dev.chronosquant.web;

import dev.chronosquant.application.MarketService;
import dev.chronosquant.application.port.CacheStore;
import dev.chronosquant.infrastructure.provider.ProviderInfo;
import dev.chronosquant.infrastructure.resilience.ProviderStats;
import dev.chronosquant.infrastructure.stream.StreamHub;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Status da ingestão: provedores (circuit breaker, rate limit), cache, persistência e conexões ao vivo. */
@RestController
@RequestMapping("/api")
public class StatusController {
    public record Status(String dataMode, boolean flow, String cache, boolean persistence, int sseClients,
                         int wsClients, List<ProviderStats> providers, List<MarketService.SourceView> instruments) {}

    private final List<ProviderInfo> providers;
    private final MarketService market;
    private final CacheStore cache;
    private final StreamHub hub;
    private final boolean persistence;
    private final String mode;

    public StatusController(List<ProviderInfo> providers, MarketService market, CacheStore cache, StreamHub hub,
                            @Value("${chronosquant.persistence.enabled:false}") boolean persistence,
                            @Value("${chronosquant.data.mode:auto}") String mode) {
        this.providers = providers;
        this.market = market;
        this.cache = cache;
        this.hub = hub;
        this.persistence = persistence;
        this.mode = mode;
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(mode, market.settings().flow(), cache.kind(), persistence, hub.sseClients(), hub.wsClients(),
                providers.stream().map(ProviderInfo::stats).toList(), market.sources());
    }
}
