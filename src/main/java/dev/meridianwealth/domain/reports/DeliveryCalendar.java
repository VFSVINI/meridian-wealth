package dev.meridianwealth.domain.reports;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Calendário de prazos de divulgação, derivado das regras da CVM (nunca de datas adivinhadas):
 *
 * <ul>
 *   <li>companhia aberta: ITR em até 45 dias após o fim de cada trimestre (Resolução CVM 80, art. 31) e DFP em até 3 meses
 *       após o fim do exercício (art. 30), que traz o 4T;</li>
 *   <li>FII (Anexo Normativo III da Resolução CVM 175): informe mensal em até 15 dias após o mês, trimestral em até 45 dias
 *       após o trimestre e anual em até 90 dias após o exercício.</li>
 * </ul>
 *
 * O prazo é o máximo legal: a data real de divulgação pode ser anterior e só é conhecida depois da entrega, quando a
 * fonte a registra. Entregas que a fonte usada não acompanha ficam como {@link Status#NOT_TRACKED}.
 */
public final class DeliveryCalendar {
    public enum Status {
        DELIVERED_ON_TIME("Entregue no prazo"),
        DELIVERED_LATE("Entregue após o prazo"),
        PENDING("Prazo em aberto"),
        NO_RECORD("Sem registro na base consultada"),
        NOT_TRACKED("Entrega não acompanhada nesta fonte");

        private final String label;

        Status(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record Entry(ReportPeriodicity periodicity, String label, LocalDate periodEnd, LocalDate deadline, String rule,
                        Status status, LocalDate deliveredOn) {}

    private static final String RULE_ITR = "ITR: até 45 dias após o fim do trimestre (Resolução CVM 80, art. 31)";
    private static final String RULE_DFP = "DFP: até 3 meses após o fim do exercício (Resolução CVM 80, art. 30)";
    private static final String RULE_FII_MONTHLY = "Informe mensal: até 15 dias após o mês (Anexo Normativo III da Resolução CVM 175)";
    private static final String RULE_FII_QUARTERLY = "Informe trimestral: até 45 dias após o trimestre (Anexo Normativo III da Resolução CVM 175)";
    private static final String RULE_FII_ANNUAL = "Informe anual: até 90 dias após o exercício (Anexo Normativo III da Resolução CVM 175)";

    private DeliveryCalendar() {}

    /** Últimos 4 períodos fechados e o próximo, do mais novo para o mais antigo (por prazo). */
    public static List<Entry> of(IssuerReports issuer, LocalDate today) {
        List<Entry> out = new ArrayList<>();
        if (issuer.kind() == IssuerKind.COMPANY) {
            for (LocalDate end : quarterEnds(today, 4)) out.add(company(issuer, end, today));
        } else {
            for (LocalDate end : monthEnds(today, 4)) {
                out.add(entry(issuer, ReportPeriodicity.MONTHLY, monthLabel(end), end, end.plusDays(15), RULE_FII_MONTHLY, today, true));
            }
            for (LocalDate end : quarterEnds(today, 1)) {
                out.add(entry(issuer, ReportPeriodicity.QUARTERLY, quarterLabel(end), end, end.plusDays(45), RULE_FII_QUARTERLY, today, false));
            }
            fiscalYearEnds(issuer.fiscalYearEnd(), today).forEach(end -> out.add(entry(issuer, ReportPeriodicity.ANNUAL,
                    "Exercício " + end.getYear(), end, end.plusDays(90), RULE_FII_ANNUAL, today, false)));
        }
        out.sort(Comparator.comparing(Entry::deadline).reversed());
        return out;
    }

    /** O próximo prazo em aberto (o mais próximo de hoje), se houver. */
    public static Optional<Entry> nextDeadline(List<Entry> entries, LocalDate today) {
        return entries.stream().filter(e -> !e.deadline().isBefore(today) && e.status() != Status.DELIVERED_ON_TIME
                && e.status() != Status.DELIVERED_LATE).min(Comparator.comparing(Entry::deadline));
    }

    private static Entry company(IssuerReports issuer, LocalDate end, LocalDate today) {
        if (end.getMonthValue() == 12) {
            return entry(issuer, ReportPeriodicity.ANNUAL, "Anual " + end.getYear() + " (inclui o 4T)", end, end.plusMonths(3), RULE_DFP, today, true);
        }
        return entry(issuer, ReportPeriodicity.QUARTERLY, quarterLabel(end), end, end.plusDays(45), RULE_ITR, today, true);
    }

    private static Entry entry(IssuerReports issuer, ReportPeriodicity periodicity, String label, LocalDate end, LocalDate deadline,
                               String rule, LocalDate today, boolean tracked) {
        Optional<Report> delivered = tracked ? issuer.deliveredFor(periodicity, end) : Optional.empty();
        if (delivered.isPresent()) {
            LocalDate on = delivered.get().deliveredOn();
            Status s = on.isAfter(deadline) ? Status.DELIVERED_LATE : Status.DELIVERED_ON_TIME;
            return new Entry(periodicity, label, end, deadline, rule, s, on);
        }
        Status s = !deadline.isBefore(today) ? Status.PENDING : tracked ? Status.NO_RECORD : Status.NOT_TRACKED;
        return new Entry(periodicity, label, end, deadline, rule, s, null);
    }

    /** Os {@code closed} últimos fins de trimestre já encerrados (até hoje) e o próximo. */
    static List<LocalDate> quarterEnds(LocalDate today, int closed) {
        YearMonth ym = YearMonth.from(today);
        YearMonth quarter = YearMonth.of(ym.getYear(), ((ym.getMonthValue() - 1) / 3 + 1) * 3);
        LocalDate lastClosed = quarter.atEndOfMonth().isAfter(today) ? quarter.minusMonths(3).atEndOfMonth() : quarter.atEndOfMonth();
        List<LocalDate> out = new ArrayList<>();
        out.add(YearMonth.from(lastClosed).plusMonths(3).atEndOfMonth());
        for (int i = 0; i < closed; i++) out.add(YearMonth.from(lastClosed).minusMonths(3L * i).atEndOfMonth());
        return out;
    }

    static List<LocalDate> monthEnds(LocalDate today, int closed) {
        YearMonth ym = YearMonth.from(today);
        YearMonth last = today.equals(ym.atEndOfMonth()) ? ym : ym.minusMonths(1);
        List<LocalDate> out = new ArrayList<>();
        out.add(last.plusMonths(1).atEndOfMonth());
        for (int i = 0; i < closed; i++) out.add(last.minusMonths(i).atEndOfMonth());
        return out;
    }

    /** Último exercício encerrado e o próximo, a partir do "dd/MM" publicado; texto fora do formato não gera entrada. */
    static List<LocalDate> fiscalYearEnds(String ddMm, LocalDate today) {
        if (ddMm == null || !ddMm.matches("\\d{2}/\\d{2}")) return List.of();
        int day = Integer.parseInt(ddMm.substring(0, 2));
        int month = Integer.parseInt(ddMm.substring(3));
        if (month < 1 || month > 12) return List.of();
        LocalDate thisYear = YearMonth.of(today.getYear(), month).atDay(Math.min(day, YearMonth.of(today.getYear(), month).lengthOfMonth()));
        LocalDate lastClosed = thisYear.isAfter(today) ? thisYear.minusYears(1) : thisYear;
        return List.of(lastClosed.plusYears(1), lastClosed);
    }

    private static String quarterLabel(LocalDate end) { return ((end.getMonthValue() - 1) / 3 + 1) + "T" + end.getYear(); }

    private static String monthLabel(LocalDate end) { return String.format("%02d/%d", end.getMonthValue(), end.getYear()); }
}
