package dev.meridianwealth.domain.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.thesis.ThesisReader.Reading;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThesisReaderTest {
    private static Holding h(String label, AssetType type, String sector, String value) {
        return new Holding(label, type, sector, new BigDecimal(value));
    }

    private static final List<Holding> PORTFOLIO = List.of(h("AAPL", AssetType.ACAO, "tecnologia", "500"),
            h("Tesouro Selic", AssetType.TESOURO_SELIC, null, "300"), h("BTC", AssetType.CRIPTO_ATIVO, "cripto", "200"));

    private static Reading read(String id) { return ThesisReader.read(ThesisCatalog.find(id).orElseThrow(), PORTFOLIO); }

    @Test void catalogHasTheFiveGenericThesesWithPremisesSensitivitiesAndInvalidations() {
        assertThat(ThesisCatalog.ALL).extracting(Thesis::id).containsExactly("equilibrio-de-riscos", "megatendencias", "sistematica-estatistica",
                "macro-juros-e-cambio", "valor-de-longo-prazo");
        assertThat(ThesisCatalog.ALL).allSatisfy(t -> {
            assertThat(t.premises()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(t.invalidations()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(t.sensitivities()).isNotEmpty();
            assertThat(t.inspiration()).startsWith("Estilo");
        });
        String inspirations = String.join(" ", ThesisCatalog.ALL.stream().map(Thesis::inspiration).toList());
        assertThat(inspirations).contains("Bridgewater", "BlackRock", "Renaissance", "Verde/SPX", "Dynamo");
        assertThat(ThesisCatalog.DISCLAIMER).contains("Não representam posições reais de nenhuma instituição").contains("não são recomendação");
        assertThat(ThesisCatalog.find("nao-existe")).isEmpty();
    }

    @Test void noThesisTextClaimsARealPosition() {
        for (Thesis t : ThesisCatalog.ALL) {
            String all = (t.summary() + " " + String.join(" ", t.premises()) + " " + String.join(" ", t.invalidations())).toLowerCase();
            assertThat(all).doesNotContain("carteira da").doesNotContain("posição da").doesNotContain("comprou").doesNotContain("vendeu")
                    .doesNotContain("detém").doesNotContain("recomend");
        }
    }

    @Test void readsExposureByHandForTheThemesThesis() {
        Reading r = read("megatendencias");
        assertThat(r.sensitivities()).extracting(ThesisReader.SensitivityReading::id).containsExactly("tecnologia", "energia", "mineracao");
        assertThat(r.sensitivities().get(0).percent()).isEqualByComparingTo("50.00");
        assertThat(r.sensitivities().get(0).holdings()).containsExactly("AAPL");
        assertThat(r.sensitivities().get(1).percent()).isEqualByComparingTo("0.00");
        assertThat(r.favoredPercent()).isEqualByComparingTo("50.00");
        assertThat(r.hurtPercent()).isEqualByComparingTo("0.00");
        assertThat(r.outsideThesisPercent()).isEqualByComparingTo("50.00");
    }

    @Test void readsFavoredHurtAndOutsideForTheValueThesis() {
        Reading r = read("valor-de-longo-prazo");
        assertThat(r.favoredPercent()).isEqualByComparingTo("50.00");
        assertThat(r.hurtPercent()).isEqualByComparingTo("20.00");
        assertThat(r.outsideThesisPercent()).isEqualByComparingTo("30.00");
    }

    @Test void liquidityThesisCountsEachHoldingOnceInTheUnion() {
        Reading r = read("sistematica-estatistica");
        assertThat(r.sensitivities().get(0).percent()).isEqualByComparingTo("70.00");
        assertThat(r.favoredPercent()).isEqualByComparingTo("70.00");
        assertThat(r.outsideThesisPercent()).isEqualByComparingTo("30.00");
    }

    @Test void sectorCriteriaNeverMatchAHoldingWithoutSector() {
        Reading r = ThesisReader.read(ThesisCatalog.find("megatendencias").orElseThrow(), List.of(h("Ação sem setor", AssetType.ACAO, null, "100")));
        assertThat(r.favoredPercent()).isEqualByComparingTo("0.00");
        assertThat(r.outsideThesisPercent()).isEqualByComparingTo("100.00");
    }

    @Test void readAllReturnsOneReadingPerThesisAndValidatesInput() {
        assertThat(ThesisReader.readAll(PORTFOLIO)).hasSize(5);
        assertThatThrownBy(() -> ThesisReader.readAll(List.of())).hasMessageContaining("ao menos uma posição");
        assertThatThrownBy(() -> ThesisReader.readAll(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Thesis.Sensitivity("x", "x", Set.of(), Set.of(), Thesis.Effect.FAVORECIDO_SE_CONFIRMAR, "x"))
                .hasMessageContaining("sem critério");
    }
}
