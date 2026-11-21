package dev.meridianwealth.infrastructure.provider;

/** Códigos do ativo em cada provedor. Nulo significa que o provedor não cobre o ativo. */
public record Symbols(String yahoo, String stooq, String finnhub, String fxBase, String fxQuote, String brapi,
                      CryptoPair crypto) {
    /** Par de um criptoativo em cada bolsa (Binance negocia em USDT; Coinbase e Kraken em USD). */
    public record CryptoPair(String binance, String coinbase, String kraken) {}

    static Symbols yahoo(String yahoo) { return new Symbols(yahoo, null, null, null, null, null, null); }

    static Symbols us(String ticker, String stooq) { return new Symbols(ticker, stooq, ticker, null, null, null, null); }

    static Symbols b3(String ticker) { return new Symbols(ticker + ".SA", null, null, null, null, ticker, null); }

    static Symbols fx(String yahoo, String base, String quote) {
        return new Symbols(yahoo, null, null, base, quote, null, null);
    }

    /** Cripto: histórico diário da Yahoo e cotação por stream (Binance, com failover Coinbase e Kraken). */
    static Symbols crypto(String yahoo, String binance, String coinbase, String kraken) {
        return new Symbols(yahoo, null, null, null, null, null, new CryptoPair(binance, coinbase, kraken));
    }
}
