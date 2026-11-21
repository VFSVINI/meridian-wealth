package dev.meridianwealth.domain.composition;

import java.math.BigDecimal;

/** Item da alocação de um fundo, em reais, como o informe oficial publica; o percentual é calculado sobre o ativo total. */
public record Allocation(String code, String label, BigDecimal valueBrl, BigDecimal percentOfAssets) {}
