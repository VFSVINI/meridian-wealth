package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.Http;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Catálogo de ativos negociados (item #33) pela API inteira: busca, abertura sob demanda, ativo sem dado real e o limite da rota de abertura. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"meridianwealth.crypto.stream.enabled=false", "meridianwealth.rate-limit.heavy-per-minute=100"})
class CatalogApiIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway fixtureGateway() { return new FixtureHttpGateway(); }
    }

    @Value("${local.server.port}")
    private int port;

    @SuppressWarnings("unchecked")
    @Test void searchFindsAnyListedAssetBySymbolOrNameWithoutAccentsAndNeverReturnsAPrice() {
        Http http = new Http(port);
        Http.Reply r = http.get("/api/catalog/search?q=xpsf");
        assertThat(r.status()).isEqualTo(200);
        String body = r.body();
        assertThat(body).contains("\"symbol\":\"XPSF11\"").contains("\"assetClass\":\"FII\"").contains("\"tracked\":false");
        assertThat(body).as("só cadastro").doesNotContain("\"price\"").doesNotContain("\"close\"");
        assertThat(http.get("/api/catalog/search?q=petroleo%20brasileiro").body()).contains("PETR4");
        assertThat(http.get("/api/catalog/search?q=apple").body()).contains("\"symbol\":\"AAPL\"");
        assertThat(http.get("/api/catalog/search?q=AAPL").body()).as("o curado já acompanhado vem marcado").contains("\"tracked\":");
        assertThat(http.get("/api/catalog/search?q=").body()).isEqualTo("[]");
        assertThat(http.get("/api/catalog/search?q=a&limit=41").status()).as("limite máximo").isEqualTo(400);
    }

    @Test void openingACuratedAssetIsInstantAndAnUnknownSymbolIs404() {
        Http http = new Http(port);
        assertThat(http.post("/api/catalog/SPY/track", Map.of()).status()).isEqualTo(200);
        assertThat(http.post("/api/catalog/NAOEXISTE/track", Map.of()).status()).isEqualTo(404);
        assertThat(http.post("/api/catalog/NAO%20EXISTE!/track", Map.of()).status()).as("símbolo com caracteres inválidos").isIn(400, 404);
    }

    @Test void aListedAssetWithoutAnyRealDataIsRefusedAndNothingIsInvented() {
        Http http = new Http(port);
        // o gateway de teste só tem dado real gravado de SPY e AAPL: o ativo existe no catálogo, mas sem provedor respondendo ele NÃO entra
        Http.Reply r = http.post("/api/catalog/XPSF11/track", Map.of());
        assertThat(r.status()).isEqualTo(503);
        assertThat(http.get("/api/universe").body()).doesNotContain("XPSF11");
        assertThat(http.get("/api/catalog/search?q=xpsf").body()).contains("\"tracked\":false");
    }
}
