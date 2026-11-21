package dev.meridianwealth.domain;

/** Ativo acompanhado. Apenas metadados de cadastro: preços vêm sempre de provedores reais. */
public record Instrument(String symbol, String name, String region, String assetClass, String sector) {}
