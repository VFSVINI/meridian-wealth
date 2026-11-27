package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Brasil: juros e inflação no visual Cosmos (item #50): só o histórico oficial do Banco Central, contas simples rotuladas e "não informado" onde falta. */
class BrazilPageContractTest {

    @Test
    void thePageHasTheQuestionThreeChartCardsExplanationTabsRealRateTimelineAndTheAcademyLink() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("O que os juros e a inflação fazem com o meu dinheiro?").contains("id=\"brCards\"").contains("O que isso muda no seu bolso").contains("data-brt=\"selic\"")
                .contains("data-brt=\"inflacao\"").contains("data-brt=\"dolar\"").contains("id=\"brReal\"").contains("id=\"brTimeline\"").contains("href=\"#/academia/m9/m9c1\"");
        assertThat(html).as("a lista completa de indicadores continua, recolhida").containsPattern("(?s)<details class=\"ck-full\">.*id=\"br\".*</details>");
    }

    @Test
    void everyNumberComesFromTheOfficialHistoryEndpointAndNothingIsEstimated() throws IOException {
        String js = StaticPage.file("brasil.js");
        assertThat(js).contains("fetch('/api/brazil/history')").doesNotContain("Math.random").doesNotContain("setInterval(() => { BR.data");
        assertThat(js).as("falta de dado é 'não informado', nunca zero").contains("const ND = '<span class=\"gap\">não informado</span>';").contains("O Banco Central não entregou esta série agora. Nada é estimado.");
        assertThat(js).as("as contas dos exemplos dizem que são contas simples").contains("(conta simples sobre a meta atual)").contains("(conta simples sobre o acumulado)").contains("(conta simples pela taxa de referência)");
        assertThat(js).contains("Juro real = (1 + taxa) ÷ (1 + IPCA de 12 meses) − 1").contains("Antes de imposto e taxas.");
    }

    @Test
    void theTimelineOnlyShowsRealMeetingsThatChangedTheTargetAndSaysTheOthersAreNotInThePublicSeries() throws IOException {
        String js = StaticPage.file("brasil.js");
        assertThat(js).contains("Só aparecem as reuniões em que a meta mudou: a série pública do Banco Central não registra as que a deixaram igual.").contains("x.direction === 'ALTA' ? 'Meta sobe' : 'Meta cai'");
        assertThat(js).as("queda de juros não é 'ruim': classes neutras, sem a cor de perda").contains("'rise' : 'fall'").doesNotContain("'ALTA' ? 'up' : 'dn'");
    }

    @Test
    void theBrazilScriptIsLoadedByThePage() throws IOException {
        assertThat(StaticPage.file("index.html")).contains("<script src=\"/brasil.js\"></script>");
    }
}
