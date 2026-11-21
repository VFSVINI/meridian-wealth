package dev.chronosquant.application;

import dev.chronosquant.domain.Candle;

/** Atualização de uma barra: {@code newBar} indica nova barra; senão substitui a última. */
public record LiveUpdate(Candle candle, boolean newBar, String source, boolean simulated, boolean marketOpen) {}
