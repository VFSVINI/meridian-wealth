package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Mapa de ativos no visual Cosmos (item #53, parte 4): aba Mundo com as bolsas sobre cotações reais e aba Brasil com os imóveis dos fundos, como já era. */
class AssetMapPageContractTest {

    @Test
    void thePageAsksTheQuestionHasWorldAndBrazilTabsAndKeepsTheFundPropertyMapInsideTheBrazilTab() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Onde estão os ativos que eu acompanho?").contains("data-mpt=\"mundo\"").contains("data-mpt=\"brasil\"").contains("id=\"mwMap\"").contains("id=\"mwSide\"").contains("id=\"mwKinds\"");
        assertThat(html).as("o mapa dos imóveis dos fundos continua, dentro da aba Brasil").containsPattern("(?s)<div id=\"mpBrasil\" hidden>.*id=\"mpMap\".*id=\"mpList\".*id=\"mpCompare\".*</div>\\s*</section>");
        assertThat(html).contains("<script src=\"/mundo.js\"></script>");
    }

    @Test
    void worldNumbersComeFromTheQuotesAndMissingDataIsNotInformedNotNeutral() throws IOException {
        String js = StaticPage.file("mundo.js");
        assertThat(js).contains("rows[u.symbol]").contains("hasData(rows[u.symbol])").contains("/data/countries.geojson").doesNotContain("Math.random").doesNotContain("fetch('/api");
        assertThat(js).contains("não informado").contains("sem ativo conectado").contains("rows[u.symbol].marketOpen");
        assertThat(js).contains("Países sem ativo conectado aparecem tracejados");
    }

    @Test
    void everyExchangeOfTheUniverseHasAPlaceAndTheOnesWithoutAreSaidOutLoud() throws IOException {
        String js = StaticPage.file("mundo.js");
        for (String ex : new String[] {"B3", "NYSE", "XETRA", "EURONEXT_AMS", "LSE", "TSE", "HKEX", "ASX", "TADAWUL", "JSE"}) assertThat(js).as(ex).contains(ex + ":");
        assertThat(js).contains("Sem lugar no mapa").contains("negociam 24 horas");
    }

    @Test
    void theBrazilTabResizesTheLeafletMapWhenShownAndTheClassFilterOnlyAppliesToTheWorld() throws IOException {
        String js = StaticPage.file("mundo.js");
        assertThat(js).as("a cada cotação o mapa se redesenha: o foco volta ao mesmo elemento").contains("n.focus({preventScroll: true})").contains("['mwx', 'mwa', 'mwk']");
        assertThat(js).contains("MP.map.invalidateSize()").contains("$('#mwKinds').hidden = t !== 'mundo';");
        assertThat(StaticPage.file("cosmos.css")).contains("@media(prefers-reduced-motion:reduce){.mw-arc{animation:none}}");
    }
}
