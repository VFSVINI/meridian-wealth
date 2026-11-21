package dev.meridianwealth.application;

import dev.meridianwealth.domain.Candle;
import java.util.List;

public record HistoryResult(List<Candle> candles, String source, boolean simulated) {}
