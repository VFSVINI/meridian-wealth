package dev.meridianwealth.domain;

/**
 * Ativo acompanhado. Apenas metadados de cadastro: preços vêm sempre de provedores reais.
 *
 * <p>{@code currency} é a moeda de cotação informada pela bolsa (ex.: {@code GBp} e {@code ZAc} são
 * centavos de libra e de rand); {@code benchmark} é o símbolo usado na força relativa (pode ser nulo).
 */
public record Instrument(String symbol, String name, String region, String assetClass, String sector,
                         Exchange exchange, String currency, String benchmark) {

    /**
     * Barras por ano para anualizar volatilidade e Sharpe: criptoativos negociam todos os dias do ano
     * (raiz de 365); os demais ativos têm cerca de 252 pregões (raiz de 252).
     */
    public int periodsPerYear() {
        return exchange == Exchange.CRYPTO ? 365 : 252;
    }

    public static Instrument of(String symbol, String name, Exchange exchange, String assetClass, String sector,
                                String currency, String benchmark) {
        return new Instrument(symbol, name, exchange.region(), assetClass, sector, exchange, currency, benchmark);
    }
}
