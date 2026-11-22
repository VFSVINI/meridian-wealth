package dev.meridianwealth.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Porta de saída: PTAX (venda) do Banco Central de uma data, para registrar o câmbio de cada operação em dólar. */
public interface FxHistoryPort {
    /** @param referenceDate dia útil da cotação (igual ou anterior à data pedida) */
    record PtaxQuote(BigDecimal brlPerUsd, LocalDate referenceDate) {}

    /** O PTAX do último dia útil até {@code date}; vazio se o Banco Central não respondeu (nunca estimado). */
    Optional<PtaxQuote> ptaxSellOnOrBefore(LocalDate date);
}
