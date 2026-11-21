package dev.chronosquant.domain;

/** Fundamentos disponíveis em APIs gratuitas. Campos ausentes ficam nulos. */
public record Fundamentals(String symbol, String industry, Double marketCap, Double peRatio,
                           Double netMargin, Double revenueGrowth, String source) {}
