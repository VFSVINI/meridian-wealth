package dev.meridianwealth.application;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MacroSnapshot;
import dev.meridianwealth.domain.thesis.GeoTranslator;
import dev.meridianwealth.domain.thesis.Holding;
import dev.meridianwealth.domain.thesis.ThesisReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Caso de uso: ler a carteira pelo tradutor geopolítico e pelas teses genéricas. Resolve cada posição (símbolo do universo,
 * que traz o setor, ou só o tipo, sem setor) e usa o último radar macro real; sem macro, nada é estimado. Nenhuma ordem é
 * enviada e a carteira não é gravada.
 */
public class ThesisService {
    /** Posição como o usuário informou: símbolo do universo ou tipo, e o valor em reais. */
    public record Item(String symbol, AssetType type, BigDecimal valueBrl) {}

    /** Valor macro bruto, com a fonte do radar; nulo quando nenhum provedor respondeu (vira "não informado"). */
    public record MacroContext(boolean available, String source, Double vix, Double dollarIndex, Double us10yYield, String headline) {
        static MacroContext of(MacroSnapshot m) {
            return new MacroContext(m.available(), m.source(), m.vix(), m.dollarIndex(), m.yield10y(), m.headline());
        }
    }

    public record Lens(GeoTranslator.Translation geopolitics, List<ThesisReader.Reading> readings, MacroContext macro) {}

    public static final int MAX_POSITIONS = 50;

    private final MarketService market;

    public ThesisService(MarketService market) { this.market = market; }

    public Lens lens(List<Item> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Informe ao menos uma posição");
        if (items.size() > MAX_POSITIONS) throw new IllegalArgumentException("No máximo " + MAX_POSITIONS + " posições");
        Map<String, Instrument> universe = new HashMap<>();
        market.universe().forEach(i -> universe.put(i.symbol(), i));
        List<Holding> holdings = items.stream().map(i -> resolve(i, universe)).toList();
        MacroSnapshot macro = market.macro();
        return new Lens(GeoTranslator.translate(holdings, macro), ThesisReader.readAll(holdings), MacroContext.of(macro));
    }

    private static Holding resolve(Item item, Map<String, Instrument> universe) {
        if (item == null) throw new IllegalArgumentException("Posição inválida");
        if (item.symbol() != null && !item.symbol().isBlank()) {
            String symbol = item.symbol().trim().toUpperCase(Locale.ROOT);
            Instrument inst = universe.get(symbol);
            if (inst == null) throw new IllegalArgumentException("Ativo fora do universo: " + symbol);
            if (item.type() != null && item.type() != inst.type()) throw new IllegalArgumentException("O tipo informado não confere com " + symbol);
            return new Holding(symbol + " · " + inst.name(), inst.type(), inst.sector(), item.valueBrl());
        }
        if (item.type() == null) throw new IllegalArgumentException("Informe o símbolo ou o tipo do ativo");
        return new Holding(item.type().label(), item.type(), null, item.valueBrl());
    }
}
