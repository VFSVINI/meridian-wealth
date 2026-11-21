package dev.meridianwealth.application.port;

import dev.meridianwealth.application.HistoryResult;
import dev.meridianwealth.application.LiveUpdate;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MacroSnapshot;
import java.util.List;

/** Porta de dados de mercado: histórico, atualização contínua e macro. */
public interface MarketDataPort {
    List<Instrument> universe();

    HistoryResult history(String symbol, int bars);

    LiveUpdate next(String symbol);

    MacroSnapshot nextMacro();
}
