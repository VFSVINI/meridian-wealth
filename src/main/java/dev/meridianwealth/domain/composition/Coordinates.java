package dev.meridianwealth.domain.composition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Coordenada de um imóvel obtida por geocodificação do endereço publicado (OpenStreetMap Nominatim, dados
 * © colaboradores do OpenStreetMap, licença ODbL), com a prova de como foi achada. Valores em {@link BigDecimal}
 * exatos como gravados na curadoria.
 */
public record Coordinates(BigDecimal lat, BigDecimal lng, GeoPrecision precision, String matchedAddress,
                          String method, String osmRef, LocalDate geocodedOn) {
    private static final BigDecimal LAT_MIN = new BigDecimal("-34.0");
    private static final BigDecimal LAT_MAX = new BigDecimal("6.0");
    private static final BigDecimal LNG_MIN = new BigDecimal("-74.5");
    private static final BigDecimal LNG_MAX = new BigDecimal("-34.0");

    public Coordinates {
        Objects.requireNonNull(lat, "lat");
        Objects.requireNonNull(lng, "lng");
        Objects.requireNonNull(precision, "precision");
        Objects.requireNonNull(matchedAddress, "matchedAddress");
        if (lat.compareTo(LAT_MIN) < 0 || lat.compareTo(LAT_MAX) > 0 || lng.compareTo(LNG_MIN) < 0 || lng.compareTo(LNG_MAX) > 0) {
            throw new IllegalArgumentException("Coordenada fora do Brasil: " + lat + ", " + lng);
        }
    }
}
