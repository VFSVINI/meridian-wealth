package dev.meridianwealth.application;

import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.application.port.ProviderStatusSource;
import java.util.List;

/** Caso de uso de status da ingestão: provedores, cache, persistência, canais e procedência por ativo. */
public class StatusService {
    public record Status(String cache, boolean persistence, List<StreamService.Channel> channels,
                         List<ProviderStatus> providers, List<MarketService.SourceView> instruments) {}

    private final CacheStore cache;
    private final boolean persistence;
    private final StreamService stream;
    private final List<ProviderStatusSource> providers;
    private final MarketService market;

    public StatusService(CacheStore cache, boolean persistence, StreamService stream,
                         List<ProviderStatusSource> providers, MarketService market) {
        this.cache = cache;
        this.persistence = persistence;
        this.stream = stream;
        this.providers = List.copyOf(providers);
        this.market = market;
    }

    public Status status() {
        return new Status(cache.kind(), persistence, stream.channels(),
                providers.stream().map(ProviderStatusSource::status).toList(), market.sources());
    }
}
