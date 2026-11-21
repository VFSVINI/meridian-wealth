package dev.meridianwealth.domain.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Monta os relatórios mensais de um FII a partir do Informe Mensal. A base do dividend yield é a média dos 3 meses
 * anteriores e a do valor patrimonial da cota é o mês anterior; sem esses meses na série, a base fica ausente.
 */
public final class FiiReportFactory {
    /** Um mês do Informe Mensal como publicado ({@code null} onde a fonte não traz). */
    public record Month(String id, LocalDate periodEnd, LocalDate firstDeliveredOn, LocalDate versionDeliveredOn, int version,
                        BigDecimal dividendYield, BigDecimal navPerShare) {
        public Month {
            Objects.requireNonNull(periodEnd, "periodEnd");
            Objects.requireNonNull(firstDeliveredOn, "firstDeliveredOn");
            Objects.requireNonNull(versionDeliveredOn, "versionDeliveredOn");
        }
    }

    private static final String DY_BASE = "média dos 3 meses anteriores";
    private static final String NAV_BASE = "mês anterior";
    private static final String[] MONTHS = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    private FiiReportFactory() {}

    public static List<Report> build(List<Month> months) {
        List<Month> sorted = months.stream().sorted(Comparator.comparing(Month::periodEnd)).toList();
        List<Report> out = new ArrayList<>();
        for (Month m : sorted) {
            List<ReportMetric> metrics = new ArrayList<>();
            if (m.dividendYield() != null) {
                metrics.add(new ReportMetric("DIVIDEND_YIELD", "Dividend yield do mês", "Percentual_Dividend_Yield_Mes",
                        "valor publicado", m.dividendYield(), label(m), averageOfPrevious(sorted, m), "3 meses anteriores", DY_BASE));
            }
            if (m.navPerShare() != null) {
                Month prev = previous(sorted, m, 1);
                metrics.add(new ReportMetric("VALOR_PATRIMONIAL_COTA", "Valor patrimonial da cota", "Valor_Patrimonial_Cotas", "BRL",
                        m.navPerShare(), label(m), prev == null ? null : prev.navPerShare(), prev == null ? null : label(prev), NAV_BASE));
            }
            out.add(new Report(m.id() == null ? "INF_MENSAL-" + m.periodEnd() : m.id(), ReportPeriodicity.MONTHLY, label(m),
                    m.periodEnd(), m.firstDeliveredOn(), m.versionDeliveredOn(), m.version(), null, metrics));
        }
        return out;
    }

    private static String label(Month m) {
        YearMonth ym = YearMonth.from(m.periodEnd());
        return MONTHS[ym.getMonthValue() - 1] + "/" + ym.getYear();
    }

    private static Month previous(List<Month> sorted, Month m, int back) {
        YearMonth target = YearMonth.from(m.periodEnd()).minusMonths(back);
        return sorted.stream().filter(x -> YearMonth.from(x.periodEnd()).equals(target)).findFirst().orElse(null);
    }

    private static BigDecimal averageOfPrevious(List<Month> sorted, Month m) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i <= 3; i++) {
            Month p = previous(sorted, m, i);
            if (p == null || p.dividendYield() == null) return null;
            sum = sum.add(p.dividendYield());
        }
        return sum.divide(BigDecimal.valueOf(3), 12, RoundingMode.HALF_EVEN);
    }
}
