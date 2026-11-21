package dev.chronosquant.application.port;

import dev.chronosquant.domain.Fundamentals;
import java.util.Optional;

public interface FundamentalsPort {
    Optional<Fundamentals> fundamentals(String symbol);
}
