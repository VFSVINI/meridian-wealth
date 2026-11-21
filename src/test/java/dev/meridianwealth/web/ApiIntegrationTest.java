package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "meridianwealth.data.mode=simulated")
class ApiIntegrationTest {
    @Autowired
    private TestRestTemplate rest;

    @Test void assessmentExposesScoreSignalAndFactors() {
        ResponseEntity<Map> r = rest.getForEntity("/api/assessment/SPY", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKeys("score", "signal", "phase", "regime", "factors", "relStrength");
    }

    @Test void overviewListsEveryInstrumentAsSimulated() {
        Map<?, ?> body = rest.getForObject("/api/overview", Map.class);
        assertThat(body.get("mode")).isEqualTo("SIMULADO");
        assertThat((java.util.List<?>) body.get("ticks")).hasSize(10);
    }

    @Test void unknownSymbolIs404() {
        assertThat(rest.getForEntity("/api/assessment/NOPE", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void invalidLimitIs400() {
        assertThat(rest.getForEntity("/api/candles/SPY?limit=0", Map.class).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void backtestRuns() {
        ResponseEntity<Map> r = rest.getForEntity("/api/backtest/SPY?buyScore=35&sellScore=30&bars=400", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKeys("trades", "strategyReturn", "buyHoldReturn");
    }

    @Test void statusShowsProvidersAndCache() {
        Map<?, ?> body = rest.getForObject("/api/status", Map.class);
        assertThat(body.get("cache")).isEqualTo("memory");
        assertThat((java.util.List<?>) body.get("providers")).isNotEmpty();
    }

    @Test void flowToggleAndOpenApiAndHealth() {
        assertThat(rest.postForEntity("/api/settings/flow?enabled=false", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
