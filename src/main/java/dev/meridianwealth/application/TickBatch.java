package dev.meridianwealth.application;

import dev.meridianwealth.domain.Assessment;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Freshness;
import dev.meridianwealth.domain.MacroSnapshot;
import java.util.List;

/**
 * Lote publicado no stream. {@code mode}: REAL (todos com cotação de provedor), PARCIAL (algum ativo
 * sem dado em tempo real) ou SEM_DADOS (nenhum ativo com cotação de provedor).
 */
public record TickBatch(long ts, String mode, MacroSnapshot macro, List<Tick> ticks) {
    /**
     * Estado de um ativo. {@code candle} e {@code assessment} são nulos quando não há dado real
     * suficiente; {@code source}, {@code quoteTime} e {@code freshness} identificam a procedência e
     * {@code stale} avisa que, com o pregão aberto, a cotação passou da idade tolerada para o seu selo.
     */
    public record Tick(String symbol, DataStatus status, Candle candle, Assessment assessment, boolean newBar,
                       String source, long quoteTime, Freshness freshness, long nextUpdateAt, boolean marketOpen,
                       boolean stale) {}
}
