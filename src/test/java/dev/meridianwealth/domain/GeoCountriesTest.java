package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Radar geopolítico por país (item #51): ligação manchete-país conferida, risco só de quem foi citado e fronteiras com procedência fixada. */
class GeoCountriesTest {

    @Test
    void headlinesLinkToTheCountriesTheyNameAndNotToWordsThatMerelyContainTheName() {
        assertThat(GeoRegions.match("Russia launches missile strike on Kyiv")).containsExactly("RUS", "UKR");
        assertThat(GeoRegions.match("Nigeria raises rates as inflation bites")).as("Nigéria não é Níger").containsExactly("NGA");
        assertThat(GeoRegions.match("Niger junta expels envoy")).containsExactly("NER");
        assertThat(GeoRegions.match("Why the US and us matter")).as("o pronome 'us' não é um país").isEmpty();
        assertThat(GeoRegions.match("UK inflation falls; BoE holds")).containsExactly("GBR");
        assertThat(GeoRegions.match("Trump tariffs hit China and Brazil")).containsExactly("USA", "BRA", "CHN");
        assertThat(GeoRegions.match("Gaza ceasefire talks resume in Cairo")).containsExactlyInAnyOrder("EGY", "PSX");
        assertThat(GeoRegions.match("Latin America braces for slower growth")).as("América Latina não é Estados Unidos").isEmpty();
        assertThat(GeoRegions.match("Clashes in South Sudan displace thousands")).as("Sudão do Sul não é Sudão").containsExactly("SDS");
        assertThat(GeoRegions.match("Sudan and South Sudan agree on oil pipeline")).containsExactlyInAnyOrder("SDN", "SDS");
        assertThat(GeoRegions.match("Markets rally after jobs report")).isEmpty();
    }

    @Test
    void everyCountryCodeExistsInTheBorderFileAndEachCodeAppearsOnce() throws IOException {
        JsonNode fc = new ObjectMapper().readTree(StaticPage.file("data/countries.geojson"));
        Set<String> inFile = new HashSet<>();
        fc.get("features").forEach(f -> inFile.add(f.get("properties").get("iso").asText()));
        Set<String> seen = new HashSet<>();
        for (GeoRegions.Country c : GeoRegions.all()) {
            assertThat(inFile).as(c.iso3() + " existe no arquivo de fronteiras").contains(c.iso3());
            assertThat(seen.add(c.iso3())).as("código repetido " + c.iso3()).isTrue();
            assertThat(c.region()).isIn("Estados Unidos", "Brasil", "Américas", "Europa", "Leste Europeu", "Oriente Médio", "África", "Ásia-Pacífico");
        }
    }

    @Test
    void theBorderFileIsTheNaturalEarthPublicDomainDataReducedAndFingerprinted() throws Exception {
        String raw = StaticPage.file("data/countries.geojson");
        String sha = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(sha).isEqualTo("c8481046cf3d3f56733b3c5cc7532ad091c68c854ff1ab137252f80680584993");
        JsonNode fc = new ObjectMapper().readTree(raw);
        assertThat(fc.get("features")).hasSize(176);
        fc.get("features").forEach(f -> assertThat(f.get("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("iso", "name"));
        assertThat(raw).as("sem Antártida").doesNotContain("\"iso\":\"ATA\"");
        assertThat(StaticPage.file("data/NOTICE-natural-earth.txt")).contains("dominio publico").contains(sha);
    }

    @Test
    void countryRiskUsesEveryScoredHeadlineSumsOverTwoStrongOnesAndOmitsCountriesNobodyMentioned() {
        NewsItem a = GeoRiskScorer.score("Russia escalates war with missile strike on Ukraine", "BBC");
        NewsItem b = GeoRiskScorer.score("Ukraine hit by new airstrike as conflict grows", "BBC");
        NewsItem calm = GeoRiskScorer.score("Brazil central bank holds rates", "Guardian");
        List<CountryRisk> out = GeoRiskScorer.countryRisks(List.of(a, b, calm));
        assertThat(out).extracting(CountryRisk::iso3).contains("UKR", "RUS", "BRA").doesNotContain("CHN", "USA");
        CountryRisk ukr = out.stream().filter(c -> c.iso3().equals("UKR")).findFirst().orElseThrow();
        assertThat(ukr.headlines()).isEqualTo(2);
        assertThat(ukr.risk()).as("duas manchetes fortes saturam o risco em 1").isEqualTo(1.0);
        assertThat(ukr.sectors()).contains("energia", "defesa");
        assertThat(ukr.top().risk()).as("a manchete principal é a de maior risco").isGreaterThanOrEqualTo(Math.min(a.risk(), b.risk()));
        CountryRisk bra = out.stream().filter(c -> c.iso3().equals("BRA")).findFirst().orElseThrow();
        assertThat(bra.headlines()).isEqualTo(1);
        assertThat(bra.risk()).as("citado mas sem palavra de risco: baixo, e não 'não informado'").isLessThan(0.25);
        assertThat(out.get(0).risk()).as("do mais para o menos arriscado").isGreaterThanOrEqualTo(out.get(out.size() - 1).risk());
    }

    @Test
    void onlyHttpAndHttpsLinksSurviveAndTheTimeAndLinkTravelWithTheHeadline() {
        Instant t = Instant.parse("2026-11-27T10:00:00Z");
        NewsItem ok = GeoRiskScorer.score("Oil jumps on Iran sanctions", "BBC", "https://www.bbc.com/news/1", t);
        assertThat(ok.link()).isEqualTo("https://www.bbc.com/news/1");
        assertThat(ok.publishedAt()).isEqualTo(t);
        assertThat(ok.countries()).containsExactly("IRN");
        assertThat(GeoRiskScorer.score("x", "s", "javascript:alert(1)", null).link()).isNull();
        assertThat(GeoRiskScorer.score("x", "s", "data:text/html,<b>", null).link()).isNull();
        assertThat(GeoRiskScorer.score("x", "s", "ftp://exemplo.org/a", null).link()).isNull();
        assertThat(GeoRiskScorer.score("x", "s", "https://exemplo.org/" + "a".repeat(2100), null).link()).as("link absurdamente longo").isNull();
        assertThat(GeoRiskScorer.score("x", "s").link()).isNull();
    }
}
