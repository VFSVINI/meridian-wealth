package dev.meridianwealth.domain.portfolio;

import dev.meridianwealth.domain.MoneyBounds;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Posição de um usuário em um ativo: quantidade e <b>preço médio ponderado</b> na moeda da posição. Imutável: cada operação devolve
 * a posição nova. Toda conta é em {@link BigDecimal} (ADR 002) e a entrada passa por {@link MoneyBounds} antes de qualquer
 * aritmética.
 *
 * <p>Regras:
 * <ul>
 *   <li><b>Compra:</b> o preço médio é o custo total (quantidade atual x preço médio + quantidade comprada x preço) dividido pela
 *       nova quantidade, arredondado a 6 casas (HALF_EVEN).
 *   <li><b>Venda:</b> reduz a quantidade e <b>mantém</b> o preço médio (é assim que o custo médio funciona); vender mais do que se tem
 *       é recusado; zerar a quantidade fecha a posição. O resultado realizado e o imposto são do livro de movimentações (item #13).
 *   <li>O preço informado deve incluir corretagem e taxas se o usuário quiser que elas entrem no custo: a plataforma não as estima.
 * </ul>
 *
 * <p>{@code version} é o contador do bloqueio otimista: o adaptador só grava se a versão lida ainda for a do banco.
 */
public record Position(UUID id, UUID userId, AssetRef asset, BigDecimal quantity, BigDecimal averagePrice, Currency currency, Instant createdAt,
                       Instant updatedAt, long version) {
    public static final int QUANTITY_DECIMALS = 8;
    public static final int PRICE_DECIMALS = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Position {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        quantity = MoneyBounds.requirePositive(quantity, "quantidade", QUANTITY_DECIMALS);
        averagePrice = MoneyBounds.requirePositive(averagePrice, "preço médio", PRICE_DECIMALS);
        if (version < 0) throw new IllegalArgumentException("version");
    }

    public static Position open(UUID userId, AssetRef asset, BigDecimal quantity, BigDecimal unitPrice, Currency currency, Instant now) {
        return new Position(UUID.randomUUID(), userId, asset, quantity, unitPrice, currency, now, now, 0);
    }

    /** Compra: novo preço médio ponderado pelo custo. */
    public Position purchase(BigDecimal boughtQuantity, BigDecimal unitPrice, Instant now) {
        BigDecimal q = MoneyBounds.requirePositive(boughtQuantity, "quantidade", QUANTITY_DECIMALS);
        BigDecimal p = MoneyBounds.requirePositive(unitPrice, "preço", PRICE_DECIMALS);
        BigDecimal newQuantity = quantity.add(q);
        MoneyBounds.requirePositive(newQuantity, "quantidade total", QUANTITY_DECIMALS);
        BigDecimal totalCost = quantity.multiply(averagePrice).add(q.multiply(p));
        BigDecimal newAverage = totalCost.divide(newQuantity, MathContext.DECIMAL128).setScale(PRICE_DECIMALS, RoundingMode.HALF_EVEN);
        return new Position(id, userId, asset, newQuantity, newAverage, currency, createdAt, now, version);
    }

    /** Venda: reduz a quantidade e mantém o preço médio. Vazio quando a venda zera a posição. */
    public Optional<Position> sale(BigDecimal soldQuantity, Instant now) {
        BigDecimal q = MoneyBounds.requirePositive(soldQuantity, "quantidade", QUANTITY_DECIMALS);
        int cmp = q.compareTo(quantity);
        if (cmp > 0) {
            throw new IllegalArgumentException("A quantidade vendida (" + q.toPlainString() + ") passa da quantidade da posição (" + quantity.toPlainString() + ")");
        }
        if (cmp == 0) return Optional.empty();
        return Optional.of(new Position(id, userId, asset, quantity.subtract(q), averagePrice, currency, createdAt, now, version));
    }

    /** Substitui quantidade e preço médio pelo que o usuário declara (ajuste manual de uma posição). */
    public Position declare(BigDecimal newQuantity, BigDecimal newAveragePrice, Instant now) {
        return new Position(id, userId, asset, newQuantity, newAveragePrice, currency, createdAt, now, version);
    }

    public Position withVersion(long newVersion) {
        return new Position(id, userId, asset, quantity, averagePrice, currency, createdAt, updatedAt, newVersion);
    }

    /** Custo total na moeda da posição (quantidade x preço médio), com 2 casas. */
    public BigDecimal costBasis() { return quantity.multiply(averagePrice).setScale(2, RoundingMode.HALF_EVEN); }

    /** Resultado não realizado em percentual do custo, com 2 casas. */
    static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        return part.multiply(HUNDRED, MathContext.DECIMAL128).divide(base, 2, RoundingMode.HALF_EVEN);
    }
}
