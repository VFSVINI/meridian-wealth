package dev.meridianwealth.domain;

/** Ativo acompanhado. {@code startPrice} e {@code annVol} calibram apenas o simulador de fallback. */
public record Instrument(String symbol, String name, String region, String assetClass, String sector,
                         double startPrice, double annVol) {}
