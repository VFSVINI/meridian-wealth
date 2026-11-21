package dev.meridianwealth.domain.thesis;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.MoneyBounds;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Posição da carteira para o tradutor geopolítico e para as teses: o tipo, o setor (nulo quando não se conhece) e o valor em
 * reais informado pelo usuário. O setor só existe para ativos do universo do painel; para uma posição informada só pelo tipo
 * ele fica nulo e vira "não informado", nunca um palpite.
 */
public record Holding(String label, AssetType type, String sector, BigDecimal valueBrl) {
    public Holding {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        if (!type.investable()) throw new IllegalArgumentException(type.label() + " é só referência e não entra em carteira");
        sector = sector == null || sector.isBlank() ? null : sector;
        valueBrl = MoneyBounds.requirePositive(valueBrl, label);
    }
}
