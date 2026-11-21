package dev.meridianwealth.infrastructure.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.domain.composition.FundComposition;
import dev.meridianwealth.domain.composition.GeoPrecision;
import dev.meridianwealth.domain.composition.MapLinks;
import dev.meridianwealth.domain.composition.RealAsset;
import dev.meridianwealth.domain.composition.SourceKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Invariantes dos dados curados que vão para produção: um arquivo quebrado derruba o teste, não o usuário. */
class JsonCompositionRepositoryTest {
    private final JsonCompositionRepository repo = new JsonCompositionRepository();

    @Test void loadsTheFourCuratedFunds() {
        assertThat(repo.all()).extracting(FundComposition::fundSymbol).containsExactly("HGLG11", "KNRI11", "MXRF11", "XPML11");
    }

    @Test void everyFundCitesAnHttpsSourceWithConsultationDate() {
        for (FundComposition f : repo.all()) {
            assertThat(f.source().url()).startsWith("https://");
            assertThat(f.source().consultedOn()).isNotNull();
        }
        assertThat(repo.find("XPML11").orElseThrow().source().kind()).isEqualTo(SourceKind.THIRD_PARTY);
        assertThat(repo.find("HGLG11").orElseThrow().source().kind()).isEqualTo(SourceKind.OFFICIAL);
    }

    @Test void coordinatesAlwaysCarryProofAndLinks() {
        long geolocated = 0;
        for (FundComposition f : repo.all()) {
            for (RealAsset a : f.assets()) {
                if (a.coordinates() == null) {
                    assertThat(a.noCoordinatesReason()).as(a.name()).isNotBlank();
                    continue;
                }
                geolocated++;
                assertThat(a.coordinates().matchedAddress()).isNotBlank();
                assertThat(a.coordinates().geocodedOn()).isNotNull();
                assertThat(MapLinks.satellite(a.coordinates())).contains("basemap=satellite");
                assertThat(MapLinks.streetView(a.coordinates())).contains("map_action=pano");
            }
        }
        assertThat(geolocated).isEqualTo(32);
    }

    @Test void geolocatedAssetsAreInsideTheirPublishedStateBox() {
        // Caixa aproximada por UF (graus): pega coordenada em outro estado sem depender de internet.
        Map<String, double[]> box = Map.of(
                "SP", new double[] {-25.4, -19.7, -53.2, -44.1}, "RJ", new double[] {-23.4, -20.7, -44.9, -40.9},
                "MG", new double[] {-23.0, -14.2, -51.1, -39.8}, "PR", new double[] {-26.8, -22.5, -54.7, -48.0},
                "RS", new double[] {-33.8, -27.0, -57.7, -49.6}, "BA", new double[] {-18.4, -8.5, -46.7, -37.3},
                "GO", new double[] {-19.6, -12.3, -53.3, -45.8}, "SC", new double[] {-29.4, -25.9, -53.9, -48.3},
                "PE", new double[] {-9.6, -7.2, -41.4, -34.7}, "DF", new double[] {-16.1, -15.4, -48.3, -47.2});
        for (FundComposition f : repo.all()) {
            for (RealAsset a : f.assets()) {
                if (a.coordinates() == null || a.uf() == null || !box.containsKey(a.uf())) continue;
                double[] b = box.get(a.uf());
                double lat = a.coordinates().lat().doubleValue();
                double lng = a.coordinates().lng().doubleValue();
                assertThat(lat).as(a.name() + " lat").isBetween(b[0], b[1]);
                assertThat(lng).as(a.name() + " lng").isBetween(b[2], b[3]);
            }
        }
    }

    @Test void streetPrecisionIsKeptSoTheUiCanSayApproximate() {
        long street = repo.all().stream().flatMap(f -> f.assets().stream()).filter(a -> a.coordinates() != null)
                .filter(a -> a.coordinates().precision() == GeoPrecision.STREET).count();
        assertThat(street).isEqualTo(24);
    }

    @Test void hglgDoesNotPresentPartialRevenueAsFullAndDropsDashSectors() {
        FundComposition f = repo.find("HGLG11").orElseThrow();
        BigDecimal sum = f.assets().stream().map(RealAsset::revenueSharePercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isLessThan(new BigDecimal("100"));
        assertThat(f.notices()).anyMatch(n -> n.contains("diferença para 100%"));
        assertThat(f.assets()).allSatisfy(a -> assertThat(a.tenantSectors()).noneMatch(s -> s.sector().contains("-")
                && s.sector().length() <= 3));
    }

    @Test void knriDeclaresMoreAssetsThanItListsAndSaysSo() {
        FundComposition f = repo.find("KNRI11").orElseThrow();
        assertThat(f.declaredAssetCount()).isEqualTo(20);
        assertThat(f.assets()).hasSize(17);
    }

    @Test void mxrfIsAllocationOnlyAndSumsToOneHundred() {
        FundComposition f = repo.find("MXRF11").orElseThrow();
        assertThat(f.assets()).isEmpty();
        assertThat(f.allocations().stream().map(a -> a.percentOfAssets()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
    }

    @Test void areaShareIsRoundedOnceToTwoPlacesAndTheTotalOverloadAgrees() {
        for (FundComposition f : repo.all()) {
            BigDecimal total = f.listedAreaM2();
            for (RealAsset a : f.assets()) {
                assertThat(f.areaSharePercent(a, total)).isEqualTo(f.areaSharePercent(a));
                assertThat(f.areaSharePercent(a).scale()).isEqualTo(2);
            }
        }
    }

    @Test void unknownFundIsEmptyAndDuplicatesAreRejected() {
        assertThat(repo.find("PETR4")).isEmpty();
        List<FundComposition> two = List.of(repo.find("HGLG11").orElseThrow(), repo.find("HGLG11").orElseThrow());
        assertThatThrownBy(() -> new JsonCompositionRepository(two)).hasMessageContaining("repetido");
    }

    @Test void brokenFilesFailLoudlyWithTheirName() throws Exception {
        var bad = new ObjectMapper().readTree("{\"versaoDoEsquema\":1,\"fundo\":\"X11\",\"nomeDoFundo\":\"X\","
                + "\"fonte\":{\"nome\":\"n\",\"url\":\"http://inseguro\",\"tipo\":\"OFICIAL\",\"consultadoEm\":\"2026-11-21\"}}");
        assertThatThrownBy(() -> JsonCompositionRepository.parse(bad, "x11.json")).hasMessageContaining("x11.json");
    }
}
