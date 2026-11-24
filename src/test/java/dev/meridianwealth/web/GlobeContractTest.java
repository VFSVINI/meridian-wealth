package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.Exchange;
import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Globo 3D dos mercados (item #41): estado real do servidor, continentes fixados, toda bolsa representada, hora no fuso da bolsa e nada animado como dado. */
class GlobeContractTest {
    static final String LAND_SHA256 = "9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9";

    private static byte[] read(String path) throws IOException {
        try (InputStream in = GlobeContractTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso ausente: " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void theLandOutlineIsTheNaturalEarthFilePinnedByChecksumWithItsPublicDomainNotice() throws Exception {
        byte[] geo = read("/static/data/ne_110m_land.geojson");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(geo))).isEqualTo(LAND_SHA256);
        assertThat(geo).hasSize(138_160);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(geo).get("features")).hasSize(127);
        assertThat(new String(read("/static/data/NOTICE-natural-earth.txt"), StandardCharsets.UTF_8)).contains("dominio publico").contains("naturalearthdata.com");
    }

    @Test
    void everyExchangeTheServerReportsIsRepresentedOnTheGlobeAsAPinOrAnOrbitRing() throws IOException {
        String js = StaticPage.file("globe3d.js");
        Matcher cities = Pattern.compile("(?m)^\\s*(?:[A-Z0-9_]+: \\['[^']+', -?[0-9.]+, -?[0-9.]+\\],?\\s*)+").matcher(js.substring(js.indexOf("const CITY")));
        assertThat(cities.find()).isTrue();
        String cityBlock = cities.group();
        for (Exchange e : Exchange.values()) {
            boolean pin = cityBlock.contains(e.name() + ": ['");
            boolean ring = e.name().equals("GLOBAL_24H") || e.name().equals("CRYPTO");
            assertThat(pin || ring).as("a bolsa %s precisa de cidade ou de anel", e).isTrue();
            assertThat(js).as("nome amigável de %s", e).contains(e.name() + ": '");
        }
    }

    @Test
    void theCityCoordinatesAreRealPlacesOnEarth() throws IOException {
        String js = StaticPage.file("globe3d.js");
        Matcher m = Pattern.compile("([A-Z0-9_]+): \\['([^']+)', (-?[0-9.]+), (-?[0-9.]+)\\]").matcher(js);
        int n = 0;
        while (m.find()) {
            double lat = Double.parseDouble(m.group(3)), lon = Double.parseDouble(m.group(4));
            assertThat(lat).as(m.group(1)).isBetween(-90.0, 90.0);
            assertThat(lon).as(m.group(1)).isBetween(-180.0, 180.0);
            n++;
        }
        assertThat(n).as("dez bolsas com cidade").isEqualTo(10);
    }

    @Test
    void localTimeIsTheExchangesOwnClockReadFromTheServersStringNotTheBrowsersZone() throws IOException {
        // medido: a API devolve a hora no fuso da bolsa (ASX 15:09); formatar no fuso do navegador (ou UTC) mostrava "hora local" errada
        assertThat(StaticPage.file("globe3d.js")).contains("function localTime(m) { return m.localTime.slice(11, 16); }").doesNotContain("toLocaleTimeString");
        String app = StaticPage.file("app.js");
        assertThat(app).contains("hora local ${m.localTime.slice(11,16)}");
        assertThat(app.substring(app.indexOf("async function clock()"), app.indexOf("async function clock()") + 900)).doesNotContain("toLocaleTimeString");
    }

    @Test
    void theGlobeReadsOnlyTheServersMarketStateAndTheUniverseAndNeverInventsOrAnimatesData() throws IOException {
        String js = StaticPage.file("globe3d.js"), app = StaticPage.file("app.js");
        assertThat(js).contains("from '/kit3d.js'").contains("'mw:markets'").contains("'mw:markets-need'").contains("'mw:market-pick'").contains("'mw:open-asset'");
        assertThat(js).contains("fetch('/data/ne_110m_land.geojson')").contains("fetch('/api/universe')").doesNotContain("Math.random").doesNotContain("setInterval").doesNotContain("innerHTML");
        assertThat(app).contains("document.dispatchEvent(new CustomEvent('mw:markets',{detail:ms}))").contains("'mw:markets-need'").contains("'mw:open-asset'");
        assertThat(js).as("o módulo existe depois do primeiro mw:markets: pede o estado ao carregar").contains("document.dispatchEvent(new Event('mw:markets-need'))");
    }

    @Test
    void theClockListIsTheAccessibleAlternativeAsButtonsThatSelectTheExchange() throws IOException {
        String html = StaticPage.file("index.html"), app = StaticPage.file("app.js");
        assertThat(html).contains("id=\"globeCanvas\" tabindex=\"0\" role=\"img\"").contains("id=\"clock\"").contains("id=\"globeSel\" class=\"globe-sel\" aria-live=\"polite\"")
                .contains("<script type=\"module\" src=\"/globe3d.js\"></script>");
        assertThat(app).contains("<button type=\"button\" class=\"mk\" data-ex=\"${esc(m.exchange)}\" aria-pressed=\"false\"");
        assertThat(StaticPage.file("app.css")).contains(".globe-stage{position:relative").contains("touch-action:pan-y");
    }

    @Test
    void theSceneKeepsTheKitGuaranteesAndDoesNotRecreateGpuBuffersWhenTheExchangesAreTheSame() throws IOException {
        String js = StaticPage.file("globe3d.js");
        assertThat(js).doesNotContain("new THREE.WebGLRenderer").contains("createStage(");
        // medido: recriar tudo a cada atualização de 30 s criava e destruía ~170 buffers da GPU; com as mesmas bolsas só se recolore
        assertThat(js).contains("function refreshPins()").contains("if (sig === pinSig && pins.length + rings.length === markets.length) { refreshPins(); return; }");
        assertThat(js).as("com o contexto perdido, nenhum evento de dados reconstrói a cena").contains("if (kit && !kit.lost) buildPins();");
        assertThat(js).as("o anel olha para fora em coordenadas do globo, não do mundo").contains("setFromUnitVectors(new THREE.Vector3(0, 0, 1), n)").doesNotContain("lookAt(n");
    }
}
