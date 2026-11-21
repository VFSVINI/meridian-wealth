package dev.meridianwealth.application;

import dev.meridianwealth.application.port.CompositionPort;
import dev.meridianwealth.domain.composition.FundComposition;
import dev.meridianwealth.domain.composition.RealAsset;
import java.util.List;

/** Caso de uso: Raio-X de composição dos fundos e os imóveis com coordenada (base do mapa). */
public class CompositionService {
    /** Imóvel com coordenada, junto do fundo a que pertence. */
    public record GeoAsset(FundComposition fund, RealAsset asset) {}

    private final CompositionPort port;

    public CompositionService(CompositionPort port) { this.port = port; }

    public List<FundComposition> all() { return port.all(); }

    public FundComposition composition(String symbol) {
        return port.find(symbol).orElseThrow(() -> new CompositionUnavailableException(symbol));
    }

    /** Só imóveis com coordenada validada; os demais ficam fora do mapa, com o motivo na composição. */
    public List<GeoAsset> geolocated() {
        return port.all().stream()
                .flatMap(f -> f.assets().stream().filter(RealAsset::geolocated).map(a -> new GeoAsset(f, a))).toList();
    }
}
