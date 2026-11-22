package dev.meridianwealth.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.risk.CorrelationService.Output;
import dev.meridianwealth.application.risk.MonteCarloService.Holding;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.auth.AuthProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationServiceTest {
    private static final AuthPrincipal WHO = new AuthPrincipal(UUID.fromString("00000000-0000-0000-0000-000000000002"), AuthProvider.PASSWORD);
    private static final long DAY = 86_400_000L;

    private static double[] noise(long seed, int n, double scale) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = (r.nextDouble() - 0.5) * 2 * scale;
        return out;
    }

    private static List<Candle> series(double[] returns, long startDay) {
        List<Candle> out = new ArrayList<>();
        double price = 100;
        out.add(new Candle(startDay * DAY, price, price, price, price, 1));
        for (int i = 0; i < returns.length; i++) {
            price *= 1 + returns[i];
            out.add(new Candle((startDay + i + 1) * DAY, price, price, price, price, 1));
        }
        return out;
    }

    private static Holding h(String key, String symbol, String value, String cat, String catLabel, String risk) {
        return new Holding(key, key, symbol, "BRL", new BigDecimal(value), cat, catLabel, risk);
    }

    private static CorrelationService service(List<Holding> holdings, Map<String, List<Candle>> data) {
        return new CorrelationService(who -> holdings, (s, l) -> {
            List<Candle> c = data.get(s);
            if (c == null) throw new IllegalStateException("sem dado real");
            return c;
        });
    }

    @Test void correlatesAssetsAndClassesLeavesOutWhatHasNoHistoryAndMapsConcentrationByRisk() {
        double[] a = noise(1, 300, 0.02), b = noise(2, 300, 0.02), c = noise(3, 300, 0.02);
        List<Holding> holdings = List.of(h("PETR4", "PETR4", "5000", "RENDA_VARIAVEL", "Renda variável", "HIGH"), h("VALE3", "VALE3", "3000", "RENDA_VARIAVEL", "Renda variável", "MEDIUM"),
                h("BTC", "BTC", "1500", "CRIPTO", "Criptoativos", "EXTREME"), h("T:LCI:x", null, "500", "RENDA_FIXA_PRIVADA", "Renda fixa privada", null));
        Output o = service(holdings, Map.of("PETR4", series(a, 0), "VALE3", series(b, 0), "BTC", series(c, 0))).analyze(WHO);

        assertThat(o.assets().keys()).containsExactly("PETR4", "VALE3", "BTC");
        for (int i = 0; i < 3; i++) assertThat(o.assets().values().get(i).get(i)).isEqualTo(1.0);
        assertThat(o.assets().values().get(0).get(1)).isEqualTo(o.assets().values().get(1).get(0)).isBetween(-1.0, 1.0);
        assertThat(o.classes().labels()).containsExactly("Renda variável", "Criptoativos");
        assertThat(o.classes().values().get(0).get(1)).as("correlação entre as duas classes com histórico").isNotNull().isBetween(-1.0, 1.0);
        assertThat(o.excluded()).extracting(CorrelationService.Excluded::assetKey).containsExactly("T:LCI:x");
        assertThat(o.excluded().get(0).reason()).contains("não é negociado em mercado");
        assertThat(o.risk().diversificationBenefitPercent()).isGreaterThanOrEqualTo(0);
        assertThat(o.risk().portfolioVolatilityPercent()).isLessThanOrEqualTo(o.risk().weightedAverageVolatilityPercent() + 1e-9);
        assertThat(o.risk().coveredPercent()).isCloseTo(9500.0 / 10000 * 100, within(1e-9));
        assertThat(o.notes()).anyMatch(n -> n.contains("pode mudar em crises"));

        var conc = o.concentration();
        assertThat(conc.riskLevels()).containsExactly("LOW", "MEDIUM", "HIGH", "EXTREME", "UNRATED");
        int rv = conc.categories().indexOf("RENDA_VARIAVEL"), cr = conc.categories().indexOf("CRIPTO"), rf = conc.categories().indexOf("RENDA_FIXA_PRIVADA");
        assertThat(conc.cells().get(rv).get(2)).as("PETR4, risco alto: 50% do patrimônio").isCloseTo(50.0, within(1e-9));
        assertThat(conc.cells().get(rv).get(1)).as("VALE3, risco moderado: 30%").isCloseTo(30.0, within(1e-9));
        assertThat(conc.cells().get(cr).get(3)).isCloseTo(15.0, within(1e-9));
        assertThat(conc.cells().get(rf).get(4)).as("sem classificação de risco: coluna própria").isCloseTo(5.0, within(1e-9));
        assertThat(conc.cells().stream().flatMap(List::stream).mapToDouble(Double::doubleValue).sum()).isCloseTo(100.0, within(1e-9));
        assertThat(conc.assetHhi()).isCloseTo(0.5 * 0.5 + 0.3 * 0.3 + 0.15 * 0.15 + 0.05 * 0.05, within(1e-12));
        assertThat(conc.effectivePositions()).isCloseTo(1 / conc.assetHhi(), within(1e-12));
        assertThat(conc.largestPositionPercent()).isCloseTo(50.0, within(1e-9));
        assertThat(conc.topPositions()).extracting(CorrelationService.Position::assetKey).containsExactly("PETR4", "VALE3", "BTC", "T:LCI:x");
    }

    @Test void anAssetWithTooLittleSharedHistoryBlanksOnlyItsOwnCellsNeverTheOthers() {
        List<Holding> holdings = List.of(h("A", "A", "1000", "RENDA_VARIAVEL", "Renda variável", "LOW"), h("B", "B", "1000", "RENDA_VARIAVEL", "Renda variável", "LOW"),
                h("C", "C", "1000", "CRIPTO", "Criptoativos", "HIGH"));
        Output o = service(holdings, Map.of("A", series(noise(4, 300, 0.02), 0), "B", series(noise(5, 300, 0.02), 0), "C", series(noise(6, 300, 0.02), 1000))).analyze(WHO);
        assertThat(o.assets().values().get(0).get(1)).as("A e B têm dado").isNotNull();
        assertThat(o.assets().values().get(0).get(2)).as("C não tem nenhuma data em comum: não informado, nunca zero").isNull();
        assertThat(o.assets().days().get(0).get(2)).isZero();
        assertThat(o.assets().values().get(2).get(2)).as("a diagonal de C tem 300 retornos próprios").isEqualTo(1.0);
    }

    @Test void withoutAnyHistoryOnlyTheConcentrationIsReturnedAndAnEmptyPortfolioIsRefused() {
        Output o = service(List.of(h("T:LCI:x", null, "1000", "RENDA_FIXA_PRIVADA", "Renda fixa privada", null)), Map.of()).analyze(WHO);
        assertThat(o.assets()).isNull();
        assertThat(o.classes()).isNull();
        assertThat(o.risk()).isNull();
        assertThat(o.concentration().assetHhi()).isEqualTo(1.0);
        assertThat(o.excluded()).hasSize(1);
        assertThatThrownBy(() -> service(List.of(), Map.of()).analyze(WHO)).isInstanceOf(MonteCarloException.class).hasMessageContaining("não tem posição com valor em reais");
    }

    @Test void aSingleClassWithHistoryGivesNoClassMatrixAndSaysSo() {
        Output o = service(List.of(h("A", "A", "1000", "RENDA_VARIAVEL", "Renda variável", "LOW"), h("B", "B", "1000", "RENDA_VARIAVEL", "Renda variável", "LOW")),
                Map.of("A", series(noise(7, 300, 0.02), 0), "B", series(noise(8, 300, 0.02), 0))).analyze(WHO);
        assertThat(o.assets()).isNotNull();
        assertThat(o.classes()).isNull();
        assertThat(o.notes()).anyMatch(n -> n.contains("Só uma classe tem histórico"));
    }
}
