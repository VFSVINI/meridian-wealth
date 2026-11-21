package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.Fundamentals;
import java.util.Optional;

public interface FundamentalsPort {
    Optional<Fundamentals> fundamentals(String symbol);
}
