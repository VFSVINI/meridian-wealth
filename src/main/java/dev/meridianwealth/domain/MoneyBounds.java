package dev.meridianwealth.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Limites de magnitude para valores em reais que vêm de fora. Notação científica como {@code 1E5000000} faria qualquer soma
 * alinhar milhões de dígitos (negação de serviço), então o tamanho é checado <b>antes</b> de qualquer conta, sobre o valor sem
 * zeros à direita (custo O(1) mesmo com expoente absurdo).
 */
public final class MoneyBounds {
    /** Até R$ 999 trilhões. */
    public static final int MAX_INTEGER_DIGITS = 15;
    public static final int MAX_DECIMAL_PLACES = 6;

    private MoneyBounds() {}

    /** Como {@link #requirePositive(BigDecimal, String, int)}, mas aceita zero (por exemplo, taxas e corretagem). */
    public static BigDecimal requireNonNegative(BigDecimal value, String label, int maxDecimals) {
        Objects.requireNonNull(value, label);
        if (value.signum() < 0) throw new IllegalArgumentException("O valor de " + label + " não pode ser negativo");
        if (value.signum() == 0) return BigDecimal.ZERO.setScale(0);
        return requirePositive(value, label, maxDecimals);
    }

    /** Valida (positivo e dentro dos limites) e devolve o valor com escala não negativa. */
    public static BigDecimal requirePositive(BigDecimal value, String label) { return requirePositive(value, label, MAX_DECIMAL_PLACES); }

    /** Como {@link #requirePositive(BigDecimal, String)}, com um limite de casas decimais próprio (por exemplo, 8 para quantidades de cripto). */
    public static BigDecimal requirePositive(BigDecimal value, String label, int maxDecimals) {
        Objects.requireNonNull(value, label);
        if (value.signum() <= 0) throw new IllegalArgumentException("O valor de " + label + " deve ser positivo");
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > maxDecimals || (long) stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("O valor de " + label + " está fora do intervalo aceito (até " + MAX_INTEGER_DIGITS
                    + " dígitos inteiros e " + maxDecimals + " casas decimais)");
        }
        return value.scale() < 0 ? value.setScale(0) : value;
    }
}
