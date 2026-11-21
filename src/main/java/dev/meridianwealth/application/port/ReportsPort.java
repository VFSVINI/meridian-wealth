package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.reports.IssuerReports;
import java.util.List;
import java.util.Optional;

/** Porta de saída: relatórios periódicos curados (CVM), com a fonte de cada dado. */
public interface ReportsPort {
    List<IssuerReports> all();

    Optional<IssuerReports> find(String symbol);
}
