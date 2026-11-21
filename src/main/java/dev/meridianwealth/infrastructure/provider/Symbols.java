package dev.meridianwealth.infrastructure.provider;

/** Códigos do ativo em cada provedor. Nulo significa que o provedor não cobre o ativo. */
public record Symbols(String yahoo, String stooq, String finnhub, String fxBase, String fxQuote, String brapi) {
    static Symbols yahoo(String yahoo) { return new Symbols(yahoo, null, null, null, null, null); }

    static Symbols us(String ticker, String stooq) { return new Symbols(ticker, stooq, ticker, null, null, null); }

    static Symbols b3(String ticker) { return new Symbols(ticker + ".SA", null, null, null, null, ticker); }

    static Symbols fx(String yahoo, String base, String quote) { return new Symbols(yahoo, null, null, base, quote, null); }
}
