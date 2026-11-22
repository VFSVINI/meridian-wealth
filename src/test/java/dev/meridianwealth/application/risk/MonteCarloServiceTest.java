package dev.meridianwealth.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.risk.MonteCarloService.Coverage;
import dev.meridianwealth.application.risk.MonteCarloService.Holding;
import dev.meridianwealth.application.risk.MonteCarloService.Output;
import dev.meridianwealth.application.risk.MonteCarloService.Request;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.auth.AuthProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonteCarloServiceTest {
    private static final AuthPrincipal WHO = new AuthPrincipal(UUID.fromString("00000000-0000-0000-0000-000000000001"), AuthProvider.PASSWORD);
    private static final long DAY = 86_400_000L;

    private static List<Candle> series(int bars, double start, double dailyReturn, double wiggle) {
        List<Candle> out = new ArrayList<>();
        double price = start;
        for (int i = 0; i < bars; i++) {
            price *= 1 + dailyReturn + (i % 2 == 0 ? wiggle : -wiggle);
            out.add(new Candle(1_700_000_000_000L + i * DAY, price, price, price, price, 1_000));
        }
        return out;
    }

    private static Holding h(String key, String symbol, String currency, String value) { return new Holding(key, key, symbol, currency, new BigDecimal(value)); }

    private static MonteCarloService service(List<Holding> holdings, Map<String, List<Candle>> data) {
        return new MonteCarloService(who -> holdings, (symbol, limit) -> {
            List<Candle> c = data.get(symbol);
            if (c == null) throw new IllegalStateException("sem dado real");
            return c;
        });
    }

    private static Request req(String monthly, int years, String goal, Long seed) {
        return new Request(null, new BigDecimal(monthly), years, null, null, goal == null ? null : new BigDecimal(goal), seed);
    }

    @Test void parametricUsesOnlyTheAssumptionsAndLabelsItselfAProjection() {
        Output o = service(List.of(), Map.of()).parametric(new Request(new BigDecimal("100000"), new BigDecimal("1000"), 10, new BigDecimal("10"), new BigDecimal("15"), new BigDecimal("400000"), 5L));
        assertThat(o.parametric()).isTrue();
        assertThat(o.label()).startsWith("PROJEÇÃO").contains("não é cotação");
        assertThat(o.months()).isEqualTo(120);
        assertThat(o.coverage()).isEmpty();
        assertThat(o.assumptions()).anyMatch(a -> a.contains("10,0% ao ano") && a.contains("15,0%"));
        assertThat(o.result().goalProbabilityFinal()).isBetween(0.0, 1.0);
        Output again = service(List.of(), Map.of()).parametric(new Request(new BigDecimal("100000"), new BigDecimal("1000"), 10, new BigDecimal("10"), new BigDecimal("15"), new BigDecimal("400000"), 5L));
        assertThat(again.result().percentiles()).isDeepEqualTo(o.result().percentiles());
    }

    @Test void parametricRejectsAbsurdPremises() {
        MonteCarloService s = service(List.of(), Map.of());
        assertThatThrownBy(() -> s.parametric(new Request(BigDecimal.TEN, null, 0, BigDecimal.TEN, BigDecimal.TEN, null, null))).hasMessageContaining("1 a 40 anos");
        assertThatThrownBy(() -> s.parametric(new Request(BigDecimal.TEN, null, 5, new BigDecimal("500"), BigDecimal.TEN, null, null))).hasMessageContaining("Retorno esperado");
        assertThatThrownBy(() -> s.parametric(new Request(BigDecimal.TEN, null, 5, BigDecimal.TEN, new BigDecimal("-1"), null, null))).hasMessageContaining("Volatilidade");
        assertThatThrownBy(() -> s.parametric(new Request(BigDecimal.TEN, null, 5, null, BigDecimal.TEN, null, null))).hasMessageContaining("Retorno esperado");
        assertThatThrownBy(() -> s.parametric(new Request(BigDecimal.ZERO, null, 5, BigDecimal.TEN, BigDecimal.TEN, null, null))).hasMessageContaining("patrimônio inicial ou um aporte");
    }

    @Test void portfolioResamplesRealReturnsKeepsUncoveredPositionsStillAndSaysWhy() {
        List<Holding> holdings = List.of(h("PETR4", "PETR4", "BRL", "6000"), h("SPY", "SPY", "USD", "2000"), h("T:LCI:x", null, "BRL", "2000"), h("SEM", "SEM", "BRL", "1000"));
        Output o = service(holdings, Map.of("PETR4", series(300, 30, 0.0008, 0.01), "SPY", series(300, 500, 0.0005, 0.006), "SEM", series(40, 10, 0.0, 0.0)))
                .fromPortfolio(WHO, req("500", 5, "50000", 1L));
        assertThat(o.parametric()).isFalse();
        assertThat(o.initial()).isEqualTo(11_000);
        assertThat(o.coveredPercent()).isEqualTo(8_000.0 / 11_000 * 100, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(o.coverage()).extracting(Coverage::assetKey, Coverage::treatment).containsExactly(
                org.assertj.core.groups.Tuple.tuple("PETR4", "HISTORICO"), org.assertj.core.groups.Tuple.tuple("SPY", "HISTORICO"),
                org.assertj.core.groups.Tuple.tuple("T:LCI:x", "CONSTANTE"), org.assertj.core.groups.Tuple.tuple("SEM", "CONSTANTE"));
        assertThat(o.coverage().get(2).reason()).contains("não é negociado em mercado");
        assertThat(o.coverage().get(3).reason()).contains("Histórico curto").contains("40");
        assertThat(o.statistics().commonDays()).isEqualTo(299);
        assertThat(o.statistics().annualVolatility()).isPositive();
        assertThat(o.assumptions()).anyMatch(a -> a.contains("moeda do ativo") && a.contains("câmbio")).anyMatch(a -> a.contains("parada no valor atual"));
        assertThat(o.result().percentiles()[2][0]).isEqualTo(11_000);
        double[][] p = o.result().percentiles();
        assertThat(p[0][60]).isLessThanOrEqualTo(p[2][60]);
        assertThat(p[2][60]).isLessThanOrEqualTo(p[4][60]);
        assertThat(p[2][60]).as("com aportes e retorno positivo, a mediana passa do inicial").isGreaterThan(11_000);
    }

    @Test void portfolioIsDeterministicForTheSameInputsAndSeedOverridesIt() {
        List<Holding> holdings = List.of(h("PETR4", "PETR4", "BRL", "5000"));
        Map<String, List<Candle>> data = Map.of("PETR4", series(300, 30, 0.0004, 0.012));
        MonteCarloService s = service(holdings, data);
        Output a = s.fromPortfolio(WHO, req("0", 3, null, null)), b = s.fromPortfolio(WHO, req("0", 3, null, null));
        assertThat(a.seed()).isEqualTo(b.seed());
        assertThat(a.result().percentiles()).isDeepEqualTo(b.result().percentiles());
        assertThat(s.fromPortfolio(WHO, req("0", 3, null, 99L)).seed()).isEqualTo(99L);
    }

    @Test void positionsWithTooLittleSharedHistoryAreDroppedOneByOneAndTheShortestGoesFirst() {
        // B tem história, mas começa tarde: sem datas em comum suficientes com A. Fica de fora; A segue.
        List<Candle> a = series(300, 30, 0.0006, 0.01), b = new ArrayList<>(series(130, 10, 0.0004, 0.01));
        List<Candle> shifted = new ArrayList<>();
        for (Candle c : b) shifted.add(new Candle(c.time() + 260 * DAY, c.open(), c.high(), c.low(), c.close(), c.volume()));
        Output o = service(List.of(h("A", "A", "BRL", "3000"), h("B", "B", "BRL", "1000")), Map.of("A", a, "B", shifted)).fromPortfolio(WHO, req("0", 2, null, 3L));
        assertThat(o.coverage()).extracting(Coverage::treatment).containsExactly("HISTORICO", "CONSTANTE");
        assertThat(o.coverage().get(1).reason()).contains("curto demais").contains("120");
    }

    @Test void withoutAnyUsableHistoryItRefusesInsteadOfInventingReturns() {
        MonteCarloService none = service(List.of(h("T:LCI:x", null, "BRL", "5000")), Map.of());
        assertThatThrownBy(() -> none.fromPortfolio(WHO, req("0", 5, null, null))).isInstanceOf(MonteCarloException.class).hasMessageContaining("premissas manuais");
        MonteCarloService empty = service(List.of(), Map.of());
        assertThatThrownBy(() -> empty.fromPortfolio(WHO, req("0", 5, null, null))).isInstanceOf(MonteCarloException.class).hasMessageContaining("não tem posição com valor em reais");
        MonteCarloService noValue = new MonteCarloService(who -> List.of(new Holding("X", "X", "X", "BRL", null)), (s, l) -> List.of());
        assertThatThrownBy(() -> noValue.fromPortfolio(WHO, req("0", 5, null, null))).isInstanceOf(MonteCarloException.class);
    }
}
