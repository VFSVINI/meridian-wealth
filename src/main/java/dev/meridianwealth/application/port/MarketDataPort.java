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

    /** Cadastro de um ativo negociado, mesmo fora do universo curado (catálogo); vazio quando o símbolo não existe. */
    default Optional<Instrument> find(String symbol) {
        return universe().stream().filter(i -> i.symbol().equals(symbol)).findFirst();
    }

    /** Busca por símbolo ou nome no catálogo de ativos negociados. */
    default List<Instrument> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) return List.of();
        return universe().stream().filter(i -> (i.symbol() + " " + i.name()).toLowerCase(java.util.Locale.ROOT).contains(q)).limit(limit).toList();
    }

    /** Histórico real (provedores e, em último caso, banco); vazio quando não há nada real. */
    HistoryResult history(String symbol, int bars);

    /** Barra viva a partir da última cotação real; vazio quando o ativo não tem cotação real. */
    Optional<LiveUpdate> next(String symbol);

    boolean marketOpen(String symbol);

    MacroSnapshot macro();
}
