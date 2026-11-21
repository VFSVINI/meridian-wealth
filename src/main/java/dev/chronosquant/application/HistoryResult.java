package dev.chronosquant.application;

import dev.chronosquant.domain.Candle;
import java.util.List;

public record HistoryResult(List<Candle> candles, String source, boolean simulated) {}
