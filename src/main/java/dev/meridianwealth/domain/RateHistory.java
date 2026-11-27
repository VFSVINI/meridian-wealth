package dev.meridianwealth.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Histórico de uma série oficial, em ordem crescente de data, com os valores exatamente como o Banco Central os publicou. */
public record RateHistory(HistorySeries series, List<Point> points, Instant fetchedAt) {
    public record Point(LocalDate date, BigDecimal value) {
        public Point {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(value, "value");
        }
    }

    public RateHistory {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        points = List.copyOf(points);
        if (points.isEmpty()) throw new IllegalArgumentException("Histórico sem pontos: nada é inventado");
        for (int i = 1; i < points.size(); i++) {
            if (!points.get(i).date().isAfter(points.get(i - 1).date())) throw new IllegalArgumentException("Pontos fora de ordem em " + series);
        }
    }

    public Point last() { return points.get(points.size() - 1); }

    /** Só os pontos a partir de {@code from} (inclusive). */
    public List<Point> since(LocalDate from) { return points.stream().filter(p -> !p.date().isBefore(from)).toList(); }
}
