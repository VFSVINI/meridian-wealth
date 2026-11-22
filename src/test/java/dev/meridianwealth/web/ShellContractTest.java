package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Contrato do shell do protótipo aprovado (ADR 012) lido do próprio {@code index.html}: toda entrada de navegação ativa tem página,
 * páginas sem backend não existem (ficam desabilitadas e indicam o item do roadmap), os dois temas definem os mesmos papéis de cor,
 * os avisos de honestidade estão presentes e nenhum dado de exemplo do protótipo foi copiado.
 */
class ShellContractTest {
    private static String html;

    @BeforeAll
    static void load() throws IOException {
        html = dev.meridianwealth.testsupport.StaticPage.whole();
    }

    private record NavEntry(String id, String soon) {}

    private static List<NavEntry> nav() {
        int start = html.indexOf("const NAV=[");
        int end = html.indexOf("const icon=", start);
        assertThat(start).as("tabela NAV no index.html").isPositive();
        Matcher m = Pattern.compile("\\['([a-z]+)','[^']*'(?:,'([^']*)')?\\]").matcher(html.substring(start, end));
        List<NavEntry> out = new ArrayList<>();
        while (m.find()) out.add(new NavEntry(m.group(1), m.group(2)));
        return out;
    }

    private static List<String> pages() {
        Matcher m = Pattern.compile("<section class=\"page\" data-page=\"([a-z]+)\"").matcher(html);
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test void everyActiveNavigationEntryHasAPageAndNoPhantomPageExists() {
        List<String> pages = pages();
        for (NavEntry e : nav()) {
            if (e.soon() != null || e.id().equals("glossario")) {
                assertThat(pages).as("página sem backend não deve existir: " + e.id()).doesNotContain(e.id());
            } else {
                assertThat(pages).as("entrada de navegação sem página: " + e.id()).contains(e.id());
            }
        }
        assertThat(pages).allSatisfy(p -> assertThat(nav()).extracting(NavEntry::id).as("página fora da navegação: " + p).contains(p));
    }

    @Test void unavailableEntriesNameTheRoadmapItemThatDeliversThem() {
        List<NavEntry> soon = nav().stream().filter(e -> e.soon() != null).toList();
        assertThat(soon).extracting(NavEntry::id).contains("montecarlo", "correlacao", "stress", "alertas", "config");
        assertThat(soon).allSatisfy(e -> assertThat(e.soon()).matches("item #\\d+( e #\\d+)?|sob pedido"));
    }

    @Test void bothThemesDefineTheSameColorRoles() {
        String[] roles = {"--bg", "--panel", "--panel-2", "--popover", "--line", "--line-2", "--text", "--text-2", "--text-3", "--gold", "--up",
            "--down", "--warn", "--info"};
        int dark = html.indexOf(":root{--bg:");
        int ivory = html.indexOf(":root[data-theme=\"ivory\"]{");
        assertThat(dark).isNotNegative();
        assertThat(ivory).isGreaterThan(dark);
        String darkBlock = html.substring(dark, ivory);
        String ivoryBlock = html.substring(ivory, html.indexOf("}", ivory));
        for (String role : roles) {
            assertThat(darkBlock).as("tema escuro sem " + role).contains(role + ":");
            assertThat(ivoryBlock).as("tema Ivory sem " + role).contains(role + ":");
        }
        assertThat(darkBlock).contains("--bg:#0b0e13").contains("--gold:#c9a96a");
        assertThat(ivoryBlock).contains("--bg:#f5f1e8").contains("--gold:#85662a");
    }

    @Test void honestyNoticesAndAccessibilityHooksArePresent() {
        assertThat(html).contains("Não executa ordens e não é recomendação de investimento").contains("Nenhum preço é simulado");
        assertThat(html).contains("prefers-reduced-motion").contains("class=\"skip\"").contains("aria-current");
        assertThat(html).contains("SIMULAÇÃO").contains("TESES ILUSTRATIVAS");
    }

    @Test void closedMobileDrawerIsRemovedFromKeyboardAndScreenReaderOrder() {
        // transform sozinho só tira a gaveta da tela: o foco continuaria nos links invisíveis
        assertThat(html).containsPattern("\\.nav\\{position:fixed[^}]*visibility:hidden");
        assertThat(html).contains("body.navopen .nav{transform:none;visibility:visible");
        assertThat(html).contains("e.key==='Escape'&&document.body.classList.contains('navopen')");
    }

    @Test void searchLabelStaysOnOneLineInMediumWidths() {
        // sem nowrap o rótulo quebrava em 4 linhas num botão de 36 px (viewport de 920 px)
        assertThat(html).contains(".search{min-width:0;white-space:nowrap}").contains(".search span{overflow:hidden;text-overflow:ellipsis");
        assertThat(html).contains("@media(max-width:1100px){.pill.hide-sm,.search kbd{display:none}}");
    }

    @Test void thePrivacyPageCoversTheLgpdBasicsAndOffersTheDataSubjectActions() {
        assertThat(html).contains("Política de privacidade").contains("Termos de uso").contains("LGPD, art. 18").contains("base legal").contains("retenção")
                .contains("Baixar meus dados").contains("Excluir minha conta").contains("art. 48");
        assertThat(html).as("o cadastro só segue com o aceite dos termos").contains("id=\"lTerms\"").contains("aceite os termos de uso");
        assertThat(html).as("nenhum rastreador, anúncio ou análise de uso é prometido sem existir").doesNotContain("google-analytics").doesNotContain("gtag(");
    }

    @Test void prototypeSampleDataWasNotCopiedIntoTheInterface() {
        assertThat(html).doesNotContain("4.382.917").doesNotContain("Vinícius").doesNotContain("Meridian Score").doesNotContain(">exemplo<");
    }
}
