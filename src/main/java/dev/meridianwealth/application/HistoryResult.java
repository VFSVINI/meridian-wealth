package dev.meridianwealth.application;

import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Freshness;
import java.util.List;

/**
 * Histórico real de um ativo. Vazio quando nenhum provedor respondeu e o banco não tem dados.
 * {@code quoteTime} é o horário da última cotação na origem (ou da última barra, se a fonte não informar).
 */
public record HistoryResult(List<Candle> candles, String source, long quoteTime, Freshness freshness) {
    public HistoryResult {
        candles = List.copyOf(candles);
    }

    public static HistoryResult empty() { return new HistoryResult(List.of(), null, 0, null); }

    public boolean available() { return !candles.isEmpty(); }
}
