package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Mercados e Ativo no visual Cosmos (item #48): estrutura do protótipo, só dado real, destaque automático com pausa e sem troca com movimento reduzido. */
class MarketsAndAssetPagesContractTest {

    @Test
    void marketsHasTheQuestionTitleSearchClassPillsAndThreeViews() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Como estão os mercados agora?").contains("id=\"mkPills\"").contains("data-mkv=\"lista\"").contains("data-mkv=\"tabela\"").contains("data-mkv=\"mapa\"")
                .contains("id=\"mkItems\"").contains("id=\"mkDetail\"").contains("id=\"mkPause\"").contains("id=\"mkMapa\" hidden");
        assertThat(html).as("o globo de pregões é a visão Mapa").containsPattern("(?s)id=\"mkMapa\" hidden>.*id=\"globeCanvas\"");
        assertThat(html).as("os blocos provisórios da página inicial saíram").doesNotContain("id=\"hmSignals\"").doesNotContain("id=\"hmMovers\"").doesNotContain("cs-legacy");
    }

    @Test
    void theFeaturedAssetRotatesEverySevenSecondsCanBePausedAndNeverRotatesWithReducedMotion() throws IOException {
        String js = StaticPage.file("mercados.js");
        assertThat(js).contains("const ROTATE_MS = 7000;").contains("setInterval(rotate, ROTATE_MS)").contains("if (!MK.auto || reduced.matches || document.hidden || !onPage() || MK.view !== 'lista') return;");
        assertThat(js).contains("if (fromUser && MK.auto) { MK.auto = false; }").contains("'Pausar troca' : 'Retomar troca'").contains("matchMedia('(prefers-reduced-motion: reduce)')");
        assertThat(StaticPage.file("cosmos.css")).contains("@keyframes mkProg").contains("@media(prefers-reduced-motion:reduce){.mk-prog i.run{animation:none}}");
    }

    @Test
    void everyNumberOnTheMarketsPageComesFromTheRealQuotesAndTheServerSparklines() throws IOException {
        String js = StaticPage.file("mercados.js"), app = StaticPage.file("app.js");
        assertThat(js).contains("fetch('/api/sparklines?bars=30')").contains("'/api/candles/' + encodeURIComponent(sym) + '?limit=140'");
        assertThat(js).doesNotContain("Math.random").doesNotContain("Date.now() %").doesNotContain("/ 100 *");
        assertThat(js).as("variação sem abertura é 'não informado', nunca infinito").contains("c.open > 0 ? c.close / c.open - 1 : null").contains("'não informado'");
        assertThat(js).as("ativo sem dado diz isso e não desenha série").contains("sem dado em tempo real").contains("Nada é estimado.");
        assertThat(app).contains("function renderList(){document.dispatchEvent(new Event('mw:mk-render'))}").contains("renderHome();renderList();");
    }

    @Test
    void theAssetPageAsksTheQuestionShowsTheOneSentenceReadingAndHidesTheRestBehindFullAnalysis() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Ativo: o que o gráfico diz?").contains("id=\"ckChips\"").contains("id=\"ckRead\"").contains("id=\"ckKnow\"").contains("<details class=\"ck-full\" id=\"ckFull\">").contains("Ver análise completa");
        assertThat(html).contains("data-ckt=\"entry\"").contains("data-ckt=\"targets\"").contains("data-ckt=\"invalid\"").contains("data-ckm=\"area\"").contains("data-ckm=\"candle\"");
        assertThat(html).as("o backtest, o timing e as métricas ficam dentro da análise completa").containsPattern("(?s)id=\"ckFull\".*id=\"btOut\".*id=\"timing\".*id=\"metrics\".*</details>");
        assertThat(html).as("a busca de ativo continua").contains("id=\"pickCockpit\"");
    }

    @Test
    void theReadingIsBuiltOnlyFromTheEngineOutputAndSaysWhenThereIsNoData() throws IOException {
        String js = StaticPage.file("ativo.js"), app = StaticPage.file("app.js");
        assertThat(app).contains("document.dispatchEvent(new Event('mw:ativo-render'));");
        assertThat(js).contains("const confidence = n => n < 40 ? 'Baixa' : n < 70 ? 'Média' : 'Alta';").contains("(A.factors || []).find(x => x.side === 'COMPRA')").contains("Fechar abaixo de ${f(A.invalidation)} invalida a leitura.");
        assertThat(js).contains("Sem dado em tempo real para este ativo").contains("Nada é estimado.").contains("análise, não ordem").contains("Não é ordem nem recomendação.");
        assertThat(js).doesNotContain("Math.random").doesNotContain("innerHTML = rows");
    }

    @Test
    void theKnowYourAssetTabsUseGeneralClassTextAndRealNumbersNeverCompanyFactsThePlatformDoesNotHave() throws IOException {
        String js = StaticPage.file("ativo.js");
        assertThat(js).contains("['resumo', 'Resumo'], ['feito', 'Do que é feito'], ['riscos', 'Riscos'], ['carteira', 'Na carteira'], ['metricas', 'Janela e métricas']");
        assertThat(js).contains("fetch('/api/composition/' + encodeURIComponent(sym))").contains("não informado");
        assertThat(js).as("texto educativo geral por classe, sem afirmar nada de uma empresa").doesNotContain("Petrobras").doesNotContain("Vale S.A.");
        assertThat(js).contains("'zscore'").contains("'relative-strength'");
    }

    @Test
    void theChartHasAreaAndCandleModesAndEachLevelLayerIsIndependent() throws IOException {
        String ck = StaticPage.file("cockpit.js");
        assertThat(ck).contains("mode:'area'").contains("show:{bb:false,sma:false,entry:true,targets:true,invalid:true}").contains("function applyMode()").contains("addAreaSeries(");
        assertThat(ck).contains("if(CK.show.entry&&A.score>=20").contains("if(CK.show.invalid)add(A.invalidation,k.down,'Invalidação')").contains("if(CK.show.targets){add(A.target1,k.warn,'Alvo 1');add(A.target2,k.warn,'Alvo 2')}");
        assertThat(ck).as("as linhas pendem da série à vista").contains("CK.lineHost=CK.mode==='area'?CK.area:CK.candle;");
    }

    @Test
    void redrawingOnEachQuoteKeepsKeyboardFocusAndScrollSkipsUnchangedHtmlAndTheDetailIsNotALiveRegion() throws IOException {
        // medido: a cada cotação a lista e o painel eram reescritos (11 vezes em 2 s) e o foco ia para o BODY; o painel era aria-live
        String js = StaticPage.file("mercados.js"), html = StaticPage.file("index.html");
        assertThat(js).contains("function paint(el, html)").contains("if (painted.get(el) === html) return;").contains("for (const k of ['mks', 'mkr', 'mko', 'mkc'])").contains("b.focus({preventScroll: true})").contains("el.scrollTop = top;");
        assertThat(js).as("nenhuma escrita direta fora do paint").doesNotContain("$('#mkDetail').innerHTML").doesNotContain("$('#mkItems').innerHTML").doesNotContain("$('#list').innerHTML");
        assertThat(html).contains("<div class=\"mk-detail\" id=\"mkDetail\">").doesNotContain("id=\"mkDetail\" aria-live");
    }
}
