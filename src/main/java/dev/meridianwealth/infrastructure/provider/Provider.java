package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.domain.Freshness;
import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo das fontes de cotação com o atraso nominal de cada uma (contrato público do provedor).
 * O selo efetivo de uma cotação também depende do pregão: fora dele, tudo é fechamento.
 */
public enum Provider {
    /** Finnhub REST: tempo real para ações dos EUA segundo a documentação pública. */
    FINNHUB("Finnhub", Freshness.REAL_TIME),
    /** Finnhub WebSocket de negócios (trades) para ações dos EUA. */
    FINNHUB_WS("Finnhub WS", Freshness.REAL_TIME),
    /** Feed pago plugável (desligado por padrão); o atraso depende do plano contratado. */
    PAID_FEED("Feed pago", Freshness.REAL_TIME),
    /** Binance WebSocket (miniTicker, 1 s por par, cotado em USDT). */
    BINANCE_WS("Binance WS", Freshness.REAL_TIME),
    /** Coinbase Exchange WebSocket (canal ticker, a cada negócio, cotado em USD). */
    COINBASE_WS("Coinbase WS", Freshness.REAL_TIME),
    /** Kraken WebSocket v2 (canal ticker, a cada negócio, cotado em USD). */
    KRAKEN_WS("Kraken WS", Freshness.REAL_TIME),
    YAHOO("Yahoo", Freshness.DELAYED),
    STOOQ("Stooq", Freshness.DELAYED),
    /** brapi.dev: usado só para histórico diário da B3; a barra diária é fechamento. */
    BRAPI("brapi", Freshness.END_OF_DAY),
    /** Frankfurter: taxa de referência diária do BCE. */
    FRANKFURTER("Frankfurter", Freshness.END_OF_DAY),
    /** Último dado real gravado no banco. */
    STORED("Banco", Freshness.STORED);

    private final String label;
    private final Freshness nominal;

    Provider(String label, Freshness nominal) {
        this.label = label;
        this.nominal = nominal;
    }

    public String label() { return label; }

    public Freshness nominal() { return nominal; }

    public static Optional<Provider> byLabel(String label) {
        return Arrays.stream(values()).filter(p -> p.label.equals(label)).findFirst();
    }
}
