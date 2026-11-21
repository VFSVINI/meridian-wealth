package dev.meridianwealth.domain.suitability;

import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aderência de uma carteira ao perfil: peso atual por classe (%), desvio em pontos percentuais contra a meta e uma
 * nota de 0 a 100. Os pesos são arredondados uma vez (2 casas, HALF_EVEN) e o desvio sai dos valores já arredondados,
 * então o que a tela mostra fecha. Nota = 100 menos metade da soma dos desvios absolutos (distância de variação total:
 * 100 quando igual à meta, 0 quando totalmente disjunta). Faixa de tolerância de 5 pontos percentuais por classe.
 * Só descreve o desvio: não diz o que comprar ou vender.
 */
public final class PortfolioAdherence {
    public enum Status { WITHIN, ABOVE, BELOW }

    /** Máximo de dígitos inteiros do valor (R$ 999 trilhões) e de casas decimais aceitas. */
    public static final int MAX_INTEGER_DIGITS = 15;
    public static final int MAX_DECIMAL_PLACES = 6;

    public record Holding(AssetType type, BigDecimal valueBrl) {
        public Holding {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(valueBrl, "valueBrl");
            if (!type.investable()) throw new IllegalArgumentException(type.label() + " é só referência e não entra em carteira");
            if (valueBrl.signum() <= 0) throw new IllegalArgumentException("O valor de " + type.label() + " deve ser positivo");
            // A magnitude é checada ANTES de qualquer conta: notação científica como 1E5000000 faria a soma alinhar milhões de
            // dígitos (DoS). Sem zeros à direita a checagem custa O(1), mesmo com expoente absurdo.
            BigDecimal stripped = valueBrl.stripTrailingZeros();
            if (stripped.scale() > MAX_DECIMAL_PLACES || (long) stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
                throw new IllegalArgumentException("O valor de " + type.label() + " está fora do intervalo aceito (até "
                        + MAX_INTEGER_DIGITS + " dígitos inteiros e " + MAX_DECIMAL_PLACES + " casas decimais)");
            }
            valueBrl = valueBrl.scale() < 0 ? valueBrl.setScale(0) : valueBrl;
        }
    }

    public record ClassDeviation(AssetCategory category, BigDecimal currentPercent, BigDecimal targetPercent, BigDecimal deviationPoints,
                                 Status status, BigDecimal valueBrl) {}

    public record Result(InvestorProfile profile, BigDecimal totalBrl, BigDecimal score, List<ClassDeviation> classes, int classesOutsideBand,
                         BigDecimal bandPoints) {
        public Result {
            classes = List.copyOf(classes);
        }
    }

    public static final BigDecimal BAND_POINTS = new BigDecimal("5");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PortfolioAdherence() {}

    public static Result evaluate(InvestorProfile profile, List<Holding> holdings) {
        Objects.requireNonNull(profile, "profile");
        if (holdings == null || holdings.isEmpty()) throw new IllegalArgumentException("A carteira está vazia");
        Map<AssetCategory, BigDecimal> byClass = new EnumMap<>(AssetCategory.class);
        BigDecimal total = BigDecimal.ZERO;
        for (Holding h : holdings) {
            byClass.merge(h.type().category(), h.valueBrl(), BigDecimal::add);
            total = total.add(h.valueBrl());
        }
        Map<AssetCategory, BigDecimal> targets = ModelPortfolio.targets(profile);
        List<ClassDeviation> out = new ArrayList<>();
        BigDecimal absSum = BigDecimal.ZERO;
        int outside = 0;
        for (AssetCategory c : ModelPortfolio.CLASSES) {
            BigDecimal value = byClass.getOrDefault(c, BigDecimal.ZERO);
            BigDecimal current = value.multiply(HUNDRED, MathContext.DECIMAL128).divide(total, 2, RoundingMode.HALF_EVEN);
            BigDecimal target = targets.get(c);
            BigDecimal deviation = current.subtract(target);
            Status status = deviation.abs().compareTo(BAND_POINTS) <= 0 ? Status.WITHIN : deviation.signum() > 0 ? Status.ABOVE : Status.BELOW;
            if (status != Status.WITHIN) outside++;
            absSum = absSum.add(deviation.abs());
            out.add(new ClassDeviation(c, current, target, deviation, status, value));
        }
        BigDecimal score = HUNDRED.subtract(absSum.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_EVEN)).max(BigDecimal.ZERO);
        return new Result(profile, total, score, out, outside, BAND_POINTS);
    }
}
