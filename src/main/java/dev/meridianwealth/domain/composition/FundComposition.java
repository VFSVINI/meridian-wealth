package dev.meridianwealth.domain.composition;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Raio-X de um fundo: os imóveis ou a alocação que a fonte publicou, com a fonte e os avisos do que não consta.
 * {@code declaredAssetCount} é o total de imóveis que a fonte declara (pode ser maior do que a lista).
 */
public record FundComposition(String fundSymbol, String fundName, String cnpj, SourceRef source, String areaLabel,
                              Integer declaredAssetCount, FundMetrics metrics, String classification,
                              List<RealAsset> assets, List<Allocation> allocations, Map<String, BigDecimal> totalsBrl,
                              List<String> notices) {
    public FundComposition {
        Objects.requireNonNull(fundSymbol, "fundSymbol");
        Objects.requireNonNull(source, "source");
        assets = List.copyOf(assets);
        allocations = List.copyOf(allocations);
        totalsBrl = totalsBrl == null ? Map.of() : Map.copyOf(totalsBrl);
        notices = List.copyOf(notices);
        Set<String> ids = new HashSet<>();
        for (RealAsset a : assets) {
            if (!ids.add(a.id())) throw new IllegalArgumentException("Imóvel repetido: " + a.id());
        }
    }

    /** Soma das áreas dos imóveis da lista (não é a ABL publicada do fundo). */
    public BigDecimal listedAreaM2() {
        return assets.stream().map(RealAsset::areaM2).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Participação do imóvel na área somada da lista, em %, com 2 casas (HALF_EVEN). */
    public BigDecimal areaSharePercent(RealAsset asset) { return areaSharePercent(asset, listedAreaM2()); }

    /** Igual a {@link #areaSharePercent(RealAsset)}, com o total já calculado (evita somar a lista a cada imóvel). */
    public BigDecimal areaSharePercent(RealAsset asset, BigDecimal listedTotalM2) {
        if (listedTotalM2.signum() == 0) return BigDecimal.ZERO;
        return asset.areaM2().multiply(BigDecimal.valueOf(100), MathContext.DECIMAL128)
                .divide(listedTotalM2, 2, RoundingMode.HALF_EVEN);
    }

    /** Os dados vêm de informes trimestrais (90 dias de período + 45 de prazo): passado disso, podem estar defasados. */
    public static final int STALE_AFTER_DAYS = 135;

    public dev.meridianwealth.domain.DataAge age(java.time.LocalDate today) {
        return dev.meridianwealth.domain.DataAge.of(source.consultedOn(), today, STALE_AFTER_DAYS);
    }

    public long geolocatedCount() { return assets.stream().filter(RealAsset::geolocated).count(); }
}
