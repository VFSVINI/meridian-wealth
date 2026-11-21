package dev.meridianwealth.application.port;

import dev.meridianwealth.application.HistoryResult;
import dev.meridianwealth.application.LiveUpdate;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MacroSnapshot;
import java.util.List;
import java.util.Optional;

/** Porta de dados de mercado reais: histórico, barra viva a partir da última cotação e macro. */
public interface MarketDataPort {
    List<Instrument> universe();

    /** Histórico real (provedores e, em último caso, banco); vazio quando não há nada real. */
    HistoryResult history(String symbol, int bars);

    /** Barra viva a partir da última cotação real; vazio quando o ativo não tem cotação real. */
    Optional<LiveUpdate> next(String symbol);

    boolean marketOpen(String symbol);

    MacroSnapshot macro();
}
