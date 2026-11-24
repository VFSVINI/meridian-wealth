package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Minha Carteira em 3D (item #43): os mesmos números da lista e da tabela, privacidade ao sair da conta e nada inventado nem animado como dado. */
class Portfolio3dContractTest {

    @Test
    void theDonutReadsTheSameNumbersAsTheListAndTheTableAndNeverInventsData() throws IOException {
        String ct = StaticPage.file("carteira.js"), c3 = StaticPage.file("carteira3d.js");
        assertThat(ct).contains("function ctEmit()").contains("'mw:ct-data'").contains("'mw:ct-need'").contains("byClass:s.byClass").contains("weights().filter(x=>x.w!=null)").contains("catCol:CAT_COL");
        assertThat(c3).contains("'mw:ct-data'").contains("data.byClass").contains("data.positions").contains("Number(c.percent)");
        assertThat(c3).doesNotContain("fetch(").doesNotContain("Math.random").doesNotContain("setInterval").doesNotContain("innerHTML");
    }

    @Test
    void theOuterRingIsTheClassesAndTheInnerRingTheOnlyPositionsWithBrlValueAndSaysWhatIsLeftOut() throws IOException {
        String c3 = StaticPage.file("carteira3d.js"), html = StaticPage.file("index.html");
        assertThat(c3).contains("RING = {pos: [1.15, 2.15], cls: [2.3, 3.4]}").contains("(data.positions || []).filter(p => p.weight > 0)").contains("positionsWithoutBrlValue").contains("ficam de fora");
        assertThat(html).contains("posições sem valor em reais ficam de fora");
    }

    @Test
    void theSliceFractionsAreNormalizedBySumSoEachRingClosesAtOneHundredPercent() throws IOException {
        // medido: soma das classes 100,00 e soma das posições 100,00
        String c3 = StaticPage.file("carteira3d.js");
        assertThat(c3).contains("const sumC = cls.reduce((a, c) => a + c.v, 0) || 1;").contains("const sumP = pos.reduce((a, p) => a + p.weight, 0);").contains("c.v / sumC").contains("p.weight / (sumP || 1)");
    }

    @Test
    void afterLoggingOutTheDonutForgetsThePortfolioClearsTheSceneAndTheAccessibleText() throws IOException {
        // medido: ao sair, o texto virou o genérico e o card sumiu; um novo login montou a cena do zero
        String ct = StaticPage.file("carteira.js"), c3 = StaticPage.file("carteira3d.js");
        assertThat(ct).contains("document.dispatchEvent(new Event('mw:ct-clear'));");
        assertThat(c3).contains("document.addEventListener('mw:ct-clear'").contains("data = null;").contains("canvas.setAttribute('aria-label', GENERIC)").contains("Entre na sua conta para ver.").contains("disposeGroup(group);");
    }

    @Test
    void hoveringASliceOnlyMovesItAndNeverChangesAnyNumber() throws IOException {
        String c3 = StaticPage.file("carteira3d.js");
        assertThat(c3).contains("a fatia sob o mouse sai alguns centésimos do anel (efeito só visual; nenhum número muda)").contains("m.position.copy(m.userData.dir).multiplyScalar(m.userData.off)");
        assertThat(c3).contains("tip.append(head, body)").contains("body.textContent = `${m.label}\\n${m.text}`");
    }

    @Test
    void theCardKeepsTheListTreemapAndTableAlternativesAndUsesTheSharedKit() throws IOException {
        String html = StaticPage.file("index.html"), c3 = StaticPage.file("carteira3d.js");
        assertThat(html).contains("id=\"ct3dCard\" hidden").contains("id=\"ct3dCanvas\" tabindex=\"0\" role=\"img\"").contains("id=\"ctAllocWrap\"").contains("id=\"ctPos\"")
                .contains("<script type=\"module\" src=\"/carteira3d.js\"></script>");
        assertThat(c3).doesNotContain("new THREE.WebGLRenderer").contains("createStage(").contains("createOrbit(kit, canvas").contains("if (!kit || !data || kit.lost) return;");
    }

    @Test
    void touchScreensOpenTheDetailWithATapBecauseTheyHaveNoHover() throws IOException {
        // medido: um toque simples numa fatia não abria nada (o tooltip só existia com hover) e o pointerleave do dedo o esconderia
        String kit = StaticPage.file("kit3d.js");
        assertThat(kit).contains("if (e.type === 'pointerup' && moved < 6 && o.onTap) o.onTap(e);").contains("if (e.pointerType === 'mouse' && o.onLeave) o.onLeave();");
        for (String f : new String[] {"cockpit3d.js", "montecarlo3d.js", "correlacao3d.js", "carteira3d.js"}) {
            assertThat(StaticPage.file(f)).as(f).contains("onTap: e => { px = e.clientX; py = e.clientY; pick(); }");
        }
    }
}
