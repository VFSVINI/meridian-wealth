package dev.meridianwealth.application;

import dev.meridianwealth.domain.Candle;

/** Atualização de uma barra: {@code newBar} indica nova barra; senão substitui a última. */
public record LiveUpdate(Candle candle, boolean newBar, String source, boolean simulated, boolean marketOpen) {}
