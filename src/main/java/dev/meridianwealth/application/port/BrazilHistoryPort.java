package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.HistorySeries;
import dev.meridianwealth.domain.RateHistory;
import java.time.LocalDate;
import java.util.Optional;

/** Porta de saída: o histórico de uma série oficial do Brasil numa janela de datas; vazio quando o provedor não respondeu (nada é estimado). */
public interface BrazilHistoryPort {
    Optional<RateHistory> history(HistorySeries series, LocalDate from, LocalDate to);
}
