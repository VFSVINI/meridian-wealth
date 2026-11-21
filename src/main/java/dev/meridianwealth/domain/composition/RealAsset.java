package dev.meridianwealth.domain.composition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Imóvel real de um fundo, só com o que a fonte publicou. Campo que a fonte não traz fica {@code null} (ou
 * lista vazia): nada é estimado. {@code publishedArea} guarda o texto exato da fonte ao lado do valor lido.
 */
public record RealAsset(String id, String fundSymbol, String name, String publishedAddress, String city, String uf,
                        PropertyKind kind, BigDecimal areaM2, String publishedArea, BigDecimal vacancyPercent,
                        BigDecimal revenueSharePercent, List<TenantSector> tenantSectors, Coordinates coordinates,
                        String noCoordinatesReason) {
    public RealAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(areaM2, "areaM2");
        if (areaM2.signum() <= 0) throw new IllegalArgumentException("A área de " + name + " deve ser positiva");
        tenantSectors = tenantSectors == null ? List.of() : List.copyOf(tenantSectors);
        if (coordinates == null && (noCoordinatesReason == null || noCoordinatesReason.isBlank())) {
            throw new IllegalArgumentException("Imóvel sem coordenada precisa dizer por quê: " + name);
        }
    }

    public boolean geolocated() { return coordinates != null; }
}
