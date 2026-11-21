package dev.meridianwealth.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Taxa em percentual (ex.: {@code 13.75} é 13,75%), sempre em {@link BigDecimal} (ADR 002). A escala vem
 * do que a fonte publicou; conversões usam {@link MathContext#DECIMAL128} e o arredondamento só acontece
 * na apresentação, com {@link #rounded(int)} (HALF_EVEN).
 */
public record Rate(BigDecimal percent) {
    /** Limite de {@link BigDecimal#pow(int, MathContext)}: acima disso a potência lança ArithmeticException. */
    public static final int MAX_PERIODS = 999_999_999;

    public Rate {
        Objects.requireNonNull(percent, "percent");
    }

    public static Rate ofPercent(String percent) { return new Rate(new BigDecimal(percent)); }

    /** A taxa como fração decimal (13,75% vira 0,1375). */
    public BigDecimal fraction() { return percent.movePointLeft(2); }

    /**
     * Taxa acumulada depois de aplicar esta taxa por {@code periods} períodos, em juros compostos:
     * {@code (1 + i)^n - 1}. Com a taxa diária do CDI e 252 períodos dá o CDI anualizado (base 252).
     */
    public Rate compoundedOver(int periods) {
        if (periods < 0 || periods > MAX_PERIODS) {
            throw new IllegalArgumentException("periods deve estar entre 0 e " + MAX_PERIODS);
        }
        BigDecimal factor = BigDecimal.ONE.add(fraction()).pow(periods, MathContext.DECIMAL128);
        return new Rate(factor.subtract(BigDecimal.ONE).movePointRight(2));
    }

    /**
     * Percentual desta taxa: {@code 110} sobre uma taxa de 0,05% a.d. dá 0,055% a.d. (o "110% do CDI"
     * aplica-se à taxa do período, não à taxa anual).
     */
    public Rate percentOf(BigDecimal factorPercent) {
        return new Rate(percent.multiply(factorPercent.movePointLeft(2), MathContext.DECIMAL128));
    }

    /** Arredonda para a apresentação (HALF_EVEN, o arredondamento bancário do ADR 002). */
    public Rate rounded(int scale) { return new Rate(percent.setScale(scale, RoundingMode.HALF_EVEN)); }

    public int compareTo(Rate other) { return percent.compareTo(other.percent); }
}
