package dev.meridianwealth.domain.suitability;

import dev.meridianwealth.domain.AssetCategory;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Carteira modelo por perfil: meta percentual por classe de ativo ({@link AssetCategory}, sem a classe de referência,
 * que não se compra). As metas são <b>ilustrativas e definidas pelo produto</b>, sem fonte externa: servem de régua
 * para medir desvio, não de recomendação. A soma é 100 em cada perfil e é verificada na carga da classe.
 */
public final class ModelPortfolio {
    /** Classes que entram na carteira modelo, na ordem de exibição. */
    public static final List<AssetCategory> CLASSES = List.of(AssetCategory.RENDA_FIXA_PUBLICA, AssetCategory.RENDA_FIXA_PRIVADA,
            AssetCategory.FUNDOS, AssetCategory.RENDA_VARIAVEL, AssetCategory.CRIPTO, AssetCategory.CAMBIO_E_COMMODITIES);

    private static final Map<InvestorProfile, Map<AssetCategory, BigDecimal>> TARGETS = new EnumMap<>(InvestorProfile.class);

    static {
        define(InvestorProfile.CONSERVATIVE, 50, 30, 10, 5, 0, 5);
        define(InvestorProfile.MODERATE, 30, 25, 15, 25, 0, 5);
        define(InvestorProfile.BOLD, 15, 15, 15, 45, 5, 5);
        define(InvestorProfile.AGGRESSIVE, 5, 10, 10, 60, 10, 5);
        for (InvestorProfile p : InvestorProfile.values()) {
            BigDecimal sum = TARGETS.get(p).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.valueOf(100)) != 0) throw new IllegalStateException("Metas de " + p + " somam " + sum);
        }
    }

    private ModelPortfolio() {}

    private static void define(InvestorProfile profile, int... percents) {
        if (percents.length != CLASSES.size()) throw new IllegalStateException("Metas incompletas para " + profile);
        Map<AssetCategory, BigDecimal> m = new EnumMap<>(AssetCategory.class);
        for (int i = 0; i < percents.length; i++) m.put(CLASSES.get(i), BigDecimal.valueOf(percents[i]));
        TARGETS.put(profile, Map.copyOf(m));
    }

    /** Meta (%) de cada classe para o perfil, na ordem de {@link #CLASSES}. */
    public static Map<AssetCategory, BigDecimal> targets(InvestorProfile profile) { return TARGETS.get(profile); }
}
