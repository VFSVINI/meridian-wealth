package dev.meridianwealth.infrastructure.provider;

import static dev.meridianwealth.domain.Exchange.ASX;
import static dev.meridianwealth.domain.Exchange.B3;
import static dev.meridianwealth.domain.Exchange.EURONEXT_AMS;
import static dev.meridianwealth.domain.Exchange.GLOBAL_24H;
import static dev.meridianwealth.domain.Exchange.HKEX;
import static dev.meridianwealth.domain.Exchange.JSE;
import static dev.meridianwealth.domain.Exchange.LSE;
import static dev.meridianwealth.domain.Exchange.NYSE;
import static dev.meridianwealth.domain.Exchange.TADAWUL;
import static dev.meridianwealth.domain.Exchange.TSE;
import static dev.meridianwealth.domain.Exchange.XETRA;

import dev.meridianwealth.domain.Instrument;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Universo acompanhado e mapeamento de símbolos por provedor. Todos os códigos foram conferidos
 * contra a API real da Yahoo em 2026-11-20 (bolsa, moeda e fuso informados pelo próprio provedor).
 */
public final class Universe {
    private Universe() {}

    public record Entry(Instrument instrument, Symbols symbols) {}

    private static Entry e(Instrument i, Symbols s) { return new Entry(i, s); }

    private static final List<Entry> ALL = List.of(
            // EUA
            e(Instrument.of("SPY", "S&P 500 ETF", NYSE, "ETF", "amplo", "USD", null), Symbols.us("SPY", "spy.us")),
            e(Instrument.of("AAPL", "Apple", NYSE, "Ação", "tecnologia", "USD", "SPY"), Symbols.us("AAPL", "aapl.us")),
            e(Instrument.of("MSFT", "Microsoft", NYSE, "Ação", "tecnologia", "USD", "SPY"), Symbols.us("MSFT", "msft.us")),
            e(Instrument.of("NVDA", "NVIDIA", NYSE, "Ação", "tecnologia", "USD", "SPY"), Symbols.us("NVDA", "nvda.us")),
            // Brasil
            e(Instrument.of("IBOV", "Ibovespa", B3, "Índice", "amplo", "BRL", null), Symbols.yahoo("^BVSP")),
            e(Instrument.of("PETR4", "Petrobras PN", B3, "Ação", "energia", "BRL", "IBOV"), Symbols.b3("PETR4")),
            e(Instrument.of("VALE3", "Vale ON", B3, "Ação", "mineracao", "BRL", "IBOV"), Symbols.b3("VALE3")),
            e(Instrument.of("ITUB4", "Itaú Unibanco PN", B3, "Ação", "financeiro", "BRL", "IBOV"), Symbols.b3("ITUB4")),
            // Europa
            e(Instrument.of("STOXX50", "Euro Stoxx 50", XETRA, "Índice", "amplo", "EUR", null), Symbols.yahoo("^STOXX50E")),
            e(Instrument.of("SAP", "SAP SE", XETRA, "Ação", "tecnologia", "EUR", "STOXX50"), Symbols.yahoo("SAP.DE")),
            e(Instrument.of("ASML", "ASML Holding", EURONEXT_AMS, "Ação", "tecnologia", "EUR", "STOXX50"), Symbols.yahoo("ASML.AS")),
            e(Instrument.of("SHEL", "Shell plc", LSE, "Ação", "energia", "GBp", "STOXX50"), Symbols.yahoo("SHEL.L")),
            // Ásia
            e(Instrument.of("N225", "Nikkei 225", TSE, "Índice", "amplo", "JPY", null), Symbols.yahoo("^N225")),
            e(Instrument.of("TOYOTA", "Toyota Motor", TSE, "Ação", "consumo", "JPY", "N225"), Symbols.yahoo("7203.T")),
            e(Instrument.of("HSI", "Hang Seng", HKEX, "Índice", "amplo", "HKD", null), Symbols.yahoo("^HSI")),
            e(Instrument.of("TENCENT", "Tencent Holdings", HKEX, "Ação", "tecnologia", "HKD", "HSI"), Symbols.yahoo("0700.HK")),
            // Oceania
            e(Instrument.of("ASX200", "S&P/ASX 200", ASX, "Índice", "amplo", "AUD", null), Symbols.yahoo("^AXJO")),
            e(Instrument.of("BHP", "BHP Group", ASX, "Ação", "mineracao", "AUD", "ASX200"), Symbols.yahoo("BHP.AX")),
            // Oriente Médio e África
            e(Instrument.of("ARAMCO", "Saudi Aramco", TADAWUL, "Ação", "energia", "SAR", null), Symbols.yahoo("2222.SR")),
            e(Instrument.of("NASPERS", "Naspers", JSE, "Ação", "tecnologia", "ZAc", null), Symbols.yahoo("NPN.JO")),
            // Global
            e(Instrument.of("EURUSD", "EUR/USD", GLOBAL_24H, "Câmbio", "cambio", "USD", null), Symbols.fx("EURUSD=X", "EUR", "USD")),
            e(Instrument.of("GBPUSD", "GBP/USD", GLOBAL_24H, "Câmbio", "cambio", "USD", null), Symbols.fx("GBPUSD=X", "GBP", "USD")),
            e(Instrument.of("USDBRL", "USD/BRL", GLOBAL_24H, "Câmbio", "cambio", "BRL", null), Symbols.fx("BRL=X", "USD", "BRL")),
            e(Instrument.of("BRENT", "Petróleo Brent", GLOBAL_24H, "Commodity", "energia", "USD", null), Symbols.yahoo("BZ=F")),
            e(Instrument.of("GOLD", "Ouro", GLOBAL_24H, "Commodity", "refugio", "USD", null), Symbols.yahoo("GC=F")));

    public static List<Entry> all() { return ALL; }

    public static List<Instrument> instruments() { return ALL.stream().map(Entry::instrument).toList(); }

    public static Entry entry(String symbol) {
        return ALL.stream().filter(x -> x.instrument().symbol().equals(symbol)).findFirst()
                .orElseThrow(() -> new NoSuchElementException(symbol));
    }
}
