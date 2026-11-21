package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.composition.FundComposition;
import java.util.List;
import java.util.Optional;

/** Porta de saída: composição curada dos fundos, com a fonte de cada dado. */
public interface CompositionPort {
    List<FundComposition> all();

    Optional<FundComposition> find(String fundSymbol);
}
