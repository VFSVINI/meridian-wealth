package dev.meridianwealth.domain.composition;

import java.math.BigDecimal;

/** Números do fundo como a fonte os publica (qualquer campo pode faltar); {@code asOf} é a data a que se referem. */
public record FundMetrics(BigDecimal totalAreaM2, BigDecimal physicalVacancyPercent,
                          BigDecimal financialVacancyPercent, String asOf) {}
