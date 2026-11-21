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
