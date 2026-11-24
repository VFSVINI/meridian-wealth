package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Módulo 3D compartilhado e cockpit em 3D (item #40): só dado real, a mesma fonte do 2D, alternativas de acesso e cena sem GPU gasta à toa. */
class Cockpit3dContractTest {

    @Test
    void onlyTheKitCreatesTheWebglRendererSoEverySceneGetsPauseAndContextLossHandling() throws IOException {
        for (String f : new String[] {"hero3d.js", "cockpit3d.js"}) {
            assertThat(StaticPage.file(f)).as(f).doesNotContain("new THREE.WebGLRenderer").contains("createStage(").contains("from '/kit3d.js'");
        }
        String kit = StaticPage.file("kit3d.js");
        assertThat(kit).contains("new THREE.WebGLRenderer").contains("export function createStage").contains("webglcontextlost").contains("webglcontextrestored");
        assertThat(kit).as("módulo ES sem global").doesNotContain("window.").doesNotContain("globalThis");
    }

    @Test
    void theThreeDimensionalChartReadsTheSameRealCandlesAsTheTwoDimensionalOneNeverInventsData() throws IOException {
        String ck = StaticPage.file("cockpit.js"), c3 = StaticPage.file("cockpit3d.js");
        assertThat(ck).contains("function ckEmit()").contains("'mw:ck-data'").contains("bb:bollinger(bars),sma:smaSeries(bars)").contains("'mw:ck-need'").contains("'mw:ck-hover'");
        assertThat(c3).contains("'mw:ck-data'");
        assertThat(c3).as("só consome o evento do cockpit: nada de buscar, sortear ou animar preço").doesNotContain("fetch(").doesNotContain("Math.random").doesNotContain("XMLHttpRequest")
                .doesNotContain("setInterval");
    }

    @Test
    void hoveringACandleShowsItsRealNumbersAndUpdatesTheInspectorCard() throws IOException {
        String c3 = StaticPage.file("cockpit3d.js");
        assertThat(c3).contains("Raycaster").contains("intersectObject(bodies").contains("'mw:ck-hover'").contains("Abertura").contains("Fechamento").contains("não informado");
        assertThat(c3).as("o texto do tooltip é escrito com textContent, nunca como HTML").doesNotContain("innerHTML");
    }

    @Test
    void theThreeDimensionalViewIsReachableByKeyboardAndHasTheTableAndTwoDimensionalAlternatives() throws IOException {
        String html = StaticPage.file("index.html"), c3 = StaticPage.file("cockpit3d.js");
        assertThat(html).contains("id=\"ck3dBtn\"").contains("aria-pressed=\"false\"").contains("id=\"ck3dCanvas\" tabindex=\"0\" role=\"img\"").contains("id=\"ckTableBtn\"")
                .contains("<script type=\"module\" src=\"/cockpit3d.js\"></script>");
        assertThat(c3).contains("ArrowLeft").contains("ArrowRight").contains("ArrowUp").contains("ArrowDown").contains("dblclick").contains("canvas.setAttribute('aria-label'");
        assertThat(c3).as("a roda só dá zoom com Ctrl ou com o gráfico focado: senão o 3D roubaria a rolagem da página").contains("e.ctrlKey || e.metaKey || document.activeElement === canvas");
        assertThat(StaticPage.file("app.css")).contains(".ck3d{position:relative;touch-action:pan-y");
    }

    @Test
    void whileTheContextIsLostBuildDoesNotHideTheNoticeAndTheCameraFitsNarrowScreens() throws IOException {
        // medido: um evento de dados do cockpit durante a perda chamava build(), que escondia o aviso "O navegador pausou o 3D"
        String c3 = StaticPage.file("cockpit3d.js");
        assertThat(c3).contains("if (!kit || !data || kit.lost) return;");
        assertThat(c3).as("distância da câmera pela proporção do palco (celular estreito não corta o terreno)").contains("if (cam.fit) cam.dist = Math.max(17,");
    }

    @Test
    void sceneColorsComeFromTheThemeTokensSoBothThemesWork() throws IOException {
        String c3 = StaticPage.file("cockpit3d.js");
        assertThat(c3).contains("getComputedStyle(document.documentElement)").contains("'--up'").contains("'--down'").contains("'--gold'").contains("data-theme");
    }

    @Test
    void everyRebuildReleasesTheInstanceBuffersOfTheOldScene() throws IOException {
        // medido: sem InstancedMesh.dispose(), 12 reconstruções vazavam 46 buffers da GPU (cada atualização de cotação reconstrói a cena)
        assertThat(StaticPage.file("cockpit3d.js")).contains("if (o.isInstancedMesh) o.dispose();").contains("o.geometry.dispose()").contains("o.material.dispose()");
    }
}
