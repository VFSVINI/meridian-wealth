package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Invariantes da taxonomia e os fatos verificados em fonte pública (FGC, dados abertos da STN). */
class TaxonomyTest {
    @Test void everyTypeIsDescribedAndCodesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (AssetType t : AssetType.values()) {
            assertThat(t.label()).isNotBlank();
            assertThat(t.code()).isNotBlank();
            assertThat(codes.add(t.code())).as("código repetido: " + t.code()).isTrue();
            assertThat(t.category()).isNotNull();
            assertThat(t.indexer().label()).isNotBlank();
            assertThat(t.income().label()).isNotBlank();
            assertThat(t.protection().label()).isNotBlank();
            assertThat(t.priceSource().label()).isNotBlank();
        }
    }

    @Test void coversEveryAssetClassTheTaskAsksFor() {
        assertThat(AssetType.byCategory(AssetCategory.RENDA_FIXA_PUBLICA)).extracting(AssetType::code)
                .containsExactly("LFT", "LTN", "NTN-F", "NTN-B Principal", "NTN-B");
        assertThat(AssetType.byCategory(AssetCategory.RENDA_FIXA_PRIVADA)).extracting(AssetType::code)
                .containsExactly("LCI", "LCA", "CRI", "CRA", "NC", "NP");
        assertThat(AssetType.byCategory(AssetCategory.FUNDOS)).hasSize(3);
        assertThat(AssetType.FII.category()).isEqualTo(AssetCategory.RENDA_VARIAVEL);
    }

    @Test void creditProtectionFollowsTheFgcPublicList() {
        // Garantidos: LCI e LCA. Não garantidos: títulos públicos, fundos, CRI e CRA. Notas não constam na lista.
        assertThat(List.of(AssetType.LCI, AssetType.LCA)).allMatch(t -> t.protection() == CreditProtection.FGC_ATE_LIMITE);
        assertThat(List.of(AssetType.CRI, AssetType.CRA, AssetType.NOTA_COMERCIAL, AssetType.NOTA_PROMISSORIA))
                .allMatch(t -> t.protection() == CreditProtection.SEM_GARANTIA_FGC);
        assertThat(AssetType.byCategory(AssetCategory.RENDA_FIXA_PUBLICA))
                .allMatch(t -> t.protection() == CreditProtection.RISCO_SOBERANO);
        assertThat(AssetType.byCategory(AssetCategory.FUNDOS)).allMatch(t -> t.protection() == CreditProtection.SEM_GARANTIA_FGC);
        assertThat(CreditProtection.FGC_LIMIT_PER_CPF_BRL).isEqualByComparingTo("250000");
        assertThat(CreditProtection.FGC_LIMIT_FOUR_YEARS_BRL).isEqualByComparingTo("1000000");
    }

    @Test void treasuryProductsHaveTheIndexerAndCouponTheirNamesState() {
        assertThat(AssetType.TESOURO_SELIC.indexer()).isEqualTo(Indexer.SELIC);
        assertThat(AssetType.TESOURO_PREFIXADO.indexer()).isEqualTo(Indexer.PRE_FIXADO);
        assertThat(AssetType.TESOURO_IPCA.indexer()).isEqualTo(Indexer.IPCA_MAIS);
        List<AssetType> withCoupon = Arrays.stream(AssetType.values()).filter(t -> t.income() == IncomeKind.CUPOM_SEMESTRAL).toList();
        assertThat(withCoupon).containsExactlyInAnyOrder(AssetType.TESOURO_PREFIXADO_JUROS, AssetType.TESOURO_IPCA_JUROS);
        assertThat(AssetType.TESOURO_PREFIXADO_JUROS.label()).contains("Juros Semestrais");
        assertThat(AssetType.TESOURO_IPCA_JUROS.label()).contains("Juros Semestrais");
    }

    @Test void fixedIncomeHasMaturityAndMarketAssetsDoNot() {
        for (AssetType t : AssetType.values()) {
            boolean fixedIncome = t.category() == AssetCategory.RENDA_FIXA_PUBLICA || t.category() == AssetCategory.RENDA_FIXA_PRIVADA;
            assertThat(t.hasMaturity()).as(t.code()).isEqualTo(fixedIncome);
        }
    }

    @Test void onlyMarketTradedTypesAreExpectedToHaveMarketPrices() {
        assertThat(Arrays.stream(AssetType.values()).filter(t -> t.priceSource() == PriceSource.MERCADO).toList())
                .containsExactlyInAnyOrder(AssetType.ACAO, AssetType.ETF, AssetType.FII, AssetType.CRIPTO_ATIVO,
                        AssetType.CAMBIO, AssetType.COMMODITY, AssetType.INDICE);
        assertThat(AssetType.LCI.priceSource()).as("sem cotação pública: o investidor informa").isEqualTo(PriceSource.INFORMADO_PELO_USUARIO);
        assertThat(AssetType.INDICE.investable()).isFalse();
        assertThat(AssetType.ACAO.investable()).isTrue();
    }

    @Test void instrumentClassesMapToTypesAndUnknownClassesFailLoudly() {
        assertThat(AssetType.fromInstrumentClass("Ação")).contains(AssetType.ACAO);
        assertThat(AssetType.fromInstrumentClass("FII")).contains(AssetType.FII);
        assertThat(AssetType.fromInstrumentClass("Cripto")).contains(AssetType.CRIPTO_ATIVO);
        assertThat(AssetType.fromInstrumentClass("Câmbio")).contains(AssetType.CAMBIO);
        assertThat(AssetType.fromInstrumentClass("Commodity")).contains(AssetType.COMMODITY);
        assertThat(AssetType.fromInstrumentClass("Índice")).contains(AssetType.INDICE);
        assertThat(AssetType.fromInstrumentClass("ETF")).contains(AssetType.ETF);
        assertThat(AssetType.fromInstrumentClass("Xpto")).isEmpty();
        assertThat(AssetType.fromInstrumentClass(null)).isEmpty();

        Instrument bad = new Instrument("X", "X", "EUA", "Xpto", "amplo", Exchange.NYSE, "USD", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(bad::type).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Xpto");
        assertThat(Instrument.of("HGLG11", "FII", Exchange.B3, "FII", "imobiliario", "BRL", null).type()).isEqualTo(AssetType.FII);
    }
}
