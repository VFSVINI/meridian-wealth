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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Autowired
    private TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private Map<String, Object> tick(String symbol) {
        Map<String, Object> body = rest.getForObject("/api/overview", Map.class);
        return ((List<Map<String, Object>>) body.get("ticks")).stream()
                .filter(t -> symbol.equals(t.get("symbol"))).findFirst().orElseThrow();
    }

    @Test void overviewShowsRealSourcesAndExplicitGaps() {
        Map<?, ?> body = rest.getForObject("/api/overview", Map.class);
        assertThat(body.get("mode")).isEqualTo("PARCIAL");
        assertThat((List<?>) body.get("ticks")).hasSize(10);

        Map<String, Object> spy = tick("SPY");
        assertThat(spy.get("status")).isEqualTo("LIVE");
        assertThat(spy.get("source")).isEqualTo("Yahoo");
        assertThat(spy.get("freshness")).isIn("DELAYED", "END_OF_DAY");
        assertThat(((Number) spy.get("quoteTime")).longValue()).isPositive();
        assertThat(spy.get("candle")).isNotNull();
        assertThat(spy.get("assessment")).isNotNull();

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
        assertThat((List<?>) body.get("instruments")).hasSize(10);
    }

    @Test void simulatedFlowEndpointNoLongerExists() {
        ResponseEntity<String> r = rest.postForEntity("/api/settings/flow?enabled=true", null, String.class);
        assertThat(r.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void openApiAndHealthAreUp() {
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
