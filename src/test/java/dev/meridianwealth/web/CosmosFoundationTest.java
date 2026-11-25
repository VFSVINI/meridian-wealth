package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Fundação Cosmos (item #47): tokens, estrelas, shell por seções e a página Início do protótipo aprovado, sem dado inventado nem animado. */
class CosmosFoundationTest {

    @Test
    void theCosmosStylesheetComesAfterTheAppOneAndDefinesTheTokensForBothThemes() throws IOException {
        String html = StaticPage.file("index.html"), css = StaticPage.file("cosmos.css");
        assertThat(html.indexOf("/app.css")).isPositive().isLessThan(html.indexOf("/cosmos.css"));
        assertThat(css).contains("--bg:#04070e").contains("--gold:#ffb340").contains("--cyan:#55d6ff").contains("--up:#38dd9c").contains("--down:#ff6d82");
        assertThat(css).contains(":root[data-theme=\"ivory\"]{--bg:#f4f0e6").contains("#stars{position:fixed");
        assertThat(css).as("sem imagem nem fonte externa").doesNotContain("http://").doesNotContain("https://").doesNotContain("@import");
    }

    @Test
    void theStarFieldIsOnlyAmbientNeverWritesTextAndStopsWithReducedMotionOrAHiddenTab() throws IOException {
        String js = StaticPage.file("stars.js"), html = StaticPage.file("index.html");
        assertThat(html).contains("<canvas id=\"stars\" aria-hidden=\"true\"></canvas>").contains("<script src=\"/stars.js\"></script>");
        assertThat(js).contains("(prefers-reduced-motion: reduce)").contains("document.hidden").contains("visibilitychange").contains("dt >= 33");
        assertThat(js).contains("addEventListener('pointermove'").contains("'mw:warp'").contains("function nebulae()");
        assertThat(StaticPage.file("app.js")).contains("document.dispatchEvent(new Event('mw:warp'))");
        assertThat(StaticPage.file("cosmos.css")).contains("@keyframes irisIn{0%{opacity:0;transform:scale(.92)");
        assertThat(js).doesNotContain("fetch(").doesNotContain("textContent").doesNotContain("innerHTML").doesNotContain("Math.random").doesNotContain("window.");
    }

    @Test
    void theHomeIsPresentationThreeStepsAndTrustWithTheRealReadingCardAndNoSimulatedNumber() throws IOException {
        String html = StaticPage.file("index.html"), js = StaticPage.file("app.js");
        assertThat(html).contains("Entenda o mercado <em>antes</em> de decidir.").contains("Três passos, no seu ritmo").contains("Transparência em cada tela")
                .contains("A leitura em uma frase").contains("Começar a explorar").contains("Não executa ordens").contains("Sem dado, sem invenção");
        assertThat(js).contains("async function renderRead(live)").contains("/api/candles/${pick.symbol}?limit=60").contains("análise, não ordem");
        assertThat(js).as("o cartão traz a fonte real e nunca o selo de exemplo").contains("Fonte: ${esc(r.source)}").doesNotContain(">exemplo<");
    }

    @Test
    void theMenuIsGroupedLikeThePrototypeAndTheLegalStripIsAlwaysAtTheBottom() throws IOException {
        String js = StaticPage.file("app.js"), html = StaticPage.file("index.html");
        assertThat(js).contains("['EXPLORAR',[['home','Início']").contains("['ENTENDER',[").contains("['SIMULAR',[").contains("['MAIS',[");
        assertThat(html).contains("<footer class=\"cs-legal\">").contains("Não é ordem nem recomendação de investimento.");
    }

    @Test
    void thePageTitlesUseTheCinemaDisplayFaceAndTheContentSecurityPolicyStaysIntact() throws IOException {
        String css = StaticPage.file("cosmos.css"), html = StaticPage.file("index.html");
        assertThat(css).contains(".ph h1{font-weight:800").contains("text-transform:uppercase").contains("--display:\"Big Shoulders Display\"");
        assertThat(html).doesNotContain("<style").doesNotContain(" onclick=").doesNotContain("javascript:");
    }
}
