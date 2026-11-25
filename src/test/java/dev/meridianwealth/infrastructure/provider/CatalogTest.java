package dev.meridianwealth.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.Exchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Catálogo de ativos negociados (item #33): cobertura, busca sem acento, prioridade do universo curado e nenhum preço no arquivo. */
class CatalogTest {

    @Test
    void theCatalogCoversTheListedMarketsAndIncludesAssetsTheCuratedUniverseNeverHad() {
        assertThat(Catalog.size()).isGreaterThan(10_000);
        var xp = Catalog.find("XPSF11").orElseThrow();       // o ativo que o dono notou faltando
        assertThat(xp.instrument().assetClass()).isEqualTo("FII");
        assertThat(xp.instrument().exchange()).isEqualTo(Exchange.B3);
        assertThat(xp.symbols().yahoo()).isEqualTo("XPSF11.SA");
        assertThat(xp.symbols().brapi()).isEqualTo("XPSF11");
        assertThat(Catalog.find("xpsf11")).as("símbolo sem diferenciar maiúscula").isPresent();
        assertThat(Catalog.find("BRK-B").orElseThrow().symbols().finnhub()).as("a Finnhub usa ponto na classe B").isEqualTo("BRK.B");
        assertThat(Catalog.find("NAOEXISTE")).isEmpty();
    }

    @Test
    void everyRowBecomesAnInstrumentTheTaxonomyUnderstands() {
        for (var e : Catalog.search("a", 40)) assertThat(e.instrument().type()).isNotNull();
        for (String s : List.of("XPSF11", "PETR4", "AAPL34", "SMAL11", "AAPL", "QQQ")) {
            assertThat(Catalog.find(s).orElseThrow().instrument().type()).as(s).isNotNull();
        }
    }

    @Test
    void searchIgnoresAccentsAndCaseAndRanksExactThenPrefixThenName() {
        assertThat(Catalog.search("XPSF11", 5).get(0).instrument().symbol()).isEqualTo("XPSF11");
        assertThat(Catalog.search("xpsf", 5).get(0).instrument().symbol()).isEqualTo("XPSF11");
        assertThat(Catalog.search("petroleo brasileiro", 10)).extracting(e -> e.instrument().symbol()).contains("PETR4");
        assertThat(Catalog.search("PETRÓLEO BRASILEIRO", 10)).extracting(e -> e.instrument().symbol()).contains("PETR4");
        assertThat(Catalog.search("", 10)).isEmpty();
        assertThat(Catalog.search("zzzzzzzzzzzz", 10)).isEmpty();
        assertThat(Catalog.search("a", 7)).hasSize(7);
    }

    @Test
    void aB3NameIsNotHiddenByUsSymbolsThatHappenToMatch() {
        assertThat(Catalog.search("vale", 20)).extracting(e -> e.instrument().symbol()).contains("VALE3");
    }

    @Test
    void theCuratedUniverseWinsOverTheCatalogWhenASymbolExistsInBoth() {
        // BHP existe no catálogo como ADR dos EUA, mas no universo curado é a ação australiana: o curado tem prioridade
        assertThat(Catalog.find("BHP")).isPresent();
        var entry = Universe.resolve("BHP").orElseThrow();
        assertThat(entry.instrument().exchange()).isEqualTo(Exchange.ASX);
        assertThat(Universe.curated("BHP")).isTrue();
        assertThat(Universe.curated("XPSF11")).isFalse();
        assertThat(Universe.resolve("XPSF11")).isPresent();
        assertThat(Universe.resolve("NAOEXISTE")).isEmpty();
    }

    @Test
    void theCatalogFileHoldsRegistryDataOnlyNeverAPrice() throws IOException {
        List<String> lines;
        try (var s = Files.lines(Path.of("src/main/resources/catalog/assets.tsv"), StandardCharsets.UTF_8)) {
            lines = s.filter(l -> !l.startsWith("#") && !l.isBlank()).toList();
        }
        assertThat(lines).hasSize(Catalog.size());
        assertThat(lines).allSatisfy(l -> assertThat(l.split("\t", -1)).hasSize(6));
        assertThat(lines.stream().map(l -> l.split("\t")[0]).distinct().count()).as("símbolos únicos").isEqualTo(lines.size());
    }
}
