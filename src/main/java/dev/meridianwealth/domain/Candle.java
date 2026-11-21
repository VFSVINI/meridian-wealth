package dev.meridianwealth.domain;

/** Barra OHLCV. {@code time} em epoch millis. */
public record Candle(long time, double open, double high, double low, double close, double volume) {}
