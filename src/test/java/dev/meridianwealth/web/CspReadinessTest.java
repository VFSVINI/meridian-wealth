package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A CSP do servidor proíbe script e estilo embutidos (ADR 013). Se alguém voltar a embutir código no HTML ou gerar marcação com
 * manipulador de evento, a página quebra no navegador sem que nenhum teste de API perceba: este teste pega isso antes.
 */
class CspReadinessTest {
    @Test void theHtmlHasNoInlineScriptOrStyleAndOnlyLoadsLocalFiles() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).doesNotContainPattern("<script(?![^>]*\\ssrc=)").doesNotContain("<style").doesNotContain("javascript:");
        assertThat(html).doesNotContainPattern("(?i)\\son[a-z]+\\s*=");
        List<String> refs = new ArrayList<>();
        // recursos carregados (script, link, img, iframe, source): só do próprio servidor. Links de navegação (<a>) podem apontar para fora,
        // desde que abram com noopener (teste abaixo): a atribuição obrigatória à TradingView é um deles
        Matcher m = Pattern.compile("<(?:script|link|img|iframe|source)\\b[^>]*?\\b(?:src|href)=\"([^\"]+)\"").matcher(html);
        while (m.find()) refs.add(m.group(1));
        assertThat(refs).isNotEmpty().allSatisfy(r -> assertThat(r).as("referência externa: " + r).matches("^(/|#).*"));
        assertThat(html).contains("<script src=\"/theme.js\">").contains("<script src=\"/app.js\">").contains("<script src=\"/auth.js\">").contains("<script src=\"/carteira.js\">").contains("<script src=\"/vendor/echarts.min.js\">").contains("<script src=\"/vendor/leaflet.js\">").contains("<script src=\"/mapa.js\">").contains("<script src=\"/shopping.js\">").contains("<script src=\"/cockpit.js\">").contains("<script src=\"/vendor/lightweight-charts.standalone.production.js\">").contains("<link rel=\"stylesheet\" href=\"/app.css\">");
    }

    @Test void theScriptDoesNotBuildCodeAtRuntimeNorGenerateInlineHandlers() throws IOException {
        String js = StaticPage.file("app.js") + StaticPage.file("theme.js") + StaticPage.file("auth.js") + StaticPage.file("carteira.js") + StaticPage.file("mapa.js") + StaticPage.file("shopping.js") + StaticPage.file("cockpit.js");
        assertThat(js).doesNotContain("eval(").doesNotContain("new Function(").doesNotContain("document.write(").doesNotContain("javascript:");
        assertThat(js).doesNotContainPattern("set(Timeout|Interval)\\(\\s*[\"'`]");
        assertThat(js).as("marcação gerada com manipulador de evento seria bloqueada pela CSP").doesNotContainPattern("(?i)\\son(click|change|input|submit|load|error|focus|blur|keydown)=[\"']");
    }

    @Test void noTokenIsEverPlacedInBrowserStorageAndTabsRenewOneAtATime() throws IOException {
        // o refresh token vive só num cookie HttpOnly; o access token, só na memória da aba (a demonstração, só em sessionStorage)
        String auth = StaticPage.file("auth.js");
        assertThat(auth).contains("'X-Session-Mode':'cookie'").contains("navigator.locks.request('mw-refresh'").contains("new BroadcastChannel('mw-auth')");
        assertThat(auth).as("nenhum token no localStorage").doesNotContain("localStorage.setItem(SESSION_KEY").doesNotContain("accessToken:d.accessToken,accessExpiresAt:d.accessExpiresAt,refreshToken");
        assertThat(auth).doesNotContainPattern("localStorage\\.setItem\\([^)]*(accessToken|refreshToken)");
        assertThat(auth).as("só o 401 da renovação encerra a sessão; rede fora do ar ou 5xx a mantêm").contains("e.definitive=true").contains("if(e&&e.definitive)")
                .doesNotContain("if(r.status===401&&SESS)saveSess(null)");
        String all = StaticPage.whole();
        assertThat(all).as("o código do navegador nunca lê nem guarda refreshToken").doesNotContain("d.refreshToken").doesNotContain("SESS.refreshToken");
    }

    @Test void theMapGroupsNearbyPinsSoNoPropertyHidesBehindAnother() throws IOException {
        // sem agrupar, 21 dos 32 imóveis ficavam cobertos por outro marcador no zoom inicial (medido no navegador)
        String map = StaticPage.file("mapa.js");
        assertThat(map).contains("function clusterGroups(").contains("MP_CLUSTER_PX").contains("MP.map.on('zoomend'").contains("mpin cluster");
        assertThat(map).as("o Leaflet é lido de window.L: o identificador L pertence ao gráfico de preço").contains("const LF=window.L").doesNotContainPattern("[^A-Za-z.]L\\.(map|marker|geoJSON|divIcon)\\(");
    }

    @Test void noTwoScriptsDeclareTheSameGlobalName() throws IOException {
        // os scripts compartilham o escopo global: uma função repetida em outro arquivo SOBRESCREVE a primeira em silêncio (aconteceu com renderList,
        // do mapa sobre o da lista de mercados) e um const repetido derruba o carregamento do arquivo
        java.util.Map<String, String> owner = new java.util.HashMap<>();
        List<String> clashes = new ArrayList<>();
        Pattern decl = Pattern.compile("^(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)|^(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)", Pattern.MULTILINE);
        for (String file : List.of("theme.js", "app.js", "auth.js", "carteira.js", "mapa.js", "shopping.js", "cockpit.js")) {
            Matcher m = decl.matcher(StaticPage.file(file));
            while (m.find()) {
                String name = m.group(1) != null ? m.group(1) : m.group(2);
                String prev = owner.putIfAbsent(name, file);
                if (prev != null && !prev.equals(file)) clashes.add(name + " (" + prev + " e " + file + ")");
            }
        }
        assertThat(clashes).as("nome global declarado em mais de um script").isEmpty();
    }

    @Test void externalLinksOpenedInANewTabNeverGiveTheOpenerBack() throws IOException {
        String all = StaticPage.file("app.js") + StaticPage.file("carteira.js") + StaticPage.file("mapa.js") + StaticPage.file("shopping.js") + StaticPage.file("cockpit.js") + StaticPage.file("index.html");
        Matcher m = Pattern.compile("<a [^>]*target=\"_blank\"[^>]*>").matcher(all);
        int found = 0;
        while (m.find()) {
            found++;
            assertThat(m.group()).as("link sem noopener: " + m.group()).contains("noopener");
        }
        assertThat(found).isPositive();
    }

    @Test void thereAreNoThirdPartyScriptsStylesFontsOrImagesAnywhere() throws IOException {
        // única exceção: o script do Google Identity Services, carregado só quando há MW_GOOGLE_CLIENT_ID (a CSP o libera só nesse caso)
        String gis = "https://accounts.google.com/gsi/client";
        assertThat(StaticPage.file("auth.js")).contains(gis);
        String all = StaticPage.whole().replace(gis, "");
        assertThat(all).doesNotContainPattern("(?i)(src|url\\()\\s*=?\\s*[\"']?https?://");
        assertThat(all).doesNotContain("googleapis.com").doesNotContain("cdnjs").doesNotContain("unpkg.com").doesNotContain("jsdelivr");
    }
}
