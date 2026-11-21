package dev.chronosquant.application;

import dev.chronosquant.domain.Assessment;
import dev.chronosquant.domain.Candle;
import dev.chronosquant.domain.MacroSnapshot;
import java.util.List;

/** {@code mode}: REAL, MISTO ou SIMULADO. {@code flow}: fluxo simulado ligado sobre a cotação real. */
public record TickBatch(long ts, String mode, boolean flow, MacroSnapshot macro, List<Tick> ticks) {
    public record Tick(String symbol, Candle candle, Assessment assessment, boolean newBar, String source,
                       boolean simulated, boolean marketOpen) {}
}
