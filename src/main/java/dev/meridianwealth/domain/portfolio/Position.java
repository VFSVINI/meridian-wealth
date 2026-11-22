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
 * Posição de um usuário em um ativo: quantidade e <b>preço médio ponderado</b> na moeda da posição, mais o <b>custo médio em reais
 * ao câmbio de cada compra</b> ({@code averageCostBrl}). Imutável: cada operação devolve a posição nova. Toda conta é em
 * {@link BigDecimal} (ADR 002) e a entrada passa por {@link MoneyBounds} antes de qualquer aritmética.
 *
 * <p>A posição é a dobra do livro de movimentações ({@link LedgerEntry}): quem a altera grava a entrada na mesma transação.
 *
 * <p>Regras:
 * <ul>
 *   <li><b>Compra:</b> o preço médio é o custo total (quantidade atual x preço médio + quantidade comprada x preço + taxas) dividido
 *       pela nova quantidade, arredondado a 6 casas (HALF_EVEN). As taxas e a corretagem entram no custo.
 *   <li><b>Venda:</b> reduz a quantidade e <b>mantém</b> o preço médio; o resultado realizado é o valor líquido (quantidade x preço
 *       menos taxas) menos o custo médio da quantidade vendida. Vender mais do que se tem é recusado; zerar fecha a posição.
 *   <li><b>Custo em reais:</b> numa posição em BRL é o próprio preço médio; numa posição em USD é a média ponderada dos custos
 *       convertidos ao câmbio informado em cada operação. Se alguma operação não tem câmbio (posição herdada do item #12), o custo
 *       em reais fica {@code null} ("não informado") e assim permanece até um ajuste com câmbio: nada é estimado.
 * </ul>
 *
 * <p>{@code version} é o contador do bloqueio otimista: o adaptador só grava se a versão lida ainda for a do banco.
 */
public record Position(UUID id, UUID userId, AssetRef asset, BigDecimal quantity, BigDecimal averagePrice, BigDecimal averageCostBrl, Currency currency,
                       Instant createdAt, Instant updatedAt, long version) {
    public static final int QUANTITY_DECIMALS = 8;
    public static final int PRICE_DECIMALS = 6;
    public static final int FEE_DECIMALS = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Resultado de uma venda: o que sobra da posição e os valores realizados (2 casas, arredondados uma só vez). */
    public record SaleOutcome(Optional<Position> remaining, BigDecimal proceedsNet, BigDecimal costOfSold, BigDecimal realized, BigDecimal proceedsNetBrl,
                              BigDecimal costOfSoldBrl, BigDecimal realizedBrl) {}

    public Position {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        quantity = MoneyBounds.requirePositive(quantity, "quantidade", QUANTITY_DECIMALS);
        averagePrice = MoneyBounds.requirePositive(averagePrice, "preço médio", PRICE_DECIMALS);
        if (averageCostBrl != null) averageCostBrl = MoneyBounds.requirePositive(averageCostBrl, "custo médio em reais", PRICE_DECIMALS);
        if (version < 0) throw new IllegalArgumentException("version");
    }

    /** Abre a posição. {@code brlPerUsd} é o câmbio da operação (reais por dólar); numa posição em BRL é ignorado, e em USD pode ser nulo. */
    public static Position open(UUID userId, AssetRef asset, BigDecimal quantity, BigDecimal unitPrice, Currency currency, BigDecimal brlPerUsd, Instant now) {
        BigDecimal price = MoneyBounds.requirePositive(unitPrice, "preço", PRICE_DECIMALS);
        return new Position(UUID.randomUUID(), userId, asset, quantity, price, brlUnit(currency, price, brlPerUsd), currency, now, now, 0);
    }

    /** Compra: novo preço médio ponderado pelo custo, com as taxas dentro dele. */
    public Position purchase(BigDecimal boughtQuantity, BigDecimal unitPrice, BigDecimal fees, BigDecimal brlPerUsd, Instant now) {
        BigDecimal q = MoneyBounds.requirePositive(boughtQuantity, "quantidade", QUANTITY_DECIMALS);
        BigDecimal p = MoneyBounds.requirePositive(unitPrice, "preço", PRICE_DECIMALS);
        BigDecimal f = MoneyBounds.requireNonNegative(fees, "taxas", FEE_DECIMALS);
        BigDecimal newQuantity = quantity.add(q);
        MoneyBounds.requirePositive(newQuantity, "quantidade total", QUANTITY_DECIMALS);
        BigDecimal paid = q.multiply(p).add(f);
        BigDecimal newAverage = quantity.multiply(averagePrice).add(paid).divide(newQuantity, MathContext.DECIMAL128).setScale(PRICE_DECIMALS, RoundingMode.HALF_EVEN);
        BigDecimal newAverageBrl;
        if (currency == Currency.BRL) {
            newAverageBrl = newAverage;
        } else if (averageCostBrl == null || brlPerUsd == null) {
            newAverageBrl = null;
        } else {
            BigDecimal fx = MoneyBounds.requirePositive(brlPerUsd, "câmbio", PRICE_DECIMALS);
            newAverageBrl = quantity.multiply(averageCostBrl).add(paid.multiply(fx)).divide(newQuantity, MathContext.DECIMAL128).setScale(PRICE_DECIMALS, RoundingMode.HALF_EVEN);
        }
        return new Position(id, userId, asset, newQuantity, newAverage, newAverageBrl, currency, createdAt, now, version);
    }

    /** Venda: reduz a quantidade, mantém o preço médio e calcula o resultado realizado (valor líquido menos o custo médio vendido). */
    public SaleOutcome sale(BigDecimal soldQuantity, BigDecimal unitPrice, BigDecimal fees, BigDecimal brlPerUsd, Instant now) {
        BigDecimal q = MoneyBounds.requirePositive(soldQuantity, "quantidade", QUANTITY_DECIMALS);
        BigDecimal p = MoneyBounds.requirePositive(unitPrice, "preço", PRICE_DECIMALS);
        BigDecimal f = MoneyBounds.requireNonNegative(fees, "taxas", FEE_DECIMALS);
        int cmp = q.compareTo(quantity);
        if (cmp > 0) {
            throw new IllegalArgumentException("A quantidade vendida (" + q.toPlainString() + ") passa da quantidade da posição (" + quantity.toPlainString() + ")");
        }
        BigDecimal net = q.multiply(p).subtract(f);
        if (net.signum() < 0) throw new IllegalArgumentException("As taxas são maiores que o valor da venda");
        BigDecimal cost = q.multiply(averagePrice);
        BigDecimal netBrl = null;
        BigDecimal costBrl = null;
        if (currency == Currency.BRL) {
            netBrl = net;
            costBrl = cost;
        } else if (brlPerUsd != null && averageCostBrl != null) {
            netBrl = net.multiply(MoneyBounds.requirePositive(brlPerUsd, "câmbio", PRICE_DECIMALS));
            costBrl = q.multiply(averageCostBrl);
        }
        Optional<Position> rest = cmp == 0 ? Optional.empty() : Optional.of(new Position(id, userId, asset, quantity.subtract(q), averagePrice, averageCostBrl, currency,
                createdAt, now, version));
        return new SaleOutcome(rest, round2(net), round2(cost), round2(net.subtract(cost)), netBrl == null ? null : round2(netBrl),
                costBrl == null ? null : round2(costBrl), netBrl == null ? null : round2(netBrl.subtract(costBrl)));
    }

    /** Substitui quantidade e preço médio pelo que o usuário declara (ajuste manual); o custo em reais passa a usar o câmbio informado. */
    public Position declare(BigDecimal newQuantity, BigDecimal newAveragePrice, BigDecimal brlPerUsd, Instant now) {
        BigDecimal price = MoneyBounds.requirePositive(newAveragePrice, "preço médio", PRICE_DECIMALS);
        return new Position(id, userId, asset, newQuantity, price, brlUnit(currency, price, brlPerUsd), currency, createdAt, now, version);
    }

    public Position withVersion(long newVersion) {
        return new Position(id, userId, asset, quantity, averagePrice, averageCostBrl, currency, createdAt, updatedAt, newVersion);
    }

    /** Custo total exato (quantidade x preço médio), sem arredondar: é de onde saem as conversões, que arredondam uma única vez. */
    public BigDecimal exactCost() { return quantity.multiply(averagePrice); }

    /** Custo total exato em reais (ao câmbio de cada compra), ou nulo se não informado. */
    public BigDecimal exactCostBrl() { return averageCostBrl == null ? null : quantity.multiply(averageCostBrl); }

    /** Custo total na moeda da posição, com 2 casas (para exibição). */
    public BigDecimal costBasis() { return round2(exactCost()); }

    private static BigDecimal brlUnit(Currency currency, BigDecimal price, BigDecimal brlPerUsd) {
        if (currency == Currency.BRL) return price;
        if (brlPerUsd == null) return null;
        return price.multiply(MoneyBounds.requirePositive(brlPerUsd, "câmbio", PRICE_DECIMALS)).setScale(PRICE_DECIMALS, RoundingMode.HALF_EVEN);
    }

    static BigDecimal round2(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN); }

    /** Resultado em percentual da base, com 2 casas. */
    static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        return part.multiply(HUNDRED, MathContext.DECIMAL128).divide(base, 2, RoundingMode.HALF_EVEN);
    }
}
