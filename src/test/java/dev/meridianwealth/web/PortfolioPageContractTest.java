package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Minha Carteira no visual Cosmos (item #52, parte 1): a pergunta, as três abas, os três números, a rosca e o mapa das posições, tudo sobre os dados reais do usuário. */
class PortfolioPageContractTest {

    @Test
    void thePageAsksTheQuestionAndGroupsTheExistingCardsIntoThreeTabs() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Quanto eu tenho, onde e com que risco?").contains("data-ctt=\"visao\"").contains("data-ctt=\"posicoes\"").contains("data-ctt=\"lancamentos\"").contains("id=\"ctKpis\"")
                .contains("id=\"ctDonut\"").contains("id=\"ctPosMap\"");
        assertThat(html).contains("id=\"ctPos\" data-ctg=\"posicoes\"").contains("id=\"ctBook\" data-ctg=\"lancamentos\"").contains("id=\"ctAdh\" data-ctg=\"visao\"");
        assertThat(StaticPage.file("cosmos.css")).contains("#ctBody[data-tab=\"visao\"] [data-ctg]:not([data-ctg=\"visao\"])");
    }

    @Test
    void theDonutUsesTheApiPercentagesAndTheMapOnlyDrawsPositionsWithAValueInReais() throws IOException {
        String js = StaticPage.file("carteira.js");
        assertThat(js).contains("Number(w.percent)").contains("function layoutTree(").contains("weights().filter(x=>x.w!=null&&x.w>0)");
    }

    @Test
    void missingDataIsNotInformedAndRealizedResultComesOnlyFromRegisteredSales() throws IOException {
        String js = StaticPage.file("carteira.js");
        assertThat(js).contains("e.entryType==='SELL'&&e.realizedPnlBrl!=null").contains("nenhuma venda registrada").contains("sem cotação para medir").contains("withPnl.length?").contains("200 últimos lançamentos");
    }
}
