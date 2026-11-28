package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.Exchange;
import dev.meridianwealth.infrastructure.provider.Universe;
import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Cripto 24h no visual Cosmos (item #54, parte 1): só cotação e volume reais, nada de negócio a negócio ou de valor de mercado inventado. */
class CryptoPageContractTest {

    @Test
    void thePageAsksTheQuestionAndHasLiveChartPriceSolarSystemDetailShareAndTable() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("O que está acontecendo no cripto agora, a qualquer hora?").contains("id=\"crChart\"").contains("id=\"crGal\"").contains("id=\"crDet\"").contains("id=\"crDon\"").contains("id=\"crTbl\"")
                .contains("id=\"crOpen\"").contains("Sistema de moedas");
        assertThat(html).contains("<script src=\"/cripto.js\"></script>");
        assertThat(StaticPage.file("app.js")).as("a entrada de navegação deixou de ser 'em breve'").contains("['cripto','Cripto 24h']").doesNotContain("'item #54'");
    }

    @Test
    void whatTheStreamDoesNotCarryIsNotDrawnAndWhatIsNotAProjectSourceIsSaidOutLoud() throws IOException {
        String html = StaticPage.file("index.html"), js = StaticPage.file("cripto.js");
        assertThat(html).contains("não traz negócio a negócio nem o lado de compra e venda").contains("valor de mercado não é fonte do projeto").contains("Não é a dominância por valor de mercado");
        assertThat(html).contains("A variação de 1 hora não aparece porque o projeto não guarda preço por hora.");
        assertThat(js).doesNotContain("Math.random").doesNotContain("compra</").contains("não informado").contains("volume negociado em 24 horas");
        assertThat(js).as("preço e volume vêm das cotações da API").contains("rows[u.symbol]").contains("c.volume").contains("/api/sparklines");
    }

    @Test
    void theLiveChartNeverStretchesATinyMoveIntoAMountainAndRedrawsKeepKeyboardFocus() throws IOException {
        String js = StaticPage.file("cripto.js");
        assertThat(js).contains("const min = hi * 0.002;").contains("n.focus({preventScroll: true})").contains("['crl', 'crc', 'crw', 'crr', 'cra', 'crh']");
        assertThat(StaticPage.file("cosmos.css")).contains("@media(prefers-reduced-motion:reduce){.cr-pl,.cr-ctr{animation:none}}").contains(".cr-gal:hover .cr-pl");
    }

    @Test
    void theTwelveCoinsAreInTheUniverseAndEveryOneHasASourceForItsDescription() throws IOException {
        var coins = Universe.all().stream().filter(e -> e.instrument().exchange() == Exchange.CRYPTO).map(e -> e.instrument().symbol()).toList();
        assertThat(coins).containsExactlyInAnyOrder("BTC", "ETH", "SOL", "XRP", "BNB", "DOGE", "ADA", "AVAX", "LINK", "DOT", "LTC", "TRX");
        String js = StaticPage.file("cripto.js");
        for (String c : coins) assertThat(js).as("texto educativo de " + c).contains("    " + c + ": '");
        assertThat(Universe.all().stream().filter(e -> e.symbols().crypto() != null).allMatch(e -> e.symbols().yahoo() != null && e.symbols().yahoo().endsWith("-USD"))).as("histórico diário de cada moeda pela Yahoo").isTrue();
    }

    @Test
    void theClockAndTheFearGreedGaugeComeFromTheInsightsEndpointAndSayWhenTheSourcesFailed() throws IOException {
        String html = StaticPage.file("index.html"), js = StaticPage.file("cripto.js");
        assertThat(html).contains("Relógio de 24 horas").contains("id=\"crClock\"").contains("id=\"crFg\"").contains("Fonte: alternative.me").contains("horário de Brasília");
        assertThat(js).contains("fetch('/api/crypto/insights')").contains("data-crh").contains("As bolsas não entregaram as barras horárias agora").contains("O índice não respondeu agora");
        assertThat(js).as("hora sem dado fica tracejada, nunca zero").contains("x.relative == null").contains("cr-hb");
        assertThat(js).as("o relógio também devolve o foco ao redesenhar").contains("'crh'");
        assertThat(StaticPage.file("README.md".replace("README.md", "cosmos.css"))).contains(".cr-hb.nd");
    }
}
