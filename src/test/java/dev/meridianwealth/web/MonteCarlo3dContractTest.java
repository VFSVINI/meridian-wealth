package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Monte Carlo em 3D (item #44): sempre projeção, mesma simulação do 2D, câmera orbital compartilhada, nada inventado nem animado. */
class MonteCarlo3dContractTest {

    @Test
    void theThreeDimensionalConeReadsTheSameSimulationResultAsTheTwoDimensionalCharts() throws IOException {
        String mc = StaticPage.file("montecarlo.js"), c3 = StaticPage.file("montecarlo3d.js");
        assertThat(mc).contains("function mcEmit()").contains("'mw:mc-data'").contains("'mw:mc-need'").contains("setTimeout(mcEmit,0)");
        assertThat(c3).contains("'mw:mc-data'").contains("data.points").contains("data.histogram").contains("data.goal");
        assertThat(c3).as("só consome o resultado que a API já devolveu: nada de buscar, sortear ou animar").doesNotContain("fetch(").doesNotContain("Math.random").doesNotContain("setInterval")
                .doesNotContain("innerHTML");
    }

    @Test
    void itIsAlwaysLabeledAsProjectionOnScreenInTheTooltipAndForScreenReaders() throws IOException {
        String html = StaticPage.file("index.html"), c3 = StaticPage.file("montecarlo3d.js");
        assertThat(html).contains("id=\"mc3d\"").contains("<span class=\"tag\">projeção</span> não é cotação nem promessa de retorno");
        assertThat(c3).contains("· projeção").contains("É uma projeção, não uma promessa de retorno");
    }

    @Test
    void theCurvesAreTheExactPercentilesAndTheSurfaceOnlyConnectsThem() throws IOException {
        String c3 = StaticPage.file("montecarlo3d.js");
        assertThat(c3).contains("[p.p5, p.p25, p.p50, p.p75, p.p95].map(Number)").contains("NAMES = ['P5', 'P25', 'P50', 'P75', 'P95']");
        assertThat(c3).as("o histograma e a meta usam a MESMA escala de patrimônio da superfície").contains("Y(edges[i])").contains("Y(goal)");
        assertThat(c3).as("nenhum percentil intermediário é inventado: são cinco curvas").contains("for (let r = 0; r < 5; r++)");
    }

    @Test
    void thereIsNoVisibleSceneWhileTheContextIsLostAndTheSharedKitDoesTheHeavyLifting() throws IOException {
        String c3 = StaticPage.file("montecarlo3d.js");
        assertThat(c3).doesNotContain("new THREE.WebGLRenderer").contains("createStage(").contains("createOrbit(kit, canvas").contains("disposeGroup(group)").contains("if (!kit || !data || kit.lost) return;");
    }

    @Test
    void theViewIsReachableByKeyboardAndKeepsTheTableAndTwoDimensionalAlternatives() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("id=\"mc3dBtn\"").contains("aria-pressed=\"false\"").contains("id=\"mc3dCanvas\" tabindex=\"0\" role=\"img\"").contains("id=\"mcTable\"").contains("id=\"mcCone\"")
                .contains("<script type=\"module\" src=\"/montecarlo3d.js\"></script>");
        assertThat(StaticPage.file("montecarlo3d.js")).contains("box.hidden = !on; flat.hidden = on;").contains("canvas.setAttribute('aria-label'");
    }

    @Test
    void theSharedOrbitIsUsedByBothThreeDimensionalCharts() throws IOException {
        String kit = StaticPage.file("kit3d.js");
        assertThat(kit).contains("export function createOrbit(kit, canvas, o = {})").contains("export function makeLabel(").contains("export function disposeGroup(");
        for (String f : new String[] {"cockpit3d.js", "montecarlo3d.js"}) {
            assertThat(StaticPage.file(f)).as(f).contains("createOrbit(kit, canvas").doesNotContain("setPointerCapture").doesNotContain("'wheel'");
        }
    }
}
