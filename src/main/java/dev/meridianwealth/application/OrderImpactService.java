package dev.meridianwealth.application;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.impact.PreOrderImpact;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Caso de uso: simulação de impacto pré-ordem. Resolve cada posição (por símbolo do universo ou só pelo tipo) e busca a
 * volatilidade anualizada com dado real; sem dado, a posição entra sem volatilidade e a cobertura do risco diz isso. Nada
 * é estimado e nenhuma ordem é enviada.
 */
public class OrderImpactService {
    /** Posição ou ordem como o usuário informou: símbolo do universo ou tipo, e o valor em reais. */
    public record Item(String symbol, AssetType type, BigDecimal valueBrl) {}

    private final MarketService market;

    public OrderImpactService(MarketService market) { this.market = market; }

    public PreOrderImpact.Result analyze(List<Item> positions, PreOrderImpact.Side side, Item order, InvestorProfile profile) {
        if (positions == null) throw new IllegalArgumentException("Carteira ausente");
        if (positions.size() > PreOrderImpact.MAX_POSITIONS) {
            throw new IllegalArgumentException("No máximo " + PreOrderImpact.MAX_POSITIONS + " posições");
        }
        if (side == null || order == null) throw new IllegalArgumentException("Informe a ordem (compra ou venda) e o ativo");
        Map<String, Instrument> universe = new HashMap<>();
        market.universe().forEach(i -> universe.put(i.symbol(), i));
        Map<String, BigDecimal> vols = new HashMap<>();
        List<PreOrderImpact.Position> resolved = positions.stream().map(i -> resolve(i, universe, vols)).toList();
        return PreOrderImpact.analyze(resolved, new PreOrderImpact.Order(side, resolve(order, universe, vols)), profile);
    }

    private PreOrderImpact.Position resolve(Item item, Map<String, Instrument> universe, Map<String, BigDecimal> vols) {
        if (item == null) throw new IllegalArgumentException("Posição inválida");
        if (item.symbol() != null && !item.symbol().isBlank()) {
            String symbol = item.symbol().trim().toUpperCase(Locale.ROOT);
            Instrument inst = universe.get(symbol);
            if (inst == null) throw new IllegalArgumentException("Ativo fora do universo: " + symbol);
            AssetType type = inst.type();
            if (item.type() != null && item.type() != type) throw new IllegalArgumentException("O tipo informado não confere com " + symbol);
            return new PreOrderImpact.Position(symbol, symbol + " · " + inst.name(), type, item.valueBrl(),
                    vols.computeIfAbsent(symbol, this::volatility));
        }
        if (item.type() == null) throw new IllegalArgumentException("Informe o símbolo ou o tipo do ativo");
        return new PreOrderImpact.Position(null, item.type().label(), item.type(), item.valueBrl(), null);
    }

    /** Volatilidade anualizada do histórico real; nula quando não há dado suficiente (nunca estimada). */
    private BigDecimal volatility(String symbol) {
        try {
            double v = market.assessment(symbol).annVol();
            return Double.isFinite(v) && v >= 0 ? BigDecimal.valueOf(v) : null;
        } catch (DataUnavailableException | IllegalArgumentException e) {
            return null;
        }
    }
}
