package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Shopping no visual Cosmos (item #52, parte 2): cartões de classes comparáveis, com texto educativo próprio e sem taxa de produto inventada. */
class ShoppingPageContractTest {

    private static final Set<String> RISKS = Set.of("Baixo", "Médio", "Alto", "Muito alto");

    @Test
    void thePageAsksTheQuestionHasRiskFiltersCardsAndAComparisonAndKeepsTheFullCatalogCollapsed() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Que tipo de investimento combina comigo?").contains("data-shr=\"Baixo\"").contains("id=\"shCards\"").contains("id=\"shCompare\"");
        assertThat(html).as("o catálogo completo e as referências do Banco Central continuam, recolhidos").containsPattern("(?s)<details class=\"ck-full\">.*id=\"shRefs\".*id=\"shTable\".*</details>");
    }

    @Test
    void theClassFileIsConsistentAndEveryMappedTypeExistsInTheTaxonomy() throws IOException {
        JsonNode root = new ObjectMapper().readTree(StaticPage.file("data/shopping.json"));
        Set<String> keys = new HashSet<>();
        assertThat(root.get("classes")).hasSize(8);
        for (JsonNode c : root.get("classes")) {
            assertThat(keys.add(c.get("key").asText())).as("chave única").isTrue();
            assertThat(RISKS).contains(c.get("risk").asText());
            for (String f : new String[] {"name", "what", "forWho", "liquidity", "protection"}) {
                assertThat(c.get(f).asText()).as(f).isNotBlank();
            }
            if (!c.get("type").isNull()) {
                assertThat(AssetType.valueOf(c.get("type").asText())).isNotNull();
            }
        }
    }

    @Test
    void protectionFollowsTheVerifiedFgcRulesAndNoProductRateOrSettlementDayIsInvented() throws IOException {
        String json = StaticPage.file("data/shopping.json");
        assertThat(json).contains("FGC até R$ 250 mil por CPF e instituição").contains("Sem garantia do FGC; regras da CVM").contains("confira no papel");
        assertThat(json).doesNotContainPattern("(?i)\\bD\\+\\d").doesNotContainPattern("(?i)(cdb|lci|lca)\\s+\\d").doesNotContainPattern("\\d\\s*%\\s*(a\\.a|ao ano)").doesNotContain("garantido");
        String js = StaticPage.file("shopping.js");
        assertThat(js).contains("Selecione até 3").contains("SH.pick.length<3").contains("não informado");
        assertThat(js).as("se o catálogo falha, os cartões dizem isso em vez de ficar carregando para sempre").contains("$('#shCards').innerHTML=m");
    }
}
