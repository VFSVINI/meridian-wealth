package dev.meridianwealth.domain.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Valorização de uma posição com dado real: preço de mercado do provedor (com fonte, horário e selo de atraso) e câmbio oficial
 * (PTAX). Nada é estimado: sem cotação, a posição fica avaliada só pelo custo ({@link Basis#COST}) e o valor de mercado e o
 * resultado ficam nulos ("não informado"); sem PTAX, os valores em reais de posições em dólar ficam nulos.
 *
 * <p>O resultado não realizado vem em duas medidas: na <b>moeda da posição</b> (só variação de preço) e em <b>reais</b>, que é o valor de
 * hoje ao PTAX de hoje menos o custo ao câmbio de cada compra (guardado no livro), e por isso inclui a variação cambial. O resultado em
 * reais só existe com cotação, PTAX e custo em reais registrado; posição herdada sem câmbio de compra fica "não informado".
 */
public final class PositionValuation {
    public enum Basis { MARKET, COST }

    /** Cotação de mercado já resolvida: {@code currency} é a moeda do preço. */
    public record MarketPrice(BigDecimal price, Currency currency, String source, Instant quoteTime, String freshnessLabel, boolean stale) {}

    /** Taxa oficial USD/BRL: reais por dólar, com a data de referência e a fonte. */
    public record FxRate(BigDecimal brlPerUsd, LocalDate date, String source, boolean stale) {
        public FxRate {
            if (brlPerUsd == null || brlPerUsd.signum() <= 0) throw new IllegalArgumentException("Câmbio inválido");
        }

        public BigDecimal toBrl(Currency from, BigDecimal amount) {
            return from == Currency.BRL ? amount.setScale(2, RoundingMode.HALF_EVEN) : amount.multiply(brlPerUsd).setScale(2, RoundingMode.HALF_EVEN);
        }
    }

    /**
     * @param unrealizedPnlBrl valor atual em reais (câmbio de hoje) menos o custo em reais (câmbio de cada compra): inclui a variação
     *     cambial, e só existe com cotação, PTAX de hoje e custo em reais registrado
     */
    public record Result(Position position, MarketPrice price, Basis basis, BigDecimal costBasis, BigDecimal marketValue, BigDecimal unrealizedPnl,
                         BigDecimal unrealizedPnlPercent, BigDecimal costBasisBrl, BigDecimal currentValueBrl, BigDecimal unrealizedPnlBrl,
                         BigDecimal unrealizedPnlPercentBrl) {}

    private PositionValuation() {}

    /**
     * @param price cotação na moeda da posição, ou nulo se não houver (ou se a moeda da cotação não for a da posição)
     * @param fx câmbio oficial, ou nulo se indisponível
     */
    public static Result value(Position p, MarketPrice price, FxRate fx) {
        MarketPrice usable = price != null && price.currency() == p.currency() ? price : null;
        // as contas partem dos valores exatos e arredondam uma única vez, na moeda de destino (arredondar em dólar e converter erra centavos)
        BigDecimal exactCost = p.exactCost();
        BigDecimal exactMarket = usable == null ? null : p.quantity().multiply(usable.price());
        BigDecimal cost = exactCost.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal market = exactMarket == null ? null : exactMarket.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal pnl = market == null ? null : market.subtract(cost);
        BigDecimal pct = pnl == null || cost.signum() == 0 ? null : Position.percentOf(pnl, cost);
        // custo em reais: ao câmbio de cada compra (guardado na posição), nunca ao câmbio de hoje
        BigDecimal exactCostBrl = p.exactCostBrl();
        BigDecimal costBrl = exactCostBrl == null ? null : exactCostBrl.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal exactCurrentBrl = brlExact(p.currency(), exactMarket != null ? exactMarket : exactCost, fx);
        BigDecimal currentBrl = exactCurrentBrl == null ? null : exactCurrentBrl.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal pnlBrl = null;
        BigDecimal pctBrl = null;
        if (exactMarket != null && exactCurrentBrl != null && exactCostBrl != null) {
            pnlBrl = exactCurrentBrl.subtract(exactCostBrl).setScale(2, RoundingMode.HALF_EVEN);
            pctBrl = exactCostBrl.signum() == 0 ? null : exactCurrentBrl.subtract(exactCostBrl).multiply(java.math.BigDecimal.valueOf(100)).divide(exactCostBrl, 2, RoundingMode.HALF_EVEN);
        }
        return new Result(p, usable, usable == null ? Basis.COST : Basis.MARKET, cost, market, pnl, pct, costBrl, currentBrl, pnlBrl, pctBrl);
    }

    /** Valor exato em reais (sem arredondar): quem chama arredonda uma só vez. */
    private static BigDecimal brlExact(Currency c, BigDecimal amount, FxRate fx) {
        if (c == Currency.BRL) return amount;
        return fx == null ? null : amount.multiply(fx.brlPerUsd());
    }
}
