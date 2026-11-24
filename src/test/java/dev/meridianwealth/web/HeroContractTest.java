package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Hero 3D da página inicial (item #31): biblioteca fixada, módulo sem global, alternativas de acessibilidade e contraste do texto sobre o degradê. */
class HeroContractTest {
    static final String THREE_SHA256 = "08fd7545d13d2c7fb65ab691530a802dafefd638596501854f267d0fb13c39e7";

    private static byte[] read(String path) throws IOException {
        try (InputStream in = HeroContractTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso ausente: " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void threeJsIsServedLocallyAtThePinnedVersionWithItsMitLicense() throws Exception {
        byte[] js = read("/static/vendor/three.module.min.js");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(js))).isEqualTo(THREE_SHA256);
        assertThat(js).hasSize(691_648);
        String head = new String(js, 0, 300, StandardCharsets.UTF_8);
        assertThat(head).contains("Three.js Authors").contains("MIT");
        assertThat(new String(js, StandardCharsets.UTF_8)).as("a CSP proíbe eval: a biblioteca não pode depender dele").doesNotContain("eval(").doesNotContain("new Function");
        assertThat(new String(read("/static/vendor/LICENSE-three.txt"), StandardCharsets.UTF_8)).contains("The MIT License").contains("three.js authors");
    }

    @Test
    void theHeroIsAnEsModuleThatImportsTheLocalLibraryAndDeclaresNoGlobal() throws IOException {
        String js = StaticPage.file("hero3d.js"), html = StaticPage.file("index.html");
        assertThat(js).contains("from '/vendor/three.module.min.js'").doesNotContain("cdn.").doesNotContain("http://").doesNotContain("https://");
        assertThat(html).contains("<script type=\"module\" src=\"/hero3d.js\"></script>");
        assertThat(js).as("módulo: nada em window").doesNotContain("window.").doesNotContain("globalThis");
    }

    @Test
    void theHeroRespectsReducedMotionPausesOffscreenAndHasANoWebglFallback() throws IOException {
        String js = StaticPage.file("hero3d.js"), css = StaticPage.file("app.css"), html = StaticPage.file("index.html");
        assertThat(js).contains("prefers-reduced-motion: reduce").contains("IntersectionObserver").contains("visibilitychange").contains("document.hidden");
        assertThat(js).contains("no3d").contains("catch");
        // perda do contexto depois de funcionar (celular em segundo plano): sem tratar, o canvas vira um retângulo branco
        assertThat(js).contains("webglcontextlost").contains("webglcontextrestored").contains("preventDefault").contains("buildEnvironment");
        assertThat(js.indexOf("buildEnvironment()")).as("o ambiente é refeito na partida").isPositive();
        assertThat(js.indexOf("buildEnvironment()", js.indexOf("webglcontextrestored"))).as("e na restauração do contexto").isPositive();
        assertThat(html).contains("class=\"medal-fb\"").contains("aria-hidden=\"true\"");
        assertThat(css).contains(".hero3d.ready .medal-fb{display:none}").contains(".hero3d.no3d");
    }

    @Test
    void theCoinCanBeDraggedAndFlickedWithoutBreakingVerticalScrollOnTouchScreens() throws IOException {
        String js = StaticPage.file("hero3d.js"), css = StaticPage.file("app.css");
        assertThat(js).contains("pointerdown").contains("setPointerCapture").contains("pointercancel").contains("vel += 0.014").contains("PointLight");
        assertThat(css).as("arrasto horizontal gira a moeda; a rolagem vertical da página continua").contains("touch-action:pan-y").contains(".hero-stage.grabbing");
    }

    @Test
    void onlyThePrimaryMouseButtonDragsAndPointerCaptureCannotBreakTheHandler() throws IOException {
        // medido: o clique direito ligava o arrasto (e o menu de contexto pode engolir o pointerup, congelando a moeda)
        String js = StaticPage.file("hero3d.js");
        assertThat(js).contains("e.pointerType === 'mouse' && e.button !== 0").contains("try { stage.setPointerCapture(e.pointerId); }").contains("lostpointercapture");
        assertThat(js).contains("if (moved < 5 && e.type === 'pointerup') vel += 0.014");
    }

    @Test
    void nothingIsDrawnWhenTheHeroIsNotOnScreen() throws IOException {
        // medido: sem essa guarda, 40 eventos de rolagem fora da página inicial faziam 360 chamadas de desenho da GPU
        String js = StaticPage.file("hero3d.js");
        assertThat(js).contains("const onScreen = () => visible && !document.hidden && stage.clientWidth > 0 && !lost;");
        assertThat(js).contains("const redraw = () => { if (!raf && onScreen()) draw(); };").contains("if (!onScreen()) return;");
    }

    @Test
    void theHeroIsIdentityNotDataNoNumberOrPriceLivesInsideTheMedallion() throws IOException {
        String stage = StaticPage.file("index.html");
        int from = stage.indexOf("class=\"hero-stage\""), to = stage.indexOf("</div>", stage.indexOf("heroCanvas"));
        String inside = stage.substring(from, to);
        assertThat(inside).doesNotContainPattern("\\d{2,}").doesNotContain("R$").doesNotContain("%");
    }

    @Test
    void theHeroKeepsTheGreetingAndDateTheHomeScriptFills() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("id=\"hmH1\"").contains("id=\"hmDate\"").contains("href=\"#/mercados\"").contains("href=\"#/cockpit\"");
    }

    @Test
    void heroTextMeetsWcagAaOverTheWholeGradient() {
        String[] gradient = {"#1d3557", "#0f1d33", "#08111f"};
        for (String bg : gradient) {
            assertThat(VisualFoundationTest.contrast("#f3efe4", bg)).as("título sobre " + bg).isGreaterThanOrEqualTo(4.5);
            assertThat(VisualFoundationTest.contrast("#b8c0cf", bg)).as("texto secundário sobre " + bg).isGreaterThanOrEqualTo(4.5);
            assertThat(VisualFoundationTest.contrast("#e7cf9b", bg)).as("data sobre " + bg).isGreaterThanOrEqualTo(4.5);
        }
        assertThat(VisualFoundationTest.contrast("#0b1424", "#f3efe4")).as("botão principal").isGreaterThanOrEqualTo(4.5);
        assertThat(VisualFoundationTest.contrast("#f3efe4", "#0f1d33")).as("botão secundário").isGreaterThanOrEqualTo(4.5);
    }
}
