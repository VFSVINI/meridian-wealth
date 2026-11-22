package dev.meridianwealth.domain.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.meridianwealth.domain.correlation.Correlation.Matrix;
import dev.meridianwealth.domain.correlation.Correlation.PortfolioRisk;
import dev.meridianwealth.domain.montecarlo.AlignedReturns;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CorrelationTest {
    /** Série de fechamentos a partir de retornos diários, nas datas {@code startDay ...}. */
    private static TreeMap<Long, Double> closes(long startDay, double[] returns) {
        TreeMap<Long, Double> m = new TreeMap<>();
        double price = 100;
        m.put(startDay, price);
        for (int i = 0; i < returns.length; i++) {
            price *= 1 + returns[i];
            m.put(startDay + i + 1, price);
        }
        return m;
    }

    private static double[] noise(long seed, int n, double scale) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = (r.nextDouble() - 0.5) * 2 * scale;
        return out;
    }

    @Test void pearsonOfASeriesWithItselfIsOneWithItsOppositeIsMinusOneAndItIsSymmetricAndBounded() {
        double[] a = noise(1, 200, 0.02), b = noise(2, 200, 0.03), neg = new double[a.length];
        for (int i = 0; i < a.length; i++) neg[i] = -a[i];
        assertThat(Correlation.pearson(a, a)).isCloseTo(1.0, within(1e-12));
        assertThat(Correlation.pearson(a, neg)).isCloseTo(-1.0, within(1e-12));
        assertThat(Correlation.pearson(a, b)).isEqualTo(Correlation.pearson(b, a));
        assertThat(Correlation.pearson(a, b)).isBetween(-1.0, 1.0);
        double[] scaled = new double[a.length];
        for (int i = 0; i < a.length; i++) scaled[i] = a[i] * 3 + 0.001;
        assertThat(Correlation.pearson(a, scaled)).as("a correlação ignora escala e deslocamento").isCloseTo(1.0, within(1e-12));
    }

    @Test void aSeriesThatNeverMovesHasNoCorrelationInsteadOfZero() {
        assertThat(Correlation.pearson(new double[100], noise(3, 100, 0.01))).isNull();
        assertThat(Correlation.pearson(new double[] {0.01}, new double[] {0.02})).isNull();
    }

    @Test void pairwiseMatrixHasOnesOnTheDiagonalIsSymmetricAndFindsPerfectPairs() {
        double[] r = noise(4, 300, 0.02), inv = new double[r.length], other = noise(5, 300, 0.02);
        for (int i = 0; i < r.length; i++) inv[i] = -r[i];
        Map<String, TreeMap<Long, Double>> in = new LinkedHashMap<>();
        in.put("A", closes(0, r));
        in.put("B", closes(0, r));
        in.put("C", closes(0, other));
        Matrix m = Correlation.pairwise(in);
        assertThat(m.keys()).containsExactly("A", "B", "C");
        for (int i = 0; i < 3; i++) assertThat(m.values()[i][i]).isEqualTo(1.0);
        assertThat(m.values()[0][1]).isCloseTo(1.0, within(1e-9));
        assertThat(m.values()[0][2]).isEqualTo(m.values()[2][0]).isBetween(-1.0, 1.0);
        assertThat(m.days()[0][1]).as("a janela é de no máximo 252 retornos").isEqualTo(Correlation.WINDOW);
        Map<String, TreeMap<Long, Double>> opp = new LinkedHashMap<>();
        opp.put("A", closes(0, r));
        opp.put("N", closes(0, inv));
        assertThat(Correlation.pairwise(opp).values()[0][1]).isCloseTo(-1.0, within(1e-6));
    }

    @Test void aShortAssetOnlyBlanksItsOwnPairsNeverTheOthers() {
        double[] r = noise(6, 200, 0.02);
        Map<String, TreeMap<Long, Double>> in = new LinkedHashMap<>();
        in.put("A", closes(0, r));
        in.put("B", closes(0, noise(7, 200, 0.02)));
        in.put("CURTO", closes(150, noise(8, 40, 0.02))); // só 40 pregões em comum
        in.put("OUTRO_MERCADO", closes(1000, noise(9, 200, 0.02))); // nenhuma data em comum
        Matrix m = Correlation.pairwise(in);
        assertThat(m.values()[0][1]).as("A e B seguem com dado").isNotNull();
        assertThat(m.values()[0][2]).as("40 pregões em comum: não informado").isNull();
        assertThat(m.days()[0][2]).isEqualTo(40);
        assertThat(m.values()[0][3]).as("sem datas em comum: não informado, nunca zero").isNull();
        assertThat(m.values()[3][3]).as("nem a diagonal se inventa com 200 retornos próprios? tem dado").isEqualTo(1.0);
        assertThat(m.values()[2][2]).as("40 retornos próprios: nem a diagonal vale").isNull();
    }

    @Test void onlyDatesBothSeriesHaveCountAndAGapComposesTheReturn() {
        TreeMap<Long, Double> a = new TreeMap<>(Map.of(1L, 100.0, 2L, 110.0, 4L, 121.0));
        TreeMap<Long, Double> b = new TreeMap<>(Map.of(1L, 50.0, 3L, 55.0, 4L, 60.5));
        double[][] r = Correlation.returnsOnCommonDates(a, b);
        assertThat(r[0]).hasSize(1);
        assertThat(r[0][0]).isCloseTo(121.0 / 100 - 1, within(1e-12));
        assertThat(r[1][0]).isCloseTo(60.5 / 50 - 1, within(1e-12));
    }

    private static AlignedReturns aligned(double[]... series) {
        Map<String, TreeMap<Long, Double>> m = new LinkedHashMap<>();
        for (int i = 0; i < series.length; i++) m.put("S" + i, closes(0, series[i]));
        return AlignedReturns.of(m);
    }

    @Test void portfolioRiskEqualsTheWeightedAverageWhenAssetsMoveTogetherAndShrinksWhenTheyDoNot() {
        double[] r = noise(10, 300, 0.02), inv = new double[r.length];
        for (int i = 0; i < r.length; i++) inv[i] = -r[i];
        PortfolioRisk same = Correlation.portfolioRisk(aligned(r, r), new double[] {0.5, 0.5});
        assertThat(same.portfolioVolatility()).isCloseTo(same.weightedAverageVolatility(), within(1e-9));
        assertThat(same.diversificationBenefit()).isCloseTo(0, within(1e-9));
        PortfolioRisk opposite = Correlation.portfolioRisk(aligned(r, inv), new double[] {0.5, 0.5});
        assertThat(opposite.portfolioVolatility()).as("movimentos opostos quase se anulam (só sobra o efeito de composição)").isLessThan(opposite.weightedAverageVolatility() * 0.05);
        assertThat(opposite.diversificationBenefit()).isGreaterThan(opposite.weightedAverageVolatility() * 0.9);
        assertThat(same.days()).isEqualTo(Correlation.WINDOW);
    }

    @Test void diversificationBenefitIsNeverNegativeForRandomPortfolios() {
        for (int seed = 0; seed < 25; seed++) {
            AlignedReturns a = aligned(noise(seed, 260, 0.02), noise(seed + 100, 260, 0.01), noise(seed + 200, 260, 0.03));
            double[] w = {0.2, 0.5, 0.3};
            PortfolioRisk risk = Correlation.portfolioRisk(a, w);
            assertThat(risk.diversificationBenefit()).isGreaterThanOrEqualTo(0);
            assertThat(risk.portfolioVolatility()).isLessThanOrEqualTo(risk.weightedAverageVolatility() + 1e-9);
        }
    }

    @Test void tooLittleSharedHistoryIsRefusedForPortfolioRisk() {
        assertThatThrownBy(() -> Correlation.portfolioRisk(aligned(new double[] {0.01}), new double[] {1.0})).isInstanceOf(IllegalArgumentException.class);
    }
}
