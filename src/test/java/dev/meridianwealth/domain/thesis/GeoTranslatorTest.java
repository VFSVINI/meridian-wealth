package dev.meridianwealth.domain.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.GeoRiskScorer;
import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.domain.NewsItem;
import dev.meridianwealth.domain.thesis.GeoTranslator.SectorLine;
import dev.meridianwealth.domain.thesis.GeoTranslator.Translation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoTranslatorTest {
    private static Holding h(String label, AssetType type, String sector, String value) {
        return new Holding(label, type, sector, new BigDecimal(value));
    }

    /** Duas manchetes determinísticas: energia/defesa/financeiro (risco 1,0 após o teto) e financeiro/indústria (0,9). */
    private static MacroSnapshot macro() {
        List<NewsItem> news = List.of(GeoRiskScorer.score("Oil sanctions escalate", "Fonte A"),
                GeoRiskScorer.score("Central bank warns of recession", "Fonte B"));
        return MacroSnapshot.of(20.0, null, null, null, "manchete", "Fonte de teste", news, GeoRiskScorer.sectorImpact(news));
    }

    private static SectorLine line(Translation t, String sector) {
        return t.sectors().stream().filter(s -> s.sector().equals(sector)).findFirst().orElseThrow();
    }

    @Test void translatesHeadlinePressureIntoExposureByHand() {
        Translation t = GeoTranslator.translate(List.of(h("PETR4", AssetType.ACAO, "energia", "600"), h("ITUB4", AssetType.ACAO, "financeiro", "300"),
                h("Tesouro Selic", AssetType.TESOURO_SELIC, null, "100")), macro());
        assertThat(t.macroAvailable()).isTrue();
        assertThat(t.totalBrl()).isEqualByComparingTo("1000");
        assertThat(t.unclassifiedPercent()).isEqualByComparingTo("10.00");
        assertThat(t.classifiedPercent()).isEqualByComparingTo("90.00");
        // pressão = soma do risco das manchetes do setor / 3: energia 1,0/3 = 0,33; financeiro (1,0 + 0,9)/3 = 0,63
        SectorLine energy = line(t, "energia");
        assertThat(energy.exposurePercent()).isEqualByComparingTo("60.00");
        assertThat(energy.pressure()).isEqualByComparingTo("0.33");
        assertThat(energy.contributionPoints()).isEqualByComparingTo("19.80");
        assertThat(energy.headlines()).extracting(GeoTranslator.Headline::title).containsExactly("Oil sanctions escalate");
        SectorLine fin = line(t, "financeiro");
        assertThat(fin.exposurePercent()).isEqualByComparingTo("30.00");
        assertThat(fin.pressure()).isEqualByComparingTo("0.63");
        assertThat(fin.contributionPoints()).isEqualByComparingTo("18.90");
        assertThat(fin.headlines()).hasSize(2);
        // média sobre a parte classificada: 600/900 x 0,33 + 300/900 x 0,63 = 0,22 + 0,21
        assertThat(t.weightedPressure()).isEqualByComparingTo("0.43");
        assertThat(t.sectors()).extracting(SectorLine::sector).containsExactly("energia", "financeiro");
        assertThat(t.pressuredWithoutExposure()).containsExactly("defesa", "industria");
        assertThat(t.findings()).anyMatch(f -> f.contains("Energia") && f.contains("60,00%"))
                .anyMatch(f -> f.contains("sem exposição") && f.contains("Defesa"))
                .anyMatch(f -> f.contains("10,00%") && f.contains("não informado"));
    }

    @Test void withoutMacroNothingIsEstimated() {
        Translation t = GeoTranslator.translate(List.of(h("PETR4", AssetType.ACAO, "energia", "100")), MacroSnapshot.unavailable());
        assertThat(t.macroAvailable()).isFalse();
        assertThat(t.weightedPressure()).isNull();
        assertThat(line(t, "energia").pressure()).isEqualByComparingTo("0");
        assertThat(line(t, "energia").headlines()).isEmpty();
        assertThat(t.findings()).anyMatch(f -> f.contains("Sem dado macro"));
        assertThat(GeoTranslator.translate(List.of(h("PETR4", AssetType.ACAO, "energia", "100")), null).macroAvailable()).isFalse();
    }

    @Test void calmHeadlinesProduceNoPressure() {
        List<NewsItem> news = List.of(GeoRiskScorer.score("Ceasefire agreed after talks", "Fonte"));
        MacroSnapshot calm = MacroSnapshot.of(15.0, null, null, null, "calma", "Fonte", news, GeoRiskScorer.sectorImpact(news));
        Translation t = GeoTranslator.translate(List.of(h("PETR4", AssetType.ACAO, "energia", "100")), calm);
        assertThat(t.weightedPressure()).isEqualByComparingTo("0");
        assertThat(t.findings()).anyMatch(f -> f.contains("Nenhuma manchete atual atinge"));
    }

    @Test void onlyUnclassifiedPositionsGiveNoWeightedPressure() {
        Translation t = GeoTranslator.translate(List.of(h("Tesouro Selic", AssetType.TESOURO_SELIC, null, "100")), macro());
        assertThat(t.weightedPressure()).isNull();
        assertThat(t.unclassifiedPercent()).isEqualByComparingTo("100.00");
        assertThat(t.sectors()).isEmpty();
    }

    @Test void limitsHeadlinesPerSectorKeepingTheRiskiest() {
        List<NewsItem> news = new ArrayList<>();
        for (int i = 0; i < 8; i++) news.add(GeoRiskScorer.score("Oil shock number " + i, "F" + i));
        MacroSnapshot m = MacroSnapshot.of(20.0, null, null, null, "x", "Fonte", news, GeoRiskScorer.sectorImpact(news));
        Translation t = GeoTranslator.translate(List.of(h("PETR4", AssetType.ACAO, "energia", "100")), m);
        assertThat(line(t, "energia").headlines()).hasSize(GeoTranslator.MAX_HEADLINES_PER_SECTOR);
    }

    @Test void validatesInput() {
        assertThatThrownBy(() -> GeoTranslator.translate(List.of(), macro())).hasMessageContaining("ao menos uma posição");
        assertThatThrownBy(() -> GeoTranslator.translate(null, macro())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Holding("Selic", AssetType.INDICE, null, BigDecimal.ONE)).hasMessageContaining("só referência");
        assertThatThrownBy(() -> new Holding("x", AssetType.ACAO, null, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Holding("x", AssetType.ACAO, null, new BigDecimal("1E999999999"))).hasMessageContaining("fora do intervalo");
        List<Holding> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) many.add(h("A" + i, AssetType.ACAO, "energia", "1"));
        assertThatThrownBy(() -> GeoTranslator.translate(many, macro())).hasMessageContaining("No máximo 50");
        assertThat(h("x", AssetType.ACAO, "  ", "1").sector()).isNull();
    }
}
