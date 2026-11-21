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
        html = new String(read("/static/index.html"), StandardCharsets.UTF_8);
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
        assertThat(files).hasSize(8);
        for (String f : files) {
            byte[] bytes = read("/static" + f);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).as("assinatura WOFF2 de " + f).isEqualTo("wOF2");
            assertThat(bytes.length).as("tamanho de " + f).isBetween(5_000, 200_000);
        }
    }

    @Test void thePrototypeFamiliesAreDeclaredWithTheirFallbacks() {
        assertThat(html).contains("font-family:\"Cormorant Garamond\"").contains("font-family:\"Figtree\"").contains("font-family:\"IBM Plex Mono\"");
        assertThat(html).contains("--serif:\"Cormorant Garamond\",Georgia").contains("--sans:\"Figtree\",system-ui").contains("--mono:\"IBM Plex Mono\",ui-monospace");
        assertThat(html).containsPattern("font-display:swap");
    }

    @Test void eachFontFamilyShipsItsOpenFontLicense() throws IOException {
        for (String family : List.of("cormorant-garamond", "figtree", "ibm-plex-mono")) {
            String license = new String(read("/static/fonts/LICENSE-" + family + ".txt"), StandardCharsets.UTF_8);
            assertThat(license).as("licença de " + family).containsIgnoringCase("SIL OPEN FONT LICENSE").contains("Copyright");
        }
    }

    @Test void noFontIsLoadedFromAnExternalDomain() {
        assertThat(html).doesNotContain("fonts.googleapis.com").doesNotContain("fonts.gstatic.com").doesNotContain("cdn.jsdelivr.net");
        assertThat(html).doesNotContainPattern("src:url\\(\"https?://");
    }
}
