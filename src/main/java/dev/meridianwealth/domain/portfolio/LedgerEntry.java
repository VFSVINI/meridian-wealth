package dev.meridianwealth.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma entrada do livro de movimentações: imutável e só acrescentada, nunca alterada. Guarda o que o usuário informou (quantidade,
 * preço, taxas, data, câmbio) e o que a operação produziu ({@code *After} é o estado da posição logo depois; o resultado realizado só
 * existe nas vendas). A posição atual é a dobra do livro: o último {@code *After} de cada ativo.
 *
 * <p>{@code idempotencyKey} e {@code requestHash} permitem reconhecer uma repetição da mesma requisição (ver
 * {@code docs/carteira.md}). Valores monetários realizados saem arredondados uma só vez, a partir dos valores exatos.
 */
public record LedgerEntry(UUID id, UUID userId, UUID positionId, AssetRef asset, EntryType type, LocalDate tradeDate, BigDecimal quantity, BigDecimal unitPrice,
                          BigDecimal fees, Currency currency, BigDecimal fxBrlPerUsd, FxSource fxSource, LocalDate fxDate, BigDecimal quantityAfter,
                          BigDecimal averagePriceAfter, BigDecimal averageCostBrlAfter, BigDecimal realizedPnl, BigDecimal realizedPnlBrl, long versionAfter,
                          String idempotencyKey, String requestHash, Instant createdAt) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(fees, "fees");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(quantityAfter, "quantityAfter");
        Objects.requireNonNull(createdAt, "createdAt");
        if ((type == EntryType.SELL) != (realizedPnl != null)) throw new IllegalArgumentException("Só a venda tem resultado realizado");
        if ((idempotencyKey == null) != (requestHash == null)) throw new IllegalArgumentException("A chave de idempotência e o hash do conteúdo andam juntos");
        if (currency == Currency.BRL && fxBrlPerUsd != null) throw new IllegalArgumentException("Operação em BRL não tem câmbio");
    }

    /** Valor bruto da operação em reais (quantidade x preço x câmbio), ou nulo se o câmbio não foi registrado. Base do limite mensal de vendas. */
    public BigDecimal grossBrl() {
        if (unitPrice == null) return null;
        BigDecimal gross = quantity.multiply(unitPrice);
        if (currency == Currency.BRL) return gross;
        return fxBrlPerUsd == null ? null : gross.multiply(fxBrlPerUsd);
    }
}
