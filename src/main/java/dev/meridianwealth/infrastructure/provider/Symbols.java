package dev.meridianwealth.infrastructure.provider;

/** Códigos do ativo em cada provedor. Nulo significa que o provedor não cobre o ativo. */
public record Symbols(String yahoo, String stooq, String finnhub, String fxBase, String fxQuote) {}
