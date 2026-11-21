package dev.meridianwealth.domain.impact;

import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.MoneyBounds;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import dev.meridianwealth.domain.suitability.ModelPortfolio;
import dev.meridianwealth.domain.suitability.PortfolioAdherence;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simulador de impacto pré-ordem. Mostra o efeito de UMA compra ou venda hipotética na concentração, no risco e na
 * aderência ao perfil, sobre valores em reais informados pelo usuário. É uma <b>simulação</b>: não usa cotação, não gera
 * ordem (a plataforma não executa ordens) e não inclui custos, impostos nem preço de execução. Não diz o que fazer: só
 * compara a carteira antes e depois.
 *
 * <p>Concentração: maior posição, HHI (soma dos quadrados dos pesos, 0 a 10.000) e posições efetivas (10.000 / HHI).
 * Risco: média das volatilidades anualizadas das posições COM dado real, ponderada pelo peso (limite superior, pois ignora
 * correlação), junto da cobertura. Aderência: a mesma de {@link PortfolioAdherence}. Percentuais saem com 2 casas
 * (HALF_EVEN) e a volatilidade com 4.
 */
public final class PreOrderImpact {
    public enum Side { BUY, SELL }

    /** Posição da carteira ({@code symbol} é nulo quando só se conhece o tipo) com a volatilidade anualizada, se houver. */
    public record Position(String symbol, String label, AssetType type, BigDecimal valueBrl, BigDecimal annualVolatility) {
        public Position {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(label, "label");
            if (!type.investable()) throw new IllegalArgumentException(type.label() + " é só referência e não entra em carteira");
            valueBrl = MoneyBounds.requirePositive(valueBrl, label);
            if (annualVolatility != null && annualVolatility.signum() < 0) throw new IllegalArgumentException("Volatilidade negativa");
        }

        String key() { return symbol != null ? symbol : "TIPO:" + type.name(); }
    }

    public record Order(Side side, Position target) {
        public Order {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(target, "target");
        }
    }

    public record Snapshot(boolean empty, BigDecimal totalBrl, int positions, String largestLabel, BigDecimal largestPercent, BigDecimal hhi,
                           BigDecimal effectivePositions, Map<AssetCategory, BigDecimal> classPercents, BigDecimal weightedVolatility,
                           BigDecimal volatilityCoverage) {
        public Snapshot {
            classPercents = Map.copyOf(classPercents);
        }
    }

    public enum Level { INFO, ATTENTION }

    public record Finding(Level level, String text) {}

    public record Result(Side side, String orderLabel, BigDecimal orderAmountBrl, Snapshot before, Snapshot after,
                         PortfolioAdherence.Result adherenceBefore, PortfolioAdherence.Result adherenceAfter, List<Finding> findings) {
        public Result {
            findings = List.copyOf(findings);
        }
    }

    /** Acima disso a maior posição gera aviso: limite de atenção do produto, não regra de mercado. */
    public static final BigDecimal LARGEST_POSITION_ATTENTION = new BigDecimal("25");
    public static final int MAX_POSITIONS = 50;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

    private PreOrderImpact() {}

    /** @param profile opcional: sem ele, a aderência não é calculada */
    public static Result analyze(List<Position> positions, Order order, InvestorProfile profile) {
        Objects.requireNonNull(order, "order");
        if (positions == null) throw new IllegalArgumentException("Carteira ausente");
        if (positions.size() > MAX_POSITIONS) throw new IllegalArgumentException("No máximo " + MAX_POSITIONS + " posições");
        Map<String, Position> before = merge(positions);
        Map<String, Position> after = apply(before, order);
        Snapshot b = snapshot(before.values());
        Snapshot a = snapshot(after.values());
        PortfolioAdherence.Result adBefore = profile == null || before.isEmpty() ? null : adherence(profile, before.values());
        PortfolioAdherence.Result adAfter = profile == null || after.isEmpty() ? null : adherence(profile, after.values());
        return new Result(order.side(), order.target().label(), order.target().valueBrl(), b, a, adBefore, adAfter,
                findings(order, b, a, adBefore, adAfter, profile));
    }

    private static Map<String, Position> merge(List<Position> positions) {
        Map<String, Position> m = new LinkedHashMap<>();
        for (Position p : positions) {
            if (p == null) throw new IllegalArgumentException("Posição inválida");
            m.merge(p.key(), p, (x, y) -> {
                if (x.type() != y.type()) throw new IllegalArgumentException("Tipos diferentes para " + x.label());
                return new Position(x.symbol(), x.label(), x.type(), x.valueBrl().add(y.valueBrl()),
                        x.annualVolatility() != null ? x.annualVolatility() : y.annualVolatility());
            });
        }
        return m;
    }

    private static Map<String, Position> apply(Map<String, Position> before, Order order) {
        Map<String, Position> after = new LinkedHashMap<>(before);
        Position t = order.target();
        Position held = before.get(t.key());
        if (order.side() == Side.BUY) {
            Position merged = held == null ? t : new Position(held.symbol(), held.label(), held.type(), held.valueBrl().add(t.valueBrl()),
                    held.annualVolatility() != null ? held.annualVolatility() : t.annualVolatility());
            after.put(t.key(), merged);
            return after;
        }
        if (held == null) throw new IllegalArgumentException("Você não tem posição em " + t.label() + " para vender");
        int cmp = t.valueBrl().compareTo(held.valueBrl());
        if (cmp > 0) {
            throw new IllegalArgumentException("O valor da venda (R$ " + t.valueBrl().toPlainString() + ") passa do valor da posição em "
                    + held.label() + " (R$ " + held.valueBrl().toPlainString() + ")");
        }
        if (cmp == 0) after.remove(t.key());
        else after.put(t.key(), new Position(held.symbol(), held.label(), held.type(), held.valueBrl().subtract(t.valueBrl()), held.annualVolatility()));
        return after;
    }

    private static PortfolioAdherence.Result adherence(InvestorProfile profile, Iterable<Position> positions) {
        List<PortfolioAdherence.Holding> holdings = new ArrayList<>();
        positions.forEach(p -> holdings.add(new PortfolioAdherence.Holding(p.type(), p.valueBrl())));
        return PortfolioAdherence.evaluate(profile, holdings);
    }

    static Snapshot snapshot(Iterable<Position> positions) {
        List<Position> list = new ArrayList<>();
        positions.forEach(list::add);
        Map<AssetCategory, BigDecimal> classes = new EnumMap<>(AssetCategory.class);
        ModelPortfolio.CLASSES.forEach(c -> classes.put(c, BigDecimal.ZERO.setScale(2)));
        if (list.isEmpty()) return new Snapshot(true, BigDecimal.ZERO, 0, null, null, null, null, classes, null, null);
        BigDecimal total = list.stream().map(Position::valueBrl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumSquares = BigDecimal.ZERO;
        BigDecimal coveredWeight = BigDecimal.ZERO;
        BigDecimal volWeighted = BigDecimal.ZERO;
        Map<AssetCategory, BigDecimal> classValue = new EnumMap<>(AssetCategory.class);
        for (Position p : list) {
            BigDecimal w = p.valueBrl().divide(total, MathContext.DECIMAL128);
            sumSquares = sumSquares.add(w.multiply(w, MathContext.DECIMAL128));
            if (p.annualVolatility() != null) {
                coveredWeight = coveredWeight.add(w);
                volWeighted = volWeighted.add(w.multiply(p.annualVolatility(), MathContext.DECIMAL128));
            }
            classValue.merge(p.type().category(), p.valueBrl(), BigDecimal::add);
        }
        classValue.forEach((c, v) -> classes.put(c, v.multiply(HUNDRED, MathContext.DECIMAL128).divide(total, 2, RoundingMode.HALF_EVEN)));
        Position largest = list.stream().max(Comparator.comparing(Position::valueBrl)).orElseThrow();
        BigDecimal wVol = coveredWeight.signum() == 0 ? null : volWeighted.divide(coveredWeight, 4, RoundingMode.HALF_EVEN);
        return new Snapshot(false, total, list.size(), largest.label(),
                largest.valueBrl().multiply(HUNDRED, MathContext.DECIMAL128).divide(total, 2, RoundingMode.HALF_EVEN),
                sumSquares.multiply(TEN_THOUSAND, MathContext.DECIMAL128).setScale(2, RoundingMode.HALF_EVEN),
                BigDecimal.ONE.divide(sumSquares, 2, RoundingMode.HALF_EVEN), classes, wVol,
                coveredWeight.multiply(HUNDRED, MathContext.DECIMAL128).setScale(2, RoundingMode.HALF_EVEN));
    }

    private static List<Finding> findings(Order order, Snapshot b, Snapshot a, PortfolioAdherence.Result adB, PortfolioAdherence.Result adA,
                                          InvestorProfile profile) {
        List<Finding> out = new ArrayList<>();
        if (a.empty()) {
            out.add(new Finding(Level.INFO, "Depois da venda a carteira ficaria vazia."));
            return out;
        }
        if (a.largestPercent().compareTo(LARGEST_POSITION_ATTENTION) > 0) {
            String was = b.empty() ? "" : " (antes: " + pct(b.largestPercent()) + ")";
            out.add(new Finding(Level.ATTENTION, "A maior posição (" + a.largestLabel() + ") ficaria com " + pct(a.largestPercent())
                    + " da carteira" + was + ", acima do limite de atenção do produto de " + pct(LARGEST_POSITION_ATTENTION) + "."));
        } else if (!b.empty() && a.largestPercent().compareTo(b.largestPercent()) != 0) {
            out.add(new Finding(Level.INFO, "A maior posição passa de " + pct(b.largestPercent()) + " para " + pct(a.largestPercent()) + "."));
        }
        if (!b.empty()) {
            int diversification = a.effectivePositions().compareTo(b.effectivePositions());
            if (diversification < 0) {
                out.add(new Finding(Level.INFO, "A carteira fica mais concentrada: posições efetivas de " + num(b.effectivePositions())
                        + " para " + num(a.effectivePositions()) + "."));
            } else if (diversification > 0) {
                out.add(new Finding(Level.INFO, "A carteira fica mais diversificada: posições efetivas de " + num(b.effectivePositions())
                        + " para " + num(a.effectivePositions()) + "."));
            }
            if (a.weightedVolatility() != null && b.weightedVolatility() != null && a.weightedVolatility().compareTo(b.weightedVolatility()) != 0) {
                out.add(new Finding(Level.INFO, "A volatilidade média ponderada (limite superior, sem correlação) passa de " + pct4(b.weightedVolatility())
                        + " para " + pct4(a.weightedVolatility()) + " ao ano."));
            }
        }
        if (a.volatilityCoverage().compareTo(HUNDRED) < 0) {
            out.add(new Finding(Level.INFO, "O risco considera só " + pct(a.volatilityCoverage()) + " da carteira: as demais posições não têm volatilidade com dado real."));
        }
        if (profile != null && adA != null) {
            if (adB != null) {
                int cmp = adA.score().compareTo(adB.score());
                if (cmp < 0) {
                    out.add(new Finding(Level.ATTENTION, "A aderência ao perfil " + profile.label() + " cai de " + num(adB.score()) + " para "
                            + num(adA.score()) + "."));
                } else if (cmp > 0) {
                    out.add(new Finding(Level.INFO, "A aderência ao perfil " + profile.label() + " sobe de " + num(adB.score()) + " para "
                            + num(adA.score()) + "."));
                }
            }
            for (PortfolioAdherence.ClassDeviation c : adA.classes()) {
                boolean hadBefore = adB != null && adB.classes().stream().anyMatch(x -> x.category() == c.category() && x.currentPercent().signum() > 0);
                if (c.targetPercent().signum() == 0 && c.currentPercent().signum() > 0 && !hadBefore) {
                    out.add(new Finding(Level.ATTENTION, "A meta do perfil " + profile.label() + " para " + c.category().label() + " é 0%, e a carteira passaria a ter "
                            + pct(c.currentPercent()) + " nessa classe."));
                }
            }
        }
        return out;
    }

    /** Número no formato brasileiro (vírgula decimal), com 2 casas. */
    private static String num(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ','); }

    private static String pct(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',') + "%"; }

    private static String pct4(BigDecimal fraction) {
        return fraction.multiply(HUNDRED).setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',') + "%";
    }
}
