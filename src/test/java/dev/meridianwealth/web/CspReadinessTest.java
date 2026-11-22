package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A CSP do servidor proíbe script e estilo embutidos (ADR 013). Se alguém voltar a embutir código no HTML ou gerar marcação com
 * manipulador de evento, a página quebra no navegador sem que nenhum teste de API perceba: este teste pega isso antes.
 */
class CspReadinessTest {
    @Test void theHtmlHasNoInlineScriptOrStyleAndOnlyLoadsLocalFiles() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).doesNotContainPattern("<script(?![^>]*\\ssrc=)").doesNotContain("<style").doesNotContain("javascript:");
        assertThat(html).doesNotContainPattern("(?i)\\son[a-z]+\\s*=");
        List<String> refs = new ArrayList<>();
        Matcher m = Pattern.compile("(?:src|href)=\"([^\"]+)\"").matcher(html);
        while (m.find()) refs.add(m.group(1));
        assertThat(refs).isNotEmpty().allSatisfy(r -> assertThat(r).as("referência externa: " + r).matches("^(/|#).*"));
        assertThat(html).contains("<script src=\"/theme.js\">").contains("<script src=\"/app.js\">").contains("<link rel=\"stylesheet\" href=\"/app.css\">");
    }

    @Test void theScriptDoesNotBuildCodeAtRuntimeNorGenerateInlineHandlers() throws IOException {
        String js = StaticPage.file("app.js") + StaticPage.file("theme.js");
        assertThat(js).doesNotContain("eval(").doesNotContain("new Function(").doesNotContain("document.write(").doesNotContain("javascript:");
        assertThat(js).doesNotContainPattern("set(Timeout|Interval)\\(\\s*[\"'`]");
        assertThat(js).as("marcação gerada com manipulador de evento seria bloqueada pela CSP").doesNotContainPattern("(?i)\\son(click|change|input|submit|load|error|focus|blur|keydown)=[\"']");
    }

    @Test void externalLinksOpenedInANewTabNeverGiveTheOpenerBack() throws IOException {
        String all = StaticPage.file("app.js") + StaticPage.file("index.html");
        Matcher m = Pattern.compile("<a [^>]*target=\"_blank\"[^>]*>").matcher(all);
        int found = 0;
        while (m.find()) {
            found++;
            assertThat(m.group()).as("link sem noopener: " + m.group()).contains("noopener");
        }
        assertThat(found).isPositive();
    }

    @Test void thereAreNoThirdPartyScriptsStylesFontsOrImagesAnywhere() throws IOException {
        String all = StaticPage.whole();
        assertThat(all).doesNotContainPattern("(?i)(src|url\\()\\s*=?\\s*[\"']?https?://");
        assertThat(all).doesNotContain("googleapis.com").doesNotContain("cdnjs").doesNotContain("unpkg.com").doesNotContain("jsdelivr");
    }
}
