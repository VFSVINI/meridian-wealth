package dev.meridianwealth.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cenários determinísticos (séries construídas em código, só em src/test) para o teste dourado do motor de timing: cada um exercita uma região
 * diferente das regras (tendências, esticamento, queda livre, estresse macro, benchmark, cripto). O teste congela o que o motor devolve hoje; os
 * testes de propriedade e de fórmula verificam a lógica por outros caminhos.
 */
final class GoldenScenarios {
    record Scenario(String name, List<Candle> candles, double macro, double[] benchmark, int perYear) {}

    private GoldenScenarios() {}

    static List<Candle> candles(double[] closes, double volumeBase, double volumeBurstFromEnd, double burst) {
        List<Candle> out = new ArrayList<>();
        double prev = closes[0];
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            double vol = volumeBase * (1 + 0.2 * Math.sin(i * 0.7)) * (closes.length - i <= volumeBurstFromEnd ? burst : 1);
            out.add(new Candle(i * 86_400_000L, prev, Math.max(prev, c) * 1.006, Math.min(prev, c) * 0.994, c, vol));
            prev = c;
        }
        return out;
    }

    private static double[] series(int n, java.util.function.IntToDoubleFunction f) {
        double[] c = new double[n];
        for (int i = 0; i < n; i++) c[i] = f.applyAsDouble(i);
        return c;
    }

    static Map<String, Scenario> all() {
        Map<String, Scenario> m = new LinkedHashMap<>();
        double[] up = series(300, i -> 100 * Math.exp(0.0015 * i + 0.012 * Math.sin(i / 3.0)));
        double[] down = series(300, i -> 100 * Math.exp(-0.0015 * i + 0.012 * Math.sin(i / 4.0)));
        double[] range = series(300, i -> 100 + 5 * Math.sin(i / 10.0));
        double[] spike = series(260, i -> i < 254 ? 100 + 2 * Math.sin(i / 6.0) : 100 + 2 * Math.sin(253 / 6.0) + (i - 253) * 4.5);
        double[] fall = series(260, i -> i < 252 ? 100 + 2 * Math.sin(i / 5.0) : 100 * Math.pow(0.95, i - 251));
        double[] dip = series(260, i -> i < 250 ? 100 + 3 * Math.sin(i / 7.0) : 100 + 3 * Math.sin(249 / 7.0) - (i - 249) * 1.2 + (i > 256 ? (i - 256) * 1.5 : 0));
        double[] bench = series(300, i -> 100 * Math.exp(0.0005 * i + 0.008 * Math.sin(i / 5.0)));
        double[] benchFast = series(300, i -> 100 * Math.exp(0.003 * i + 0.008 * Math.sin(i / 5.0)));
        m.put("uptrend", new Scenario("uptrend", candles(up, 1000, 0, 1), 0.1, null, 252));
        m.put("downtrend", new Scenario("downtrend", candles(down, 1000, 0, 1), 0.2, null, 252));
        m.put("range", new Scenario("range", candles(range, 1000, 0, 1), 0.3, null, 252));
        m.put("spike", new Scenario("spike", candles(spike, 1000, 6, 2.5), 0.1, null, 252));
        m.put("fall", new Scenario("fall", candles(fall, 1000, 8, 3.0), 0.2, null, 252));
        m.put("dip", new Scenario("dip", candles(dip, 1000, 0, 1), 0.3, null, 252));
        m.put("stressMacro", new Scenario("stressMacro", candles(range, 1000, 0, 1), 0.9, null, 252));
        double[] knife = series(260, i -> 100 + 2 * Math.sin(i / 5.0) - Math.max(0, i - 250) * 3);
        m.put("knifeMacro", new Scenario("knifeMacro", candles(knife, 1000, 0, 1), 0.9, null, 252));
        m.put("benchBehind", new Scenario("benchBehind", candles(up, 1000, 0, 1), 0.1, benchFast, 252));
        m.put("benchAhead", new Scenario("benchAhead", candles(up, 1000, 0, 1), 0.1, bench, 252));
        m.put("crypto", new Scenario("crypto", candles(down, 1000, 0, 1), 0.2, null, 365));
        return m;
    }
}
