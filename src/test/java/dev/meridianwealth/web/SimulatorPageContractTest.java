package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Simulador no visual Cosmos (item #53, parte 2): órbita da carteira sobre a conta do servidor, sem ordem enviada e sem número inventado. */
class SimulatorPageContractTest {

    @Test
    void thePageAsksTheQuestionStatesItIsASimulationAndKeepsTheAdvancedModeCollapsed() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("O que muda se eu registrar essa operação?").contains("O MeridianWealth não executa ordens").contains("id=\"smOrbit\"").contains("id=\"smFall\"").contains("id=\"smConc\"").contains("id=\"smSum\"");
        assertThat(html).as("o simulador antigo continua, recolhido").containsPattern("(?s)<details class=\"ck-full\">.*id=\"sm\".*</details>");
        assertThat(html).contains("<script src=\"/simulador.js\"></script>");
    }

    @Test
    void theImpactIsComputedByTheServerAndNothingIsSentOrStored() throws IOException {
        String js = StaticPage.file("simulador.js");
        assertThat(js).contains("fetch('/api/impact/pre-order'").contains("method: 'POST'").doesNotContain("Math.random").doesNotContain("localStorage").doesNotContain("sessionStorage");
        assertThat(js).as("sem cotação em reais não há quantidade estimada").contains("qty = r && u.currency === 'BRL'").contains("precisa de cotação em reais").contains("custos não incluídos");
    }

    @Test
    void theOrbitIsLabelledAndTheFallScenarioIsDeclaredHypothetical() throws IOException {
        String js = StaticPage.file("simulador.js"), html = StaticPage.file("index.html"), css = StaticPage.file("cosmos.css");
        assertThat(js).contains("Órbita da carteira: cada planeta é uma posição").contains("pontilhado: sem volatilidade");
        assertThat(html).contains("Cenário hipotético, não previsão");
        assertThat(css).contains(".sm-sky:hover .sm-r,.sm-sky:hover .sm-c{animation-play-state:paused}").contains("@media(prefers-reduced-motion:reduce){.sm-r,.sm-c,.sm-sun{animation:none}}");
    }

    @Test
    void theConcentrationNoteSpeaksOfThisAssetAndNamesTheLargestPositionFromTheApi() throws IOException {
        String js = StaticPage.file("simulador.js");
        assertThat(js).contains("Maior posição depois da ordem").contains("d.after.largestLabel").contains("(atenção a partir de 25%)");
    }
}
