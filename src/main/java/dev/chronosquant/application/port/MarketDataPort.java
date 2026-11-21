package dev.chronosquant.application.port;

import dev.chronosquant.application.HistoryResult;
import dev.chronosquant.application.LiveUpdate;
import dev.chronosquant.domain.Instrument;
import dev.chronosquant.domain.MacroSnapshot;
import java.util.List;

/** Porta de dados de mercado: histórico, atualização contínua e macro. */
public interface MarketDataPort {
    List<Instrument> universe();

    HistoryResult history(String symbol, int bars);

    LiveUpdate next(String symbol);

    MacroSnapshot nextMacro();
}
