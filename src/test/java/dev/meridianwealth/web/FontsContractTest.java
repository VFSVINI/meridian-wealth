package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Contrato das fontes do protótipo (ADR 012): toda fonte declarada no {@code index.html} é servida pelo próprio projeto, é um WOFF2
 * de verdade, tem o texto da licença SIL OFL ao lado e nenhuma fonte vem de domínio externo.
 */
class FontsContractTest {
    private static String html;

    @BeforeAll
    static void load() throws IOException {
        html = dev.meridianwealth.testsupport.StaticPage.whole();
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = FontsContractTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso ausente: " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    private static List<String> fontFiles() {
        Matcher m = Pattern.compile("url\\(\"(/fonts/[^\"]+\\.woff2)\"\\)").matcher(html);
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test void everyDeclaredFontIsServedByTheProjectAsRealWoff2() throws IOException {
        List<String> files = fontFiles();
        assertThat(files).containsExactlyInAnyOrder("/fonts/big-shoulders-display-latin-variable-normal.woff2", "/fonts/inter-latin-variable-normal.woff2",
                "/fonts/ibm-plex-mono-latin-400-normal.woff2", "/fonts/ibm-plex-mono-latin-500-normal.woff2");
        for (String f : files) {
            byte[] bytes = read("/static" + f);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).as("assinatura WOFF2 de " + f).isEqualTo("wOF2");
            assertThat(bytes.length).as("tamanho de " + f).isBetween(5_000, 200_000);
        }
    }

    @Test void thePrototypeFamiliesAreDeclaredWithTheirFallbacks() {
        assertThat(html).contains("font-family:\"Big Shoulders Display\"").contains("font-family:\"Inter\"").contains("font-family:\"IBM Plex Mono\"");
        assertThat(html).contains("--display:\"Big Shoulders Display\",\"Arial Narrow\"").contains("--sans:\"Inter\",system-ui").contains("--mono:\"IBM Plex Mono\",ui-monospace");
        assertThat(html).containsPattern("font-display:swap");
        assertThat(html).as("as fontes do protótipo anterior saíram").doesNotContain("Cormorant").doesNotContain("Figtree");
    }

    /** O arquivo é o que foi baixado do Google Fonts em 2026-11-24 (Big Shoulders Display v24 e Inter v20, subconjunto latino, 35436 e 48256 bytes). */
    @Test void theTwoCosmosFontsAreExactlyTheFilesThatWereDownloaded() throws Exception {
        assertThat(sha256(read("/static/fonts/big-shoulders-display-latin-variable-normal.woff2"))).isEqualTo("698f9960927e7b5a0c38e5bf388cd0a6e42d855062fe70366cf5057664e39401");
        assertThat(sha256(read("/static/fonts/inter-latin-variable-normal.woff2"))).isEqualTo("3100e775e8616cd2611beecfa23a4263d7037586789b43f035236a2e6fbd4c62");
    }

    private static String sha256(byte[] b) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Test void eachFontFamilyShipsItsOpenFontLicense() throws IOException {
        for (String family : List.of("big-shoulders-display", "inter", "ibm-plex-mono")) {
            String license = new String(read("/static/fonts/LICENSE-" + family + ".txt"), StandardCharsets.UTF_8);
            assertThat(license).as("licença de " + family).containsIgnoringCase("SIL OPEN FONT LICENSE").contains("Copyright");
        }
    }

    @Test void noFontIsLoadedFromAnExternalDomain() {
        assertThat(html).doesNotContain("fonts.googleapis.com").doesNotContain("fonts.gstatic.com").doesNotContain("cdn.jsdelivr.net");
        assertThat(html).doesNotContainPattern("src:url\\(\"https?://");
    }
}
