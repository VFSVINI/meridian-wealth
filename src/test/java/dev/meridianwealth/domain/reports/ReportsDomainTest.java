package dev.meridianwealth.domain.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.composition.SourceKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportsDomainTest {
    private static final LocalDate D = LocalDate.of(2026, 11, 10);

    private static ReportMetric metric(String code, String current, String base) {
        return new ReportMetric(code, code, code, "BRL", new BigDecimal(current), "atual", base == null ? null : new BigDecimal(base),
                "base", "mesmo trimestre do ano anterior");
    }

    private static Report report(ReportMetric... metrics) {
        return new Report("r", ReportPeriodicity.QUARTERLY, "3T2026", LocalDate.of(2026, 9, 30), D, D, 1, null, List.of(metrics));
    }

    private static Verdict verdict(ReportMetric... metrics) { return ReportAnalyzer.assess(report(metrics)).verdict(); }

    @Test void variationIsRoundedOnceHalfEvenAndUsesAbsoluteBase() {
        assertThat(ReportAnalyzer.variationPercent(new BigDecimal("105"), new BigDecimal("100"))).isEqualByComparingTo("5.00");
        assertThat(ReportAnalyzer.variationPercent(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("-66.67");
        // base negativa: sair de -100 para -50 é melhora de 50%
        assertThat(ReportAnalyzer.variationPercent(new BigDecimal("-50"), new BigDecimal("-100"))).isEqualByComparingTo("50.00");
        // arredonda uma vez, HALF_EVEN: 12,345 vira 12,34 (par) e 12,355 vira 12,36
        assertThat(ReportAnalyzer.variationPercent(new BigDecimal("1.12345"), BigDecimal.ONE)).isEqualByComparingTo("12.34");
        assertThat(ReportAnalyzer.variationPercent(new BigDecimal("1.12355"), BigDecimal.ONE)).isEqualByComparingTo("12.36");
    }

    @Test void thresholdBoundaryIsInclusive() {
        assertThat(verdict(metric("RECEITA", "105", "100"))).isEqualTo(Verdict.POSITIVE);
        assertThat(verdict(metric("RECEITA", "104.99", "100"))).isEqualTo(Verdict.NEUTRAL);
        assertThat(verdict(metric("RECEITA", "95", "100"))).isEqualTo(Verdict.NEGATIVE);
        assertThat(verdict(metric("RECEITA", "95.01", "100"))).isEqualTo(Verdict.NEUTRAL);
    }

    @Test void mixedSignalsAreNeutral() {
        assertThat(verdict(metric("RECEITA", "120", "100"), metric("RESULTADO_LIQUIDO", "80", "100"))).isEqualTo(Verdict.NEUTRAL);
    }

    @Test void bothPositiveOrBothNegative() {
        assertThat(verdict(metric("RECEITA", "120", "100"), metric("RESULTADO_LIQUIDO", "130", "100"))).isEqualTo(Verdict.POSITIVE);
        assertThat(verdict(metric("RECEITA", "80", "100"), metric("RESULTADO_LIQUIDO", "50", "100"))).isEqualTo(Verdict.NEGATIVE);
    }

    @Test void oneComputedSignalDecidesAndMissingBaseIsCaveated() {
        ReportAssessment a = ReportAnalyzer.assess(report(metric("RECEITA", "120", "100"), metric("RESULTADO_LIQUIDO", "10", null)));
        assertThat(a.verdict()).isEqualTo(Verdict.POSITIVE);
        assertThat(a.caveats()).anyMatch(c -> c.contains("sem sinal por falta de base"));
        assertThat(a.signals().get(1).computed()).isFalse();
    }

    @Test void noBaseAtAllMeansNoVerdictInsteadOfAGuess() {
        ReportAssessment a = ReportAnalyzer.assess(report(metric("RECEITA", "120", null), metric("RESULTADO_LIQUIDO", "10", "0")));
        assertThat(a.verdict()).isNull();
        assertThat(a.caveats()).anyMatch(c -> c.contains("não há veredito"));
        assertThat(ReportAnalyzer.assess(report()).verdict()).isNull();
    }

    @Test void netLossCanNeverBePositive() {
        // prejuízo caiu de -100 para -10 (melhora de 90%): sinal positivo, mas o veredito fica NEUTRO
        ReportAssessment a = ReportAnalyzer.assess(report(metric("RECEITA", "120", "100"), metric("RESULTADO_LIQUIDO", "-10", "-100")));
        assertThat(a.verdict()).isEqualTo(Verdict.NEUTRAL);
        assertThat(a.caveats()).anyMatch(c -> c.contains("prejuízo"));
    }

    @Test void turnaroundFromLossToProfitIsPositive() {
        assertThat(verdict(metric("RESULTADO_LIQUIDO", "26774", "-2515"))).isEqualTo(Verdict.POSITIVE);
    }

    @Test void everySignalCarriesNumbersBaseAndThreshold() {
        MetricSignal s = ReportAnalyzer.assess(report(metric("RECEITA", "169353000000", "127906000000"))).signals().get(0);
        assertThat(s.variationPercent()).isEqualByComparingTo("32.40");
        assertThat(s.thresholdPercent()).isEqualByComparingTo("5");
        assertThat(s.explanation()).contains("R$ 169.353.000.000,00").contains("R$ 127.906.000.000,00").contains("+32,40%")
                .contains("±5%").contains("positivo");
    }

    @Test void unknownMetricFailsLoudly() {
        assertThatThrownBy(() -> ReportAnalyzer.assess(report(metric("OUTRA", "1", "1")))).hasMessageContaining("sem limiar");
    }

    private static FiiReportFactory.Month month(int m, String dy, String nav) {
        LocalDate end = LocalDate.of(2026, m, 1).plusMonths(1).minusDays(1);
        return new FiiReportFactory.Month("M" + m, end, end.plusDays(10), end.plusDays(10), 1, dy == null ? null : new BigDecimal(dy),
                nav == null ? null : new BigDecimal(nav));
    }

    @Test void fiiUsesAverageOfThreePreviousMonthsAndPreviousMonthNav() {
        List<Report> r = FiiReportFactory.build(List.of(month(1, "0.0060", "100"), month(2, "0.0066", "100"), month(3, "0.0063", "100"),
                month(4, "0.0070", "98")));
        Report april = r.get(3);
        assertThat(april.label()).isEqualTo("abr/2026");
        ReportMetric dy = april.metrics().get(0);
        assertThat(dy.base()).isEqualByComparingTo("0.0063");  // (0.0060 + 0.0066 + 0.0063) / 3
        ReportAssessment a = ReportAnalyzer.assess(april);
        // dy: +11,11% (positivo); VP: -2% (negativo) => misto => NEUTRO
        assertThat(a.signals().get(0).signal()).isEqualTo(1);
        assertThat(a.signals().get(1).signal()).isEqualTo(-1);
        assertThat(a.verdict()).isEqualTo(Verdict.NEUTRAL);
    }

    @Test void fiiWithoutEnoughHistoryHasNoBaseInsteadOfAnEstimate() {
        List<Report> r = FiiReportFactory.build(List.of(month(1, "0.0060", "100"), month(3, "0.0070", "100")));
        assertThat(r.get(1).metrics().get(0).base()).isNull();  // março não tem 3 meses anteriores
        assertThat(r.get(1).metrics().get(1).base()).isNull();  // fevereiro está ausente
        assertThat(ReportAnalyzer.assess(r.get(1)).verdict()).isNull();
    }

    private static IssuerReports company(List<Report> reports) {
        return new IssuerReports("PETR4", "Petrobras", "1", IssuerKind.COMPANY,
                List.of(new ReportSource("CVM", "https://dados.cvm.gov.br", SourceKind.OFFICIAL, D, Map.of())), reports, null, List.of());
    }

    @Test void companyCalendarUsesTheLegalDeadlinesAndDeliveryDates() {
        LocalDate today = LocalDate.of(2026, 11, 21);
        Report q3 = new Report("q3", ReportPeriodicity.QUARTERLY, "3T2026", LocalDate.of(2026, 9, 30), LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 10), 1, null, List.of());
        Report late = new Report("q2", ReportPeriodicity.QUARTERLY, "2T2026", LocalDate.of(2026, 6, 30), LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20), 1, null, List.of());
        List<DeliveryCalendar.Entry> cal = DeliveryCalendar.of(company(List.of(q3, late)), today);
        assertThat(cal).extracting(DeliveryCalendar.Entry::label).containsExactly("Anual 2026 (inclui o 4T)", "3T2026", "2T2026", "1T2026",
                "Anual 2025 (inclui o 4T)");
        DeliveryCalendar.Entry next = cal.get(0);
        assertThat(next.deadline()).isEqualTo(LocalDate.of(2027, 3, 31));  // 3 meses após 31/12
        assertThat(next.status()).isEqualTo(DeliveryCalendar.Status.PENDING);
        assertThat(cal.get(1).deadline()).isEqualTo(LocalDate.of(2026, 11, 14));  // 30/09 + 45 dias
        assertThat(cal.get(1).status()).isEqualTo(DeliveryCalendar.Status.DELIVERED_ON_TIME);
        assertThat(cal.get(2).deadline()).isEqualTo(LocalDate.of(2026, 8, 14));  // 30/06 + 45 dias
        assertThat(cal.get(2).status()).isEqualTo(DeliveryCalendar.Status.DELIVERED_LATE);
        assertThat(cal.get(3).status()).isEqualTo(DeliveryCalendar.Status.NO_RECORD);  // prazo vencido, sem registro na base
        assertThat(DeliveryCalendar.nextDeadline(cal, today).orElseThrow().label()).isEqualTo("Anual 2026 (inclui o 4T)");
    }

    @Test void quarterEndsOnTheLastDayOfAQuarterCountsThatQuarterAsClosed() {
        assertThat(DeliveryCalendar.quarterEnds(LocalDate.of(2026, 9, 30), 1)).containsExactly(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 9, 30));
        assertThat(DeliveryCalendar.quarterEnds(LocalDate.of(2026, 10, 1), 1)).containsExactly(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 9, 30));
        assertThat(DeliveryCalendar.quarterEnds(LocalDate.of(2026, 1, 15), 1)).containsExactly(LocalDate.of(2026, 3, 31), LocalDate.of(2025, 12, 31));
    }

    @Test void fiiCalendarHasMonthlyQuarterlyAndAnnualRulesAndMarksUntrackedOnes() {
        LocalDate today = LocalDate.of(2026, 11, 21);
        Report oct = new Report("o", ReportPeriodicity.MONTHLY, "out/2026", LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 13), 1, null, List.of());
        IssuerReports fii = new IssuerReports("KNRI11", "Kinea", "1", IssuerKind.FII, List.of(), List.of(oct), "30/06", List.of());
        List<DeliveryCalendar.Entry> cal = DeliveryCalendar.of(fii, today);
        DeliveryCalendar.Entry october = cal.stream().filter(e -> e.label().equals("10/2026")).findFirst().orElseThrow();
        assertThat(october.deadline()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(october.status()).isEqualTo(DeliveryCalendar.Status.DELIVERED_ON_TIME);
        assertThat(cal.stream().filter(e -> e.label().equals("11/2026")).findFirst().orElseThrow().status())
                .isEqualTo(DeliveryCalendar.Status.PENDING);
        DeliveryCalendar.Entry q3 = cal.stream().filter(e -> e.label().equals("3T2026")).findFirst().orElseThrow();
        assertThat(q3.deadline()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(q3.status()).isEqualTo(DeliveryCalendar.Status.NOT_TRACKED);
        DeliveryCalendar.Entry annual = cal.stream().filter(e -> e.periodicity() == ReportPeriodicity.ANNUAL && e.periodEnd().getYear() == 2026)
                .findFirst().orElseThrow();
        assertThat(annual.periodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(annual.deadline()).isEqualTo(LocalDate.of(2026, 9, 28));  // 30/06 + 90 dias
    }

    @Test void malformedFiscalYearEndSkipsTheAnnualEntryInsteadOfFailing() {
        assertThat(DeliveryCalendar.fiscalYearEnds("dezembro", LocalDate.of(2026, 11, 21))).isEmpty();
        assertThat(DeliveryCalendar.fiscalYearEnds(null, LocalDate.of(2026, 11, 21))).isEmpty();
        assertThat(DeliveryCalendar.fiscalYearEnds("31/12", LocalDate.of(2026, 11, 21))).containsExactly(LocalDate.of(2026, 12, 31), LocalDate.of(2025, 12, 31));
    }

    @Test void reportRejectsInsecureLinkAndSourceRejectsNonHttps() {
        assertThatThrownBy(() -> new Report("x", ReportPeriodicity.ANNUAL, "a", D, D, D, 1, "http://x", List.of())).hasMessageContaining("https");
        assertThatThrownBy(() -> new ReportSource("n", "http://x", SourceKind.OFFICIAL, D, Map.of())).hasMessageContaining("https");
    }

    @Test void curatedDataGoesStaleAfterTheCvmCadencePlusDeadline() {
        LocalDate consulted = LocalDate.of(2026, 11, 21);
        IssuerReports i = company(List.of());
        assertThat(i.age(consulted.plusDays(0)).stale()).isFalse();
        // as fontes de company() foram consultadas em D (2026-11-10)
        assertThat(i.age(D.plusDays(45)).stale()).isFalse();
        assertThat(i.age(D.plusDays(46)).stale()).isTrue();
        assertThat(i.age(D.plusDays(46)).days()).isEqualTo(46);
        assertThat(i.age(D.minusDays(3)).days()).isZero();  // relógio antes da consulta nunca dá idade negativa
    }
}
