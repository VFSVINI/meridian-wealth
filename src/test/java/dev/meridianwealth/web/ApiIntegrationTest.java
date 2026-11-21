package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Sobe a aplicação inteira com respostas reais gravadas da Yahoo para SPY e AAPL. Os outros ativos
 * não têm fixture (HTTP 404) e precisam aparecer como "sem dado em tempo real", nunca simulados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "meridianwealth.crypto.stream.enabled=false")
class ApiIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private dev.meridianwealth.infrastructure.bcb.LiveBrazilRates brazilRates;

    @SuppressWarnings("unchecked")
    private Map<String, Object> tick(String symbol) {
        Map<String, Object> body = rest.getForObject("/api/overview", Map.class);
        return ((List<Map<String, Object>>) body.get("ticks")).stream()
                .filter(t -> symbol.equals(t.get("symbol"))).findFirst().orElseThrow();
    }

    @Test void compositionServesSourceDateAndExplicitGaps() {
        Map<?, ?> knri = rest.getForObject("/api/composition/knri11", Map.class);
        assertThat(knri.get("symbol")).isEqualTo("KNRI11");
        assertThat(((Map<?, ?>) knri.get("source")).get("kind")).isEqualTo("OFFICIAL");
        assertThat(knri.get("declaredAssetCount")).isEqualTo(20);
        assertThat(knri.get("listedAssetCount")).isEqualTo(17);
        List<Map<String, Object>> assets = (List<Map<String, Object>>) knri.get("assets");
        Map<String, Object> without = assets.stream().filter(a -> a.get("coordinates") == null).findFirst().orElseThrow();
        assertThat((String) without.get("noCoordinatesReason")).isNotBlank();
        Map<String, Object> with = assets.stream().filter(a -> a.get("coordinates") != null).findFirst().orElseThrow();
        assertThat((String) ((Map<?, ?>) with.get("coordinates")).get("satelliteUrl")).contains("basemap=satellite");
        assertThat((String) knri.get("attribution")).contains("OpenStreetMap");
        assertThat(knri.get("staleAfterDays")).isEqualTo(135);
        assertThat(knri.get("dataAgeDays")).isNotNull();
        assertThat(knri.get("stale")).isNotNull();
    }

    @Test void compositionGeoListsOnlyValidatedPoints() {
        Map<?, ?> geo = rest.getForObject("/api/composition/geo", Map.class);
        assertThat((List<?>) geo.get("points")).hasSize(32);
    }

    @Test void compositionWithoutCuratedDataIs404() {
        var response = rest.getForEntity("/api/composition/PETR4", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat((String) response.getBody().get("error")).contains("Sem composição publicada");
    }

    @Test void reportsRadarShowsVerdictWithNumbersRuleAndCalendar() {
        Map<?, ?> petr = rest.getForObject("/api/reports/petr4", Map.class);
        assertThat(petr.get("symbol")).isEqualTo("PETR4");
        assertThat((String) petr.get("rule")).contains("limiar").contains("POSITIVO");
        assertThat((String) petr.get("disclaimer")).contains("não é recomendação");
        List<Map<String, Object>> reports = (List<Map<String, Object>>) petr.get("reports");
        Map<String, Object> q3 = reports.stream().filter(r -> "3T2026".equals(r.get("label"))).findFirst().orElseThrow();
        assertThat(q3.get("verdict")).isEqualTo("POSITIVE");
        assertThat(q3.get("verdictLabel")).isEqualTo("POSITIVO");
        List<Map<String, Object>> signals = (List<Map<String, Object>>) q3.get("signals");
        assertThat(signals).hasSize(2);
        assertThat(signals.get(0).get("thresholdPercent")).isNotNull();
        assertThat((String) signals.get(0).get("explanation")).contains("contra").contains("Limiar");
        List<Map<String, Object>> calendar = (List<Map<String, Object>>) petr.get("calendar");
        assertThat(calendar).isNotEmpty().allSatisfy(c -> assertThat((String) c.get("rule")).contains("Resolução CVM"));
        assertThat(petr.get("nextDeadline")).isNotNull();
        assertThat(petr.get("staleAfterDays")).isEqualTo(45);
        assertThat(petr.get("dataAgeDays")).isNotNull();
        assertThat(petr.get("stale")).isNotNull();
    }

    @Test void reportsSummaryListsEveryCuratedIssuer() {
        List<?> all = rest.getForObject("/api/reports", List.class);
        assertThat(all).hasSize(7);
    }

    @Test void reportsForUnsupportedAssetIs404WithAClearReason() {
        var response = rest.getForEntity("/api/reports/AAPL", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat((String) response.getBody().get("error")).contains("Sem relatórios publicados");
    }

    private static Map<String, String> boldestAnswers() {
        return new java.util.HashMap<>(Map.of("HORIZON", "OVER_10Y", "DRAWDOWN", "BUY_MORE", "PURPOSE", "GROW", "INCOME", "LARGE_SURPLUS",
                "WEALTH_SHARE", "UNDER_30", "LIQUIDITY_NEED", "NO", "EMERGENCY_RESERVE", "OVER_6M", "FAMILIARITY", "COMPLEX",
                "EXPERIENCE", "OVER_5Y"));
    }

    @Test void suitabilityQuestionnaireCitesTheLawAndTheLimitation() {
        Map<?, ?> q = rest.getForObject("/api/suitability/questionnaire", Map.class);
        assertThat((List<?>) q.get("questions")).hasSize(9);
        assertThat((String) q.get("disclaimer")).contains("não substitui a análise de um profissional habilitado");
        assertThat(q.get("maxTotalPoints")).isEqualTo(36);
        assertThat((List<?>) q.get("caps")).hasSize(4);
    }

    @Test void suitabilityEvaluatesWithCapsAndReturnsTheModelPortfolio() {
        Map<?, ?> top = rest.postForObject("/api/suitability/evaluate", Map.of("answers", boldestAnswers()), Map.class);
        assertThat(top.get("profile")).isEqualTo("AGGRESSIVE");
        assertThat(top.get("profileLabel")).isEqualTo("AGRESSIVO");
        Map<?, ?> portfolio = (Map<?, ?>) top.get("modelPortfolio");
        assertThat((List<?>) portfolio.get("targets")).hasSize(6);
        Map<String, String> shortHorizon = boldestAnswers();
        shortHorizon.put("HORIZON", "UP_TO_1Y");
        Map<?, ?> capped = rest.postForObject("/api/suitability/evaluate", Map.of("answers", shortHorizon), Map.class);
        assertThat(capped.get("scoreProfile")).isEqualTo("AGGRESSIVE");
        assertThat(capped.get("profile")).isEqualTo("CONSERVATIVE");
        assertThat((List<?>) capped.get("appliedCaps")).hasSize(1);
    }

    @Test void suitabilityIncompleteAnswersAre400() {
        Map<String, String> partial = boldestAnswers();
        partial.remove("INCOME");
        var response = rest.postForEntity("/api/suitability/evaluate", Map.of("answers", partial), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat((String) response.getBody().get("error")).contains("INCOME");
    }

    @Test void modelPortfoliosListEveryProfileWithTargetsSummingToOneHundred() {
        Map<?, ?> m = rest.getForObject("/api/suitability/model-portfolios", Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) m.get("portfolios");
        assertThat(list).hasSize(4);
        for (Map<String, Object> p : list) {
            double sum = ((List<Map<String, Object>>) p.get("targets")).stream().mapToDouble(t -> ((Number) t.get("percent")).doubleValue()).sum();
            assertThat(sum).isEqualTo(100.0);
        }
    }

    @Test void adherenceReportsDeviationPerClassAndRejectsInvalidInput() {
        Map<String, Object> body = Map.of("profile", "MODERATE", "holdings", List.of(Map.of("type", "ACAO", "valueBrl", "700"),
                Map.of("type", "TESOURO_SELIC", "valueBrl", "300")));
        Map<?, ?> ok = rest.postForObject("/api/suitability/adherence", body, Map.class);
        assertThat((List<?>) ok.get("classes")).hasSize(6);
        assertThat(ok.get("classesOutsideBand")).isNotNull();
        assertThat((String) ok.get("disclaimer")).contains("nada aqui é recomendação");
        var index = rest.postForEntity("/api/suitability/adherence", Map.of("profile", "MODERATE",
                "holdings", List.of(Map.of("type", "INDICE", "valueBrl", "100"))), Map.class);
        assertThat(index.getStatusCode().value()).isEqualTo(400);
        var huge = rest.postForEntity("/api/suitability/adherence", "{\"profile\":\"MODERATE\",\"holdings\":[{\"type\":\"ACAO\",\"valueBrl\":1E999999999},"
                + "{\"type\":\"TESOURO_SELIC\",\"valueBrl\":1}]}", Map.class);
        assertThat(huge.getStatusCode().value()).isIn(400, 415);
        var empty = rest.postForEntity("/api/suitability/adherence", Map.of("profile", "MODERATE", "holdings", List.of()), Map.class);
        assertThat(empty.getStatusCode().value()).isEqualTo(400);
        var badType = rest.postForEntity("/api/suitability/adherence", Map.of("profile", "MODERATE",
                "holdings", List.of(Map.of("type", "NAO_EXISTE", "valueBrl", "100"))), Map.class);
        assertThat(badType.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void glossaryServesEveryEntryAndUnknownIdIs404() {
        Map<?, ?> g = rest.getForObject("/api/glossary", Map.class);
        assertThat((List<?>) g.get("entries")).hasSizeGreaterThanOrEqualTo(55);
        Map<?, ?> score = rest.getForObject("/api/glossary/score", Map.class);
        assertThat(score.get("term")).isEqualTo("Score do timing");
        assertThat((String) score.get("whenToWorry")).isNotBlank();
        var missing = rest.getForEntity("/api/glossary/nao-existe", Map.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
    }

    @Test void preOrderSimulationIsLabeledAsSimulationAndUsesRealVolatility() {
        Map<String, Object> body = Map.of("positions", List.of(Map.of("symbol", "SPY", "valueBrl", "6000"), Map.of("type", "TESOURO_SELIC", "valueBrl", "4000")),
                "order", Map.of("side", "BUY", "symbol", "AAPL", "valueBrl", "2000"), "profile", "MODERATE");
        Map<?, ?> r = rest.postForObject("/api/impact/pre-order", body, Map.class);
        assertThat(r.get("simulation")).isEqualTo(true);
        assertThat((String) r.get("label")).contains("não é cotação").contains("não executa ordens");
        Map<?, ?> before = (Map<?, ?>) r.get("before");
        Map<?, ?> after = (Map<?, ?>) r.get("after");
        assertThat(before.get("positions")).isEqualTo(2);
        assertThat(after.get("positions")).isEqualTo(3);
        assertThat(before.get("weightedVolatility")).as("volatilidade real do SPY").isNotNull();
        assertThat(((Number) before.get("hhi")).doubleValue()).isEqualTo(5200.0);
        assertThat((List<?>) r.get("assumptions")).isNotEmpty();
        assertThat((String) r.get("disclaimer")).contains("Nada aqui é recomendação");
        assertThat(r.get("adherenceBefore")).isNotNull();
    }

    @Test void preOrderSimulationRejectsInvalidInputWithClearErrors() {
        Map<String, Object> sellTooMuch = Map.of("positions", List.of(Map.of("symbol", "SPY", "valueBrl", "100")),
                "order", Map.of("side", "SELL", "symbol", "SPY", "valueBrl", "100.01"));
        var tooMuch = rest.postForEntity("/api/impact/pre-order", sellTooMuch, Map.class);
        assertThat(tooMuch.getStatusCode().value()).isEqualTo(400);
        assertThat((String) tooMuch.getBody().get("error")).contains("passa do valor da posição");
        Map<String, Object> unknown = Map.of("positions", List.of(), "order", Map.of("side", "BUY", "symbol", "NAOEXISTE", "valueBrl", "10"));
        assertThat((String) rest.postForEntity("/api/impact/pre-order", unknown, Map.class).getBody().get("error")).contains("fora do universo");
        Map<String, Object> index = Map.of("positions", List.of(), "order", Map.of("side", "BUY", "type", "INDICE", "valueBrl", "10"));
        assertThat(rest.postForEntity("/api/impact/pre-order", index, Map.class).getStatusCode().value()).isEqualTo(400);
        var huge = rest.postForEntity("/api/impact/pre-order", "{\"positions\":[],\"order\":{\"side\":\"BUY\",\"type\":\"ACAO\",\"valueBrl\":1E999999999}}", Map.class);
        assertThat(huge.getStatusCode().value()).isIn(400, 415);
        var noOrder = rest.postForEntity("/api/impact/pre-order", Map.of("positions", List.of()), Map.class);
        assertThat(noOrder.getStatusCode().value()).isEqualTo(400);
    }

    @Test void thesisLensTranslatesPortfolioAndStatesThatThesesAreIllustrative() {
        Map<String, Object> body = Map.of("positions", List.of(Map.of("symbol", "AAPL", "valueBrl", "6000"), Map.of("type", "TESOURO_SELIC", "valueBrl", "4000")));
        Map<?, ?> r = rest.postForObject("/api/thesis/lens", body, Map.class);
        assertThat(r.get("illustrative")).isEqualTo(true);
        assertThat((String) r.get("disclaimer")).contains("Não representam posições reais de nenhuma instituição");
        Map<?, ?> geo = (Map<?, ?>) r.get("geopolitics");
        assertThat(((Number) geo.get("unclassifiedPercent")).doubleValue()).isEqualTo(40.0);
        List<Map<String, Object>> sectors = (List<Map<String, Object>>) geo.get("sectors");
        assertThat(sectors).hasSize(1);
        assertThat(sectors.get(0).get("sector")).isEqualTo("tecnologia");
        assertThat(((Number) sectors.get(0).get("exposurePercent")).doubleValue()).isEqualTo(60.0);
        assertThat((List<?>) geo.get("assumptions")).isNotEmpty();
        assertThat((List<?>) r.get("readings")).hasSize(5);
        assertThat(r.get("macro")).isNotNull();
        Map<?, ?> catalog = rest.getForObject("/api/thesis", Map.class);
        assertThat((List<?>) catalog.get("theses")).hasSize(5);
        assertThat((String) catalog.get("disclaimer")).contains("ilustrativas");
    }

    @Test void thesisLensRejectsInvalidInputWithClearErrors() {
        assertThat(rest.postForEntity("/api/thesis/lens", Map.of("positions", List.of()), Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(rest.postForEntity("/api/thesis/lens", Map.of(), Map.class).getStatusCode().value()).isEqualTo(400);
        var unknown = rest.postForEntity("/api/thesis/lens", Map.of("positions", List.of(Map.of("symbol", "NAOEXISTE", "valueBrl", "10"))), Map.class);
        assertThat((String) unknown.getBody().get("error")).contains("fora do universo");
        var index = rest.postForEntity("/api/thesis/lens", Map.of("positions", List.of(Map.of("type", "INDICE", "valueBrl", "10"))), Map.class);
        assertThat(index.getStatusCode().value()).isEqualTo(400);
        var huge = rest.postForEntity("/api/thesis/lens", "{\"positions\":[{\"type\":\"ACAO\",\"valueBrl\":1E999999999}]}", Map.class);
        assertThat(huge.getStatusCode().value()).isIn(400, 415);
    }

    @Test void overviewShowsRealSourcesAndExplicitGaps() {
        Map<?, ?> body = rest.getForObject("/api/overview", Map.class);
        assertThat(body.get("mode")).isEqualTo("PARCIAL");
        assertThat((List<?>) body.get("ticks")).hasSize(33);

        Map<String, Object> spy = tick("SPY");
        assertThat(spy.get("status")).isEqualTo("LIVE");
        assertThat(spy.get("source")).isEqualTo("Yahoo");
        assertThat(spy.get("freshness")).isIn("DELAYED", "END_OF_DAY");
        assertThat(((Number) spy.get("quoteTime")).longValue()).isPositive();
        assertThat(spy.get("candle")).isNotNull();
        assertThat(spy.get("assessment")).isNotNull();

        Map<String, Object> petr = tick("PETR4");
        assertThat(petr.get("status")).isEqualTo("LIVE");
        assertThat(petr.get("source")).isEqualTo("brapi");

        Map<String, Object> usdbrl = tick("USDBRL");
        assertThat(usdbrl.get("source")).as("sem fixture da Yahoo, o câmbio cai no Frankfurter").isEqualTo("Frankfurter");
        assertThat(usdbrl.get("freshness")).isEqualTo("END_OF_DAY");
        assertThat(spy).containsKey("stale");

        Map<String, Object> msft = tick("MSFT");
        assertThat(msft.get("status")).isEqualTo("UNAVAILABLE");
        assertThat(msft.get("candle")).isNull();
        assertThat(msft.get("assessment")).isNull();
        assertThat(msft.get("source")).isNull();
    }

    @Test void assessmentExposesScoreSignalAndFactors() {
        ResponseEntity<Map> r = rest.getForEntity("/api/assessment/SPY", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKeys("score", "signal", "phase", "regime", "factors", "relStrength");
    }

    @Test void instrumentWithoutRealDataIs503AndSaysSo() {
        ResponseEntity<Map> r = rest.getForEntity("/api/assessment/MSFT", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat((String) r.getBody().get("error")).contains("Sem dado em tempo real");
        assertThat(r.getBody().get("status")).isEqualTo("UNAVAILABLE");
        assertThat(rest.getForObject("/api/candles/MSFT", List.class)).isEmpty();
    }

    @Test void unknownSymbolIs404() {
        assertThat(rest.getForEntity("/api/assessment/NOPE", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void invalidLimitIs400() {
        assertThat(rest.getForEntity("/api/candles/SPY?limit=0", Map.class).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void backtestRunsOnRealHistory() {
        ResponseEntity<Map> r = rest.getForEntity("/api/backtest/AAPL?buyScore=35&sellScore=30&bars=400", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKeys("trades", "strategyReturn", "buyHoldReturn");
    }

    @Test void statusShowsProvidersAndPerInstrumentProvenance() {
        Map<String, Object> body = rest.getForObject("/api/status", Map.class);
        assertThat(body.get("cache")).isEqualTo("memory");
        assertThat(body).doesNotContainKeys("flow", "dataMode");
        assertThat((List<?>) body.get("providers")).isNotEmpty();
        assertThat((List<?>) body.get("instruments")).hasSize(33);
    }

    @Test void simulatedFlowEndpointNoLongerExists() {
        ResponseEntity<String> r = rest.postForEntity("/api/settings/flow?enabled=true", null, String.class);
        assertThat(r.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void marketsReportEveryExchangeWithReasonAndCalendarStatus() {
        List<Map<String, Object>> markets = rest.getForObject("/api/markets", List.class);
        assertThat(markets).extracting(m -> m.get("exchange"))
                .contains("NYSE", "B3", "XETRA", "LSE", "TSE", "HKEX", "ASX", "TADAWUL", "JSE", "GLOBAL_24H", "CRYPTO");
        assertThat(markets).allSatisfy(m -> {
            assertThat(m).containsKeys("open", "reason", "localTime", "holidaysVerified");
            assertThat((String) m.get("reason")).isNotBlank();
        });
    }

    @Test void cryptoUsesYahooHistoryBeta365AndShowsGapsForTheRest() {
        Map<String, Object> btc = tick("BTC");
        assertThat(btc.get("status")).isEqualTo("LIVE");
        assertThat(btc.get("source")).isEqualTo("Yahoo");
        assertThat(btc.get("marketOpen")).isEqualTo(true);
        assertThat(tick("SOL").get("status")).as("sem fixture da Yahoo para SOL").isEqualTo("UNAVAILABLE");

        Map<String, Object> eth = rest.getForObject("/api/assessment/ETH", Map.class);
        assertThat(eth.get("periodsPerYear")).isEqualTo(365);
        assertThat(eth.get("beta")).as("beta contra o BTC").isNotNull();
        Map<String, Object> btcAssessment = rest.getForObject("/api/assessment/BTC", Map.class);
        assertThat(btcAssessment.get("beta")).as("o BTC é o próprio benchmark").isNull();
        assertThat(rest.getForObject("/api/assessment/SPY", Map.class).get("periodsPerYear")).isEqualTo(252);
    }

    @Test void fiiTradesOnB3AndIsPricedByYahooWithItsClassFromTheTaxonomy() {
        Map<String, Object> hglg = tick("HGLG11");
        assertThat(hglg.get("status")).isEqualTo("LIVE");
        assertThat(hglg.get("source")).isEqualTo("Yahoo");
        List<Map<String, Object>> universe = rest.getForObject("/api/universe", List.class);
        Map<String, Object> inst = universe.stream().filter(u -> "HGLG11".equals(u.get("symbol"))).findFirst().orElseThrow();
        assertThat(inst.get("assetClass")).isEqualTo("FII");
        assertThat(inst.get("currency")).isEqualTo("BRL");
        assertThat(tick("KNRI11").get("status")).as("sem fixture, aparece como sem dado").isEqualTo("UNAVAILABLE");
    }

    @Test void taxonomyEndpointListsEveryTypeWithPortugueseLabels() {
        List<Map<String, Object>> types = rest.getForObject("/api/taxonomy", List.class);
        assertThat(types).hasSize(dev.meridianwealth.domain.AssetType.values().length);
        Map<String, Object> lci = types.stream().filter(t -> "LCI".equals(t.get("key"))).findFirst().orElseThrow();
        assertThat(lci.get("protection")).isEqualTo("FGC_ATE_LIMITE");
        assertThat(lci.get("protectionLabel")).isEqualTo("Garantido pelo FGC até o limite");
        assertThat(lci.get("hasMaturity")).isEqualTo(true);
        Map<String, Object> cri = types.stream().filter(t -> "CRI".equals(t.get("key"))).findFirst().orElseThrow();
        assertThat(cri.get("protection")).isEqualTo("SEM_GARANTIA_FGC");
        Map<String, Object> ntnb = types.stream().filter(t -> "TESOURO_IPCA_JUROS".equals(t.get("key"))).findFirst().orElseThrow();
        assertThat(ntnb.get("code")).isEqualTo("NTN-B");
        assertThat(ntnb.get("income")).isEqualTo("CUPOM_SEMESTRAL");
    }

    @Test void brazilRatesAreServedAsExactTextWithDateSourceAndStatus() {
        brazilRates.refresh();
        Map<String, Object> body = rest.getForObject("/api/brazil/rates", Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(dev.meridianwealth.domain.RateSeries.values().length);
        assertThat(body.get("notice").toString()).contains("não é recomendação financeira");

        Map<String, Object> selic = items.stream().filter(i -> "selicTarget".equals(i.get("key"))).findFirst().orElseThrow();
        assertThat(selic.get("value")).as("texto exato, sem passar por double").isEqualTo("13.75");
        assertThat(selic.get("unit")).isEqualTo("% a.a.");
        assertThat(selic.get("dateLabel")).as("série preenchida para a frente").isEqualTo("vigente em");
        assertThat(selic.get("confirmedAt")).isNotNull();
        assertThat(items.stream().filter(i -> "cdiDaily".equals(i.get("key"))).findFirst().orElseThrow().get("dateLabel"))
                .isEqualTo("referência");
        assertThat(selic.get("source")).isEqualTo("BCB/SGS 432");
        java.time.LocalDate selicDate = java.time.LocalDate.parse((String) selic.get("date"));
        assertThat(selicDate).as("nunca uma data posterior a hoje (a fixture traz até 21/11/2026)")
                .isAfterOrEqualTo("2026-11-01").isBeforeOrEqualTo(java.time.LocalDate.parse((String) body.get("today")));
        assertThat(items.stream().filter(i -> "ptaxSell".equals(i.get("key"))).findFirst().orElseThrow().get("value"))
                .isEqualTo("5.88890");
        assertThat(items.stream().filter(i -> "cdiDaily".equals(i.get("key"))).findFirst().orElseThrow().get("value"))
                .isEqualTo("0.050788");
        assertThat(items).extracting(i -> i.get("status")).as("depois do refresh nenhum indicador fica sem dado")
                .isSubsetOf("OK", "STALE").doesNotContain("UNAVAILABLE");
    }

    @Test void openApiAndHealthAreUp() {
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
