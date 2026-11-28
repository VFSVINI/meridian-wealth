package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Saúde dos dados no visual Cosmos (item #53, parte 1): constelação de fontes sobre o /api/status real, sem série inventada. */
class DataHealthPageContractTest {

    @Test
    void thePageAsksTheQuestionAndHasKpisConstellationSidePanelStatesAndKeepsTheTechnicalStatusCollapsed() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Posso confiar nos números que estou vendo?").contains("id=\"svKpis\"").contains("id=\"svSky\"").contains("id=\"svSide\"").contains("Constelação de fontes");
        assertThat(html).contains("Carregando").contains("Sem dado para este período").contains("Desatualizado").contains("Erro");
        assertThat(html).as("o status técnico antigo continua, recolhido").containsPattern("(?s)<details class=\"ck-full\">.*id=\"status\".*</details>");
        assertThat(html).contains("<script src=\"/saude.js\"></script>");
    }

    @Test
    void everyNumberComesFromTheStatusEndpointAndNoAvailabilityOrLatencySeriesIsInvented() throws IOException {
        String js = StaticPage.file("saude.js");
        assertThat(js).contains("mw:status").contains("p.callNanos / p.attempts / 1e6").doesNotContain("Math.random").doesNotContain("fetch(");
        assertThat(js).as("sem chamadas medidas fica 'não informado'").contains("Nenhuma chamada medida desde que o servidor ligou");
        assertThat(StaticPage.file("index.html")).contains("não guarda histórico de disponibilidade nem de tempo de resposta").contains("nada é inventado");
        assertThat(StaticPage.file("app.js")).contains("new CustomEvent('mw:status'");
    }

    @Test
    void theConstellationIsKeyboardReachableTextAlternativeExistsAndMotionRespectsReducedMotion() throws IOException {
        String js = StaticPage.file("saude.js"), css = StaticPage.file("cosmos.css");
        assertThat(js).contains("tabindex=\"0\" role=\"button\"").contains("aria-label=\"Constelação de fontes").contains("e.key === 'Enter'");
        assertThat(css).contains("@media(prefers-reduced-motion:reduce){.sv-orbit,.sv-ctr,.sv-sun{animation:none}}").contains("animation-play-state:paused");
        assertThat(css).as("no celular o SVG encolhe para metade: rótulos e estrelas crescem para continuar legíveis").contains(".sv-lbl{font-size:19px}");
    }
}
