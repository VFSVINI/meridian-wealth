package dev.meridianwealth.web;

import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.infrastructure.provider.ProviderInfo;
import dev.meridianwealth.infrastructure.resilience.ProviderStats;
import dev.meridianwealth.infrastructure.stream.StreamHub;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Status da ingestão: provedores (circuit breaker, rate limit), cache, persistência e conexões ao vivo. */
@RestController
@RequestMapping("/api")
public class StatusController {
    public record Status(String cache, boolean persistence, int sseClients,
                         int wsClients, List<ProviderStats> providers, List<MarketService.SourceView> instruments) {}

    private final List<ProviderInfo> providers;
    private final MarketService market;
    private final CacheStore cache;
    private final StreamHub hub;
    private final boolean persistence;

    public StatusController(List<ProviderInfo> providers, MarketService market, CacheStore cache, StreamHub hub,
                            @Value("${meridianwealth.persistence.enabled:false}") boolean persistence) {
        this.providers = providers;
        this.market = market;
        this.cache = cache;
        this.hub = hub;
        this.persistence = persistence;
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(cache.kind(), persistence, hub.sseClients(), hub.wsClients(),
                providers.stream().map(ProviderInfo::stats).toList(), market.sources());
    }
}
