package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Correlação em 3D (item #42): a mesma matriz da tabela, "não informado" nunca vira zero, só retornos reais e nada animado como dado. */
class Correlation3dContractTest {

    @Test
    void theBarsReadTheSameMatrixAsTheTableAndNeverInventData() throws IOException {
        String cr = StaticPage.file("correlacao.js"), c3 = StaticPage.file("correlacao3d.js");
        assertThat(cr).contains("function crEmit()").contains("'mw:cr-data'").contains("'mw:cr-need'").contains("crEmit();\n}");
        assertThat(c3).contains("'mw:cr-data'").contains("data.assets").contains("matrix.days[i][j]").contains("data.minDays");
        assertThat(c3).doesNotContain("fetch(").doesNotContain("Math.random").doesNotContain("setInterval").doesNotContain("innerHTML");
    }

    @Test
    void aCellWithoutDataIsAFlatGreyMarkerWithNoBarSoItNeverLooksLikeZeroCorrelation() throws IOException {
        // medido com uma matriz de teste: a célula nula mostrou "não informado, 40 pregões em comum (mínimo 60)" e nenhuma correlação 0,00
        String c3 = StaticPage.file("correlacao3d.js");
        assertThat(c3).contains("if (v == null) posVoids.push([i, j]); else posBars.push([i, j, Number(v)]);").contains("não informado");
        assertThat(c3).contains("d.scale.set(0.82, 0.03, 0.82)").contains("opacity: 0.45");
        assertThat(c3).as("a barra tem altura mínima só para correlação real; o vazio é outra malha").contains("Math.max(0.03, Math.abs(v) * HMAX)");
    }

    @Test
    void positiveGoesUpInGreenNegativeGoesDownInRedAndFragileEstimatesAreFlagged() throws IOException {
        String c3 = StaticPage.file("correlacao3d.js");
        assertThat(c3).contains("v >= 0 ? h / 2 : -h / 2").contains("v >= 0 ? up : down").contains("days < 120").contains("estimativa frágil (poucos pregões)");
    }

    @Test
    void theCardKeepsTheTableAlternativeAndIsReachableByKeyboard() throws IOException {
        String html = StaticPage.file("index.html"), c3 = StaticPage.file("correlacao3d.js");
        assertThat(html).contains("id=\"cr3dCard\" hidden").contains("id=\"cr3dCanvas\" tabindex=\"0\" role=\"img\"").contains("id=\"crAssets\"").contains("A tabela acima traz os mesmos números")
                .contains("<script type=\"module\" src=\"/correlacao3d.js\"></script>");
        assertThat(c3).contains("canvas.setAttribute('aria-label'").contains("createOrbit(kit, canvas");
    }

    @Test
    void theSharedKitGuaranteesApplyAndNothingRebuildsWhileTheContextIsLost() throws IOException {
        String c3 = StaticPage.file("correlacao3d.js");
        assertThat(c3).doesNotContain("new THREE.WebGLRenderer").contains("createStage(").contains("disposeGroup(group)").contains("if (!kit || !data || kit.lost) return;");
    }

    @Test
    void afterLoggingOutTheThreeDimensionalViewForgetsThePortfolioMatrix() throws IOException {
        // medido: depois do logout o módulo ainda guardava a matriz e o aria-label citava "3 ativos da carteira"
        String cr = StaticPage.file("correlacao.js"), c3 = StaticPage.file("correlacao3d.js");
        assertThat(cr).contains("CR.data=null;document.dispatchEvent(new Event('mw:cr-clear'));");
        assertThat(c3).contains("document.addEventListener('mw:cr-clear'").contains("data = null; matrix = null;").contains("Entre na sua conta para ver.").contains("disposeGroup(group);");
    }
}
