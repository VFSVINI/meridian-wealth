package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.domain.Instrument;
import java.util.List;
import java.util.NoSuchElementException;

/** Universo acompanhado e mapeamento de símbolos por provedor. */
public final class Universe {
    private Universe() {}

    public record Entry(Instrument instrument, Symbols symbols) {}

    private static final List<Entry> ALL = List.of(
            new Entry(new Instrument("SPY", "S&P 500 ETF", "EUA", "ETF", "amplo", 520, 0.16),
                    new Symbols("SPY", "spy.us", "SPY", null, null)),
            new Entry(new Instrument("AAPL", "Apple", "EUA", "Ação", "tecnologia", 190, 0.28),
                    new Symbols("AAPL", "aapl.us", "AAPL", null, null)),
            new Entry(new Instrument("MSFT", "Microsoft", "EUA", "Ação", "tecnologia", 420, 0.25),
                    new Symbols("MSFT", "msft.us", "MSFT", null, null)),
            new Entry(new Instrument("NVDA", "NVIDIA", "EUA", "Ação", "tecnologia", 880, 0.50),
                    new Symbols("NVDA", "nvda.us", "NVDA", null, null)),
            new Entry(new Instrument("SAP", "SAP SE", "Europa", "Ação", "tecnologia", 175, 0.26),
                    new Symbols("SAP.DE", "sap.de", null, null, null)),
            new Entry(new Instrument("STOXX50", "Euro Stoxx 50", "Europa", "Índice", "amplo", 4900, 0.17),
                    new Symbols("^STOXX50E", "^sx5e", null, null, null)),
            new Entry(new Instrument("EURUSD", "EUR/USD", "Global", "Câmbio", "cambio", 1.08, 0.07),
                    new Symbols("EURUSD=X", "eurusd", null, "EUR", "USD")),
            new Entry(new Instrument("GBPUSD", "GBP/USD", "Global", "Câmbio", "cambio", 1.27, 0.08),
                    new Symbols("GBPUSD=X", "gbpusd", null, "GBP", "USD")),
            new Entry(new Instrument("BRENT", "Petróleo Brent", "Global", "Commodity", "energia", 82, 0.32),
                    new Symbols("BZ=F", null, null, null, null)),
            new Entry(new Instrument("GOLD", "Ouro", "Global", "Commodity", "refugio", 2400, 0.15),
                    new Symbols("GC=F", "xauusd", null, null, null)));

    public static List<Entry> all() { return ALL; }

    public static List<Instrument> instruments() { return ALL.stream().map(Entry::instrument).toList(); }

    public static Entry entry(String symbol) {
        return ALL.stream().filter(e -> e.instrument().symbol().equals(symbol)).findFirst()
                .orElseThrow(() -> new NoSuchElementException(symbol));
    }
}
