package dev.meridianwealth.domain.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Retornos diários <b>reais</b> de vários ativos, alinhados pelas datas em que <b>todos</b> têm cotação. Sem alinhar, a correlação entre
 * os ativos se perderia (um pregão de Nova York não coincide com um de São Paulo). O retorno de uma data é o do fechamento anterior em
 * comum até ela; quando um feriado de um dos mercados separa duas datas em comum, o retorno cobre o intervalo todo.
 *
 * @param symbols ativos, na ordem das linhas de {@code returns}
 * @param returns {@code returns[ativo][dia]}
 * @param dates datas (dia da época, UTC) de cada coluna de {@code returns}
 */
public record AlignedReturns(List<String> symbols, double[][] returns, long[] dates) {
    public int days() { return dates.length; }

    /**
     * @param closes fechamentos por ativo, indexados pelo dia da época (UTC) do pregão
     * @return os retornos alinhados, ou {@code null} se os ativos não têm ao menos duas datas em comum
     */
    public static AlignedReturns of(Map<String, TreeMap<Long, Double>> closes) {
        if (closes.isEmpty()) return null;
        List<String> symbols = new ArrayList<>(closes.keySet());
        Set<Long> common = null;
        for (String s : symbols) {
            Set<Long> keys = closes.get(s).keySet();
            if (common == null) common = new TreeSet<>(keys);
            else common.retainAll(keys);
        }
        if (common == null || common.size() < 2) return null;
        long[] days = common.stream().mapToLong(Long::longValue).toArray();
        double[][] r = new double[symbols.size()][days.length - 1];
        for (int a = 0; a < symbols.size(); a++) {
            TreeMap<Long, Double> c = closes.get(symbols.get(a));
            for (int d = 1; d < days.length; d++) {
                double prev = c.get(days[d - 1]), now = c.get(days[d]);
                if (!(prev > 0) || !(now > 0)) throw new IllegalArgumentException("Fechamento inválido em " + symbols.get(a));
                r[a][d - 1] = now / prev - 1;
            }
        }
        long[] dates = new long[days.length - 1];
        System.arraycopy(days, 1, dates, 0, dates.length);
        return new AlignedReturns(List.copyOf(symbols), r, dates);
    }

    /** Retorno diário da carteira de pesos fixos {@code w}: média ponderada dos retornos de cada ativo no dia. */
    public double[] portfolioDaily(double[] w) {
        double[] out = new double[days()];
        for (int d = 0; d < out.length; d++) {
            double t = 0;
            for (int a = 0; a < w.length; a++) t += w[a] * returns[a][d];
            out[d] = t;
        }
        return out;
    }
}
