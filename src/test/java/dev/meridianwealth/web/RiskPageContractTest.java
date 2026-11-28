package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Risco no visual Cosmos (item #53, parte 3): cone por ano e paisagem de probabilidades sobre as mesmas trajetórias do servidor, sempre rotulados como projeção. */
class RiskPageContractTest {

    @Test
    void thePageAsksTheQuestionHasTheShortcutsTheConeTheLandscapeAndKeepsTheDetailedChartsCollapsed() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Quão longe posso chegar e quanto posso oscilar no caminho?").contains("id=\"rk-aporte\"").contains("id=\"rk-prazo\"").contains("id=\"rkCone\"").contains("id=\"rkLand\"").contains("id=\"rkYear\"")
                .contains("Paisagem de probabilidades").contains("href=\"#/correlacao\"");
        assertThat(html).as("o cone 2D e 3D, a distribuição e a tabela continuam, recolhidos").containsPattern("(?s)<details class=\"ck-full\" id=\"rkMore\">.*id=\"mcCone\".*id=\"mc3dCanvas\".*id=\"mcTable\".*</details>");
        assertThat(html).contains("<script src=\"/risco.js\"></script>");
    }

    @Test
    void everythingIsDrawnFromTheServerResultAndNothingIsDrawnAtRandom() throws IOException {
        String js = StaticPage.file("risco.js");
        assertThat(js).contains("mw:mc-data").contains("d.density").contains("dn.counts[y]").doesNotContain("Math.random").doesNotContain("fetch(");
        assertThat(js).as("a paisagem e o cone dizem o que mostram em texto").contains("Paisagem de probabilidades: uma curva por ano").contains("Cone de patrimônio: faixa dos percentis 5 a 95");
        assertThat(js).as("sem meta, o anel diz o que mede").contains("chance de terminar acima do que foi investido").contains("Retorno e volatilidade são hipóteses, não previsão.");
    }

    @Test
    void theLastRidgeAlwaysFitsInsideTheDrawing() throws IOException {
        String js = StaticPage.file("risco.js");
        assertThat(js).as("a altura segue o número de curvas, para o ano final não ser cortado").contains("const H = top + 130 + (years.length - 1) * step + 28;").doesNotContain("H + 60");
    }

    @Test
    void shortcutsAreDebouncedBecauseProjectionsHaveARateLimitAndTheLabelStaysProjection() throws IOException {
        String js = StaticPage.file("risco.js"), html = StaticPage.file("index.html");
        assertThat(js).contains("requestSubmit").contains("}, 600)");
        assertThat(html).contains("PROJEÇÃO: não é cotação nem promessa de retorno").contains("class=\"cs-chip am\">projeção</span>");
        assertThat(StaticPage.file("cosmos.css")).contains("@media(prefers-reduced-motion:no-preference){.rk-r{animation:rk-in");
    }
}
