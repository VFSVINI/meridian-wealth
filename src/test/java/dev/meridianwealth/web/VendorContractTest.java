package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Contrato da biblioteca de gráficos servida pelo projeto (item #15): Apache ECharts 5.5.1, sem CDN (a CSP só aceita o próprio servidor),
 * com a licença Apache 2.0 e o NOTICE ao lado. O SHA-256 trava a versão: trocar o arquivo exige trocar o valor aqui, de propósito.
 */
class VendorContractTest {
    static final String ECHARTS_SHA256 = "e84270bd0cd5bdf60fefc26d00c2a391cb2e81f4d26a7a9ee16185a54773a3cf";

    private static byte[] read(String path) throws IOException {
        try (InputStream in = VendorContractTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso ausente: " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test void echartsIsServedLocallyAtThePinnedVersion() throws Exception {
        byte[] js = read("/static/vendor/echarts.min.js");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(js))).isEqualTo(ECHARTS_SHA256);
        assertThat(new String(js, 0, 400, StandardCharsets.UTF_8)).contains("Apache Software Foundation").contains("Apache License");
    }

    @Test void theApacheLicenseAndNoticeTravelWithTheLibrary() throws IOException {
        assertThat(new String(read("/static/vendor/LICENSE-echarts.txt"), StandardCharsets.UTF_8)).contains("Apache License").contains("Version 2.0");
        assertThat(new String(read("/static/vendor/NOTICE-echarts.txt"), StandardCharsets.UTF_8)).contains("Apache ECharts").contains("Apache Software Foundation");
    }

    @Test void leafletAndTheStatesOutlineAreServedLocallyAtPinnedChecksums() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] js = read("/static/vendor/leaflet.js");
        assertThat(HexFormat.of().formatHex(sha.digest(js))).isEqualTo("db49d009c841f5ca34a888c96511ae936fd9f5533e90d8b2c4d57596f4e5641a");
        assertThat(new String(js, 0, 200, StandardCharsets.UTF_8)).contains("Leaflet 1.9.4");
        assertThat(HexFormat.of().formatHex(sha.digest(read("/static/vendor/leaflet.css")))).isEqualTo("a7837102824184820dfa198d1ebcd109ff6d0ff9a2672a074b9a1b4d147d04c6");
        assertThat(new String(read("/static/vendor/LICENSE-leaflet.txt"), StandardCharsets.UTF_8)).contains("BSD 2-Clause License").contains("Agafonkin");
        byte[] geo = read("/static/data/brasil-ufs.geojson");
        assertThat(HexFormat.of().formatHex(sha.digest(geo))).isEqualTo("07c671aa87aa1ab296c446de6294df9827112782537b996c0a160284907e7c0f");
    }

    @Test void theOutlineHasExactlyTheTwentySevenStatesOfIbgeAndEveryPublishedCoordinateFallsInsideBrazil() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode g = json.readTree(read("/static/data/brasil-ufs.geojson"));
        java.util.Set<String> codes = new java.util.TreeSet<>();
        g.get("features").forEach(f -> codes.add(f.get("properties").get("codarea").asText()));
        assertThat(codes).containsExactly("11", "12", "13", "14", "15", "16", "17", "21", "22", "23", "24", "25", "26", "27", "28", "29", "31", "32", "33", "35", "41", "42", "43", "50", "51", "52", "53");
        // as coordenadas curadas (item #6) ficam dentro do retângulo do Brasil: um erro de geocodificação não vira marcador no oceano
        for (String fund : new String[] {"hglg11", "knri11", "xpml11", "mxrf11"}) {
            com.fasterxml.jackson.databind.JsonNode d = json.readTree(read("/data/composicao/" + fund + ".json"));
            for (com.fasterxml.jackson.databind.JsonNode a : d.get("ativos")) {
                com.fasterxml.jackson.databind.JsonNode c = a.get("coordenadas");
                if (c == null || c.isNull()) continue;
                assertThat(c.get("lat").asDouble()).as(fund + " " + a.get("id")).isBetween(-34.0, 6.0);
                assertThat(c.get("lng").asDouble()).as(fund + " " + a.get("id")).isBetween(-74.5, -32.0);
            }
        }
    }

    @Test void theInterfaceNeverReachesForTheLibraryOnAnExternalHost() throws IOException {
        String all = dev.meridianwealth.testsupport.StaticPage.whole();
        assertThat(all).contains("/vendor/echarts.min.js").doesNotContain("cdn.jsdelivr").doesNotContain("echarts.apache.org");
    }
}
