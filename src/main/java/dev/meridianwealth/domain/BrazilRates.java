package dev.meridianwealth.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Juros, inflação e câmbio oficial do Brasil, como o BCB publicou. Cada indicador pode faltar (a fonte não
 * respondeu ainda): nada é estimado nem copiado de outro indicador. {@code fetchedAt} é quando o sistema
 * conseguiu atualizar algum indicador pela última vez (nulo se nunca conseguiu).
 */
public record BrazilRates(Map<RateSeries, RateObservation> observations, Instant fetchedAt) {
    public BrazilRates {
        EnumMap<RateSeries, RateObservation> copy = new EnumMap<>(RateSeries.class);
        copy.putAll(observations);
        observations = Map.copyOf(copy);
    }

    public static BrazilRates unavailable() { return new BrazilRates(Map.of(), null); }

    public Optional<RateObservation> get(RateSeries series) { return Optional.ofNullable(observations.get(series)); }

    /** Cópia com o indicador atualizado; o horário de atualização passa a ser {@code now}. */
    public BrazilRates with(RateSeries series, RateObservation observation, Instant now) {
        EnumMap<RateSeries, RateObservation> next = new EnumMap<>(RateSeries.class);
        next.putAll(observations);
        next.put(series, observation);
        return new BrazilRates(next, now);
    }
}
