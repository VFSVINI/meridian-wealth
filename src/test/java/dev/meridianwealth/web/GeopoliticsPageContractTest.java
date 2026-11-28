package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Geopolítica no visual Cosmos (item #51): mapa por país com manchetes reais, passo a passo, termômetros e notícias com link de origem. */
class GeopoliticsPageContractTest {

    @Test
    void thePageHasTheQuestionTheMapTabsStepsThermometersNewsAndKeepsThePortfolioLensCollapsed() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("O que acontece no mundo e por que mexe com o meu dinheiro?").contains("data-gpt=\"risco\"").contains("data-gpt=\"setores\"").contains("data-gpt=\"manchetes\"")
                .contains("id=\"gpMap\"").contains("id=\"gpSide\"").contains("id=\"gpSteps\"").contains("id=\"gpTh\"").contains("id=\"gpNews\"");
        assertThat(html).as("o tradutor e o radar antigos continuam, recolhidos").containsPattern("(?s)<details class=\"ck-full\">.*id=\"tz\".*id=\"macro\".*</details>");
        assertThat(html).contains("<script src=\"/geo.js\"></script>");
    }

    @Test
    void theMapSaysItIsHeadlineBasedNotOfficialAndShowsMissingCountriesAsNotInformedNeverZero() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("não é um índice oficial").contains("nunca como risco zero");
        String js = StaticPage.file("geo.js");
        assertThat(js).contains("Nada é estimado.").doesNotContain("Math.random");
    }

    @Test
    void newsLinksOpenSafelyAndEveryDynamicValueIsEscaped() throws IOException {
        String js = StaticPage.file("geo.js");
        assertThat(js).contains("target=\"_blank\" rel=\"noopener noreferrer\"").contains("href=\"${esc(n.link)}\"");
        assertThat(js).as("fonte sem link diz isso em vez de inventar um").contains("Esta fonte não informou o link da matéria.");
        assertThat(js).contains("esc(c.name)").contains("esc(n.title)").contains("esc(n.source)");
    }

    @Test
    void theMapDrawsFromTheBundledNaturalEarthFileAndListensToTheMacroEvent() throws IOException {
        String js = StaticPage.file("geo.js");
        assertThat(js).contains("/data/countries.geojson").contains("mw:macro");
        assertThat(StaticPage.file("app.js")).contains("new CustomEvent('mw:macro'");
    }
}
