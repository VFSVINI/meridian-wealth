package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Efeitos de interface (item #45): transição entre telas, inclinação de cartões e revelação ao rolar, sem animar nenhum número (ADR 004). */
class FxContractTest {

    @Test
    void noNumberIsEverAnimatedTheModuleNeverWritesTextToAnyElement() throws IOException {
        String js = StaticPage.file("fx.js");
        assertThat(js).as("ADR 004: nenhum preço é simulado, interpolado ou animado; o módulo só escreve classes e variáveis de CSS")
                .doesNotContain("textContent =").doesNotContain("innerText").doesNotContain("innerHTML").doesNotContain("nodeValue").doesNotContain("insertAdjacent")
                .doesNotContain("requestAnimationFrame");
    }

    @Test
    void theModuleDeclaresNoGlobalAndIsOffForReducedMotionAndTouchScreens() throws IOException {
        String js = StaticPage.file("fx.js");
        assertThat(js.stripLeading()).as("módulo envolto numa função").contains("(function () {").endsWith("})();\n");
        assertThat(js).contains("prefers-reduced-motion: reduce").contains("(hover: hover) and (pointer: fine)");
        assertThat(StaticPage.file("app.css")).contains("@media(prefers-reduced-motion:reduce){.fx .page.enter{animation:none}");
    }

    @Test
    void theIrisTransitionEndsOnlyWithThePagesOwnAnimationNotAChildsPulse() throws IOException {
        // medido: o pulso de cotação nova (beat, fd, fu) de um filho também dispara animationend, borbulha e cortava a íris no meio
        String js = StaticPage.file("fx.js"), css = StaticPage.file("app.css");
        assertThat(js).contains("e.target === el && e.animationName === 'irisIn'");
        assertThat(css).contains("@keyframes irisIn").contains(".fx .page.enter{animation:irisIn");
    }

    @Test
    void theRouterStillSwitchesPagesSynchronouslySoChartsMeasureTheirContainers() throws IOException {
        String app = StaticPage.file("app.js");
        assertThat(app).contains("document.querySelectorAll('.page').forEach(p=>{p.hidden=p.dataset.page!==id});").doesNotContain("startViewTransition");
        assertThat(StaticPage.file("index.html").indexOf("/fx.js")).isPositive().isLessThan(StaticPage.file("index.html").indexOf("/app.js"));
    }

    @Test
    void tiltTargetsAreDelegatedAndTheRevealNeverHidesWhatJavaScriptCannotShow() throws IOException {
        String js = StaticPage.file("fx.js"), css = StaticPage.file("app.css");
        assertThat(js).contains("document.addEventListener('pointermove'").contains("'IntersectionObserver' in window");
        assertThat(css).as("a revelação só esconde sob a classe fx, que o JavaScript acrescenta").contains(".fx .card.rv:not(.in){opacity:0");
    }

    @Test
    void printingShowsEveryCardEvenBeforeScrollingToReachThem() throws IOException {
        // medido: 7 de 7 cartões do cockpit ficam com opacidade 0 ao carregar; sem regra de impressão, o PDF sairia em branco
        assertThat(StaticPage.file("app.css")).contains("@media print{.fx .card.rv:not(.in){opacity:1!important;transform:none!important}");
    }
}
