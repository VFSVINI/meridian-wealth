package dev.meridianwealth.application;

import dev.meridianwealth.domain.Candle;

/** Barra viva derivada da última cotação real: {@code newBar} indica nova barra; senão substitui a última. */
public record LiveUpdate(Candle candle, boolean newBar, QuoteMeta quote, boolean marketOpen) {}
