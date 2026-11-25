package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Fundação visual (item #30): contraste medido, seletor de ativo com busca, saudação por horário e escala em telas médias. */
class VisualFoundationTest {

    private static Map<String, String> tokens(String block) {
        Map<String, String> m = new HashMap<>();
        Matcher t = Pattern.compile("--([a-z0-9-]+):(#[0-9a-fA-F]{6})").matcher(block);
        while (t.find()) m.put(t.group(1), t.group(2));
        return m;
    }

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contrast(String a, String b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Tokens do tema escuro (no :root) e do claro (o :root do ivory, com as sobreposições do item #30 por cima). */
    private static Map<String, String> theme(boolean ivory) throws IOException {
        String css = StaticPage.file("app.css");
        Map<String, String> m = tokens(css.substring(css.indexOf(":root{"), css.indexOf("color-scheme:dark")));
        if (ivory) {
            Matcher blocks = Pattern.compile(":root\\[data-theme=\"ivory\"\\]\\{([^}]*)\\}").matcher(css);
            while (blocks.find()) m.putAll(tokens(blocks.group(1)));
        }
        return m;
    }

    @Test
    void everyTextColorMeetsWcagAaOnEverySurfaceInBothThemes() throws IOException {
        for (boolean ivory : new boolean[] {false, true}) {
            Map<String, String> t = theme(ivory);
            for (String fg : new String[] {"text", "text-2", "text-3", "gold", "gold-2", "up", "down", "warn", "info"}) {
                for (String bg : new String[] {"bg", "panel", "panel-2", "popover"}) {
                    assertThat(contrast(t.get(fg), t.get(bg))).as("%s sobre %s no tema %s", fg, bg, ivory ? "claro" : "escuro").isGreaterThanOrEqualTo(4.5);
                }
            }
        }
    }

    @Test
    void theLightThemeCardsStandOutFromThePageAndTheirBordersAreVisible() throws IOException {
        Map<String, String> t = theme(true);
        assertThat(t.get("panel")).as("cartão branco sobre o fundo creme").isNotEqualTo(t.get("bg"));
        assertThat(contrast(t.get("line-2"), t.get("panel"))).as("borda de controle sobre o cartão (componente de interface, 3:1)").isGreaterThanOrEqualTo(3.0);
        assertThat(StaticPage.file("app.css")).contains(":root[data-theme=\"ivory\"] .card{box-shadow");
    }

    @Test
    void noRuleMakesEveryCanvasOpaqueBecauseTheChartsStackTransparentLayers() throws IOException {
        // regressão do próprio item #30: pintar todo canvas de branco no tema claro escondia as camadas de baixo do gráfico
        assertThat(Pattern.compile("(^|[,}\\s])canvas\\{[^}]*background").matcher(StaticPage.file("app.css")).find()).isFalse();
    }

    @Test
    void theAssetPickerIsASearchableAccessibleComboboxLoadedBeforeTheApp() throws IOException {
        String html = StaticPage.file("index.html"), picker = StaticPage.file("picker.js");
        assertThat(html).doesNotContain("<select class=\"symPick\"");
        assertThat(html.indexOf("/picker.js")).isPositive().isLessThan(html.indexOf("/app.js"));
        assertThat(html).contains("id=\"pickCockpit\"").contains("id=\"pickRadar\"").contains("id=\"pickComp\"");
        assertThat(picker).contains("role=\"combobox\"").contains("aria-expanded").contains("aria-controls").contains("aria-activedescendant").contains("role=\"listbox\"")
                .contains("role=\"option\"").contains("ArrowDown").contains("ArrowUp").contains("Enter").contains("Escape");
        assertThat(picker).as("o texto vem escapado").contains("&amp;");
        assertThat(StaticPage.file("app.js")).contains("mountAssetPicker(").contains("setAsset(");
    }

    @Test
    void theHomeIsAPresentationWithoutAGreetingOrAToyObject() throws IOException {
        // no protótipo Cosmos a página inicial apresenta a plataforma (o relógio e a saudação saíram) e não tem objeto 3D para "arrastar"
        String js = StaticPage.file("app.js"), html = StaticPage.file("index.html");
        assertThat(js).doesNotContain("function greeting(").doesNotContain("hmH1").doesNotContain("hmDate").doesNotContain("hour12:false");
        assertThat(html).doesNotContain("Arraste para girar</span></div>").doesNotContain("heroCanvas");
    }

    @Test
    void mediumScreensStartWithTheSidebarCollapsedAndTheUserChoiceWins() throws IOException {
        String js = StaticPage.file("app.js");
        assertThat(js).contains("matchMedia('(max-width:1279px)')").contains("pref==='collapsed'").contains("!pref");
        assertThat(StaticPage.file("app.css")).contains("body.collapsed .grid2").contains("clamp(");
    }
}
