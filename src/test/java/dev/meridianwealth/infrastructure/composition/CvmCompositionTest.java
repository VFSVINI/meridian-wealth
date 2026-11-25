package dev.meridianwealth.infrastructure.composition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.composition.FundComposition;
import dev.meridianwealth.domain.composition.RealAsset;
import dev.meridianwealth.domain.composition.SourceKind;
import dev.meridianwealth.infrastructure.provider.Catalog;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Composição de todos os FIIs com imóveis na CVM (item #34): fonte oficial, ligação ticker-CNPJ conferida e nada inventado. */
class CvmCompositionTest {
    private static final Set<String> CURATED = Set.of("HGLG11", "KNRI11", "MXRF11", "XPML11");
    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private final JsonCompositionRepository repo = new JsonCompositionRepository();

    private List<FundComposition> cvm() {
        return repo.all().stream().filter(f -> !CURATED.contains(f.fundSymbol())).toList();
    }

    @Test void coversManyListedFundsFromTheOfficialCvmDatasetBesideTheFourCuratedOnes() {
        assertThat(cvm().size()).as("fundos vindos da CVM").isGreaterThan(100);
        assertThat(repo.all()).extracting(FundComposition::fundSymbol).contains("HGLG11", "KNRI11", "MXRF11", "XPML11", "XPLG11", "VISC11");
        assertThat(cvm().stream().mapToInt(f -> f.assets().size()).sum()).as("imóveis").isGreaterThan(900);
    }

    @Test void everyCvmFundIsAnOfficialSourceWithDateHashAndAHttpsCvmUrl() {
        for (FundComposition f : cvm()) {
            assertThat(f.source().kind()).as(f.fundSymbol()).isEqualTo(SourceKind.OFFICIAL);
            assertThat(f.source().url()).startsWith("https://dados.cvm.gov.br/dados/FII/DOC/INF_TRIMESTRAL/");
            assertThat(LocalDate.parse(f.source().referenceDate())).isAfter(LocalDate.of(2025, 1, 1));
            assertThat(f.source().evidenceSha256()).matches("[0-9a-f]{64}");
            assertThat(f.cnpj()).matches("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
            assertThat(f.notices()).anyMatch(n -> n.contains("ISIN")).anyMatch(n -> n.contains("não publica o tipo do imóvel"));
        }
    }

    @Test void everyCvmFundIsAListedFiiInTheCatalogSoTheTickerLinkIsNeverAGuess() {
        for (FundComposition f : cvm()) {
            var entry = Catalog.find(f.fundSymbol());
            assertThat(entry).as(f.fundSymbol() + " existe no catálogo").isPresent();
            assertThat(entry.get().instrument().assetClass()).isEqualTo("FII");
        }
        Set<String> cnpjs = new HashSet<>();
        for (FundComposition f : repo.all()) if (f.cnpj() != null) assertThat(cnpjs.add(f.cnpj())).as("CNPJ único: " + f.cnpj()).isTrue();
    }

    @Test void nothingIsInventedPropertyTypeAndCoordinatesAreOnlyWhatTheSourceAndTheGeocoderProved() {
        Set<String> ids = new HashSet<>();
        for (FundComposition f : cvm()) {
            for (RealAsset a : f.assets()) {
                assertThat(ids.add(a.id())).as("id único " + a.id()).isTrue();
                assertThat(a.kind()).as("a CVM não publica o tipo do imóvel").isNull();
                assertThat(a.areaM2().signum()).isPositive();
                if (a.uf() != null) assertThat(UFS).as(a.name() + " UF").contains(a.uf());
                if (a.coordinates() == null) assertThat(a.noCoordinatesReason()).as(a.name()).isNotBlank();
                if (a.vacancyPercent() != null) assertThat(a.vacancyPercent().doubleValue()).isBetween(0.0, 100.0001);
            }
        }
    }

    @Test void aLargeFundKeepsAllItsPropertiesAndTheirSectors() {
        FundComposition xplg = repo.find("XPLG11").orElseThrow();
        assertThat(xplg.assets().size()).isGreaterThan(10);
        assertThat(xplg.assets()).anyMatch(a -> !a.tenantSectors().isEmpty());
    }

    @Test void sectorsAreNeverMultipliedByRepeatedPropertyNamesAndInconsistentSourceSumsAreFlagged() {
        // medido: nomes repetidos recebiam os setores de todas as linhas (FLMA11 chegava a 1.600%); agora ficam sem setores, com aviso
        for (FundComposition f : cvm()) {
            boolean over100 = false;
            java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;
            for (RealAsset a : f.assets()) {
                java.math.BigDecimal sectors = a.tenantSectors().stream().map(s -> s.percentOfPropertyRevenue()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                if (sectors.compareTo(new java.math.BigDecimal("100.5")) > 0) over100 = true;
                assertThat(sectors.doubleValue()).as(f.fundSymbol() + " " + a.name()).isLessThan(110.0);
                if (a.revenueSharePercent() != null) revenue = revenue.add(a.revenueSharePercent());
            }
            if (over100) assertThat(f.notices()).as(f.fundSymbol()).anyMatch(n -> n.contains("setores publicados somam mais de 100%"));
            if (revenue.compareTo(new java.math.BigDecimal("100.5")) > 0) assertThat(f.notices()).as(f.fundSymbol()).anyMatch(n -> n.contains("acima de 100%"));
        }
        assertThat(repo.find("FLMA11").orElseThrow().notices()).anyMatch(n -> n.contains("repetem o nome de um imóvel"));
    }
}
