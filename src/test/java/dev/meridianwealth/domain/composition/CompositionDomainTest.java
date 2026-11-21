package dev.meridianwealth.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompositionDomainTest {
    private static final SourceRef SOURCE = new SourceRef("Informe", "https://exemplo.gov.br/doc", SourceKind.OFFICIAL,
            "2026-09-30", LocalDate.parse("2026-11-21"), "abc");

    private static Coordinates coords(String lat, String lng) {
        return new Coordinates(new BigDecimal(lat), new BigDecimal(lng), GeoPrecision.ADDRESS, "Rua X, São Paulo",
                "OSM_NOMINATIM", "way/1", LocalDate.parse("2026-11-21"));
    }

    private static RealAsset asset(String id, String area, Coordinates c) {
        return new RealAsset(id, "XXXX11", id, "Rua X, 1", "São Paulo", "SP", null, new BigDecimal(area), area, null, null,
                null, c, c == null ? "sem endereço" : null);
    }

    @Test void googleMapsLinksFollowTheDocumentedFormats() {
        Coordinates c = coords("-23.567890", "-46.693000");
        assertThat(MapLinks.satellite(c)).isEqualTo(
                "https://www.google.com/maps/@?api=1&map_action=map&center=-23.567890%2C-46.693000&zoom=17&basemap=satellite");
        assertThat(MapLinks.streetView(c)).isEqualTo(
                "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=-23.567890%2C-46.693000");
    }

    @Test void coordinatesOutsideBrazilAreRejected() {
        assertThatThrownBy(() -> coords("48.8566", "2.3522")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora do Brasil");
        assertThatThrownBy(() -> coords("-23.5", "46.6")).as("sinal da longitude trocado").isInstanceOf(IllegalArgumentException.class);
        assertThat(coords("-23.5", "-46.6").precision()).isEqualTo(GeoPrecision.ADDRESS);
    }

    @Test void anAssetWithoutCoordinatesMustSayWhyAndAreaMustBePositive() {
        assertThatThrownBy(() -> new RealAsset("a", "X", "A", null, null, null, null, BigDecimal.TEN, "10", null, null, null, null, " "))
                .hasMessageContaining("por quê");
        assertThatThrownBy(() -> asset("a", "0", null)).hasMessageContaining("positiva");
        assertThat(asset("a", "10", null).geolocated()).isFalse();
        assertThat(asset("a", "10", coords("-23.5", "-46.6")).geolocated()).isTrue();
    }

    @Test void areaSharesUseTheListedAreaAndSumToAboutOneHundredPercent() {
        RealAsset a = asset("a", "62587.38", null);
        RealAsset b = asset("b", "90484.02", null);
        RealAsset c = asset("c", "27946.44", null);
        FundComposition f = new FundComposition("XXXX11", "Fundo", null, SOURCE, "Área (m2)", 3, null, null, List.of(a, b, c),
                List.of(), Map.of(), List.of());
        assertThat(f.listedAreaM2()).isEqualByComparingTo("181017.84");
        assertThat(f.areaSharePercent(a)).isEqualByComparingTo("34.58");
        BigDecimal soma = f.areaSharePercent(a).add(f.areaSharePercent(b)).add(f.areaSharePercent(c));
        assertThat(soma).isBetween(new BigDecimal("99.98"), new BigDecimal("100.02"));
        assertThat(f.geolocatedCount()).isZero();
    }

    @Test void duplicateAssetsAndInsecureSourcesAreRefused() {
        assertThatThrownBy(() -> new FundComposition("X", "X", null, SOURCE, null, null, null, null,
                List.of(asset("a", "1", null), asset("a", "2", null)), List.of(), Map.of(), List.of()))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> new SourceRef("x", "http://inseguro.com", SourceKind.THIRD_PARTY, null, LocalDate.now(), null))
                .hasMessageContaining("https");
        assertThat(SourceKind.OFFICIAL.label()).isEqualTo("Fonte oficial");
    }
}
