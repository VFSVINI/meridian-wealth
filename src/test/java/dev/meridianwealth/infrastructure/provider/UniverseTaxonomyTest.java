package dev.meridianwealth.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.Instrument;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** O cadastro de ativos precisa conversar com a taxonomia: nenhuma classe pode ficar sem tipo. */
class UniverseTaxonomyTest {
    @Test void everyInstrumentHasATypeAndAUniqueSymbol() {
        Set<String> symbols = new HashSet<>();
        for (Instrument i : Universe.instruments()) {
            assertThat(i.type()).as(i.symbol()).isNotNull();
            assertThat(symbols.add(i.symbol())).as("símbolo repetido: " + i.symbol()).isTrue();
        }
    }

    @Test void universeCoversTheMarketTradedTypes() {
        Set<AssetType> types = new HashSet<>();
        Universe.instruments().forEach(i -> types.add(i.type()));
        assertThat(types).contains(AssetType.ACAO, AssetType.ETF, AssetType.FII, AssetType.CRIPTO_ATIVO, AssetType.CAMBIO,
                AssetType.COMMODITY, AssetType.INDICE);
        assertThat(Universe.instruments().stream().filter(i -> i.type() == AssetType.FII)).hasSize(4);
    }

    @Test void everyTypeInTheUniverseIsPricedByTheMarket() {
        Universe.instruments().forEach(i -> assertThat(i.type().priceSource().name()).as(i.symbol()).isEqualTo("MERCADO"));
    }
}
