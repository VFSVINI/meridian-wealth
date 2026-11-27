package dev.meridianwealth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.BrazilHistoryPort;
import dev.meridianwealth.domain.HistorySeries;
import dev.meridianwealth.domain.RateHistory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Página Brasil (item #50): mudanças da Selic, juro real, cache e último dado bom, tudo sobre o que o Banco Central publicou. */
class BrazilHistoryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    /** Relógio que a gente avança. */
    static final class MutableClock extends Clock {
        Instant now = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(12 * 3600);

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    /** Porta em memória: devolve o que o teste configurar e conta as consultas. */
    static final class FakePort implements BrazilHistoryPort {
        final Map<HistorySeries, List<RateHistory.Point>> data = new EnumMap<>(HistorySeries.class);
        final AtomicInteger calls = new AtomicInteger();
        boolean down;
        final Clock clock;

        FakePort(Clock clock) { this.clock = clock; }

        @Override public Optional<RateHistory> history(HistorySeries s, LocalDate from, LocalDate to) {
            calls.incrementAndGet();
            if (down || !data.containsKey(s)) return Optional.empty();
            return Optional.of(new RateHistory(s, data.get(s), clock.instant()));
        }
    }

    private final MutableClock clock = new MutableClock();
    private final FakePort port = new FakePort(clock);
    private final BrazilHistoryService service = new BrazilHistoryService(port, clock);

    private static RateHistory.Point p(String date, String v) { return new RateHistory.Point(LocalDate.parse(date), new BigDecimal(v)); }

    /** A sequência real da meta Selic nos últimos 24 meses (mudanças confirmadas na API do Banco Central em 2026-11-27): um ponto por dia de mudança e um no meio. */
    private void realSelic() {
        String[][] steps = {{"2024-11-27", "11.25"}, {"2024-12-12", "12.25"}, {"2025-01-30", "13.25"}, {"2025-03-20", "14.25"}, {"2025-05-08", "14.75"}, {"2025-06-19", "15.00"},
            {"2026-03-19", "14.75"}, {"2026-04-30", "14.50"}, {"2026-06-18", "14.25"}, {"2026-08-06", "14.00"}, {"2026-09-17", "13.75"}};
        List<RateHistory.Point> pts = new ArrayList<>();
        for (int i = 0; i < steps.length; i++) {
            LocalDate start = LocalDate.parse(steps[i][0]);
            pts.add(p(steps[i][0], steps[i][1]));
            LocalDate next = i + 1 < steps.length ? LocalDate.parse(steps[i + 1][0]) : TODAY.plusDays(1);
            LocalDate mid = start.plusDays(Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, next) / 2));
            if (mid.isBefore(next) && mid.isAfter(start)) pts.add(new RateHistory.Point(mid, new BigDecimal(steps[i][1])));
        }
        port.data.put(HistorySeries.SELIC_TARGET, pts);
    }

    @Test void selicChangesAreTheRealOnesMostRecentFirstWithDirection() {
        realSelic();
        var d = service.view().decisions();
        assertThat(d).hasSize(10);
        assertThat(d.get(0)).isEqualTo(new BrazilHistoryService.Decision(LocalDate.parse("2026-09-17"), new BigDecimal("14.00"), new BigDecimal("13.75")));
        assertThat(d.get(0).direction()).isEqualTo("QUEDA");
        assertThat(d.get(d.size() - 1).direction()).isEqualTo("ALTA");
        assertThat(d.get(d.size() - 1).from()).isEqualByComparingTo("11.25");
    }

    @Test void realRateIsNominalOverInflationNotASimpleSubtraction() {
        assertThat(BrazilHistoryService.real(new BigDecimal("10.00"), new BigDecimal("4.00"))).isEqualByComparingTo("5.77");
        assertThat(BrazilHistoryService.real(new BigDecimal("13.75"), new BigDecimal("4.63"))).isEqualByComparingTo("8.72");
        assertThat(BrazilHistoryService.real(new BigDecimal("4.63"), new BigDecimal("4.63"))).isEqualByComparingTo("0.00");
        assertThat(BrazilHistoryService.real(new BigDecimal("3.00"), new BigDecimal("4.00"))).as("juro real negativo").isNegative();
    }

    private void ratesFor(String selic, String cdi, String ipca) {
        port.data.put(HistorySeries.SELIC_TARGET, List.of(p("2026-11-01", selic), p("2026-11-26", selic)));
        port.data.put(HistorySeries.CDI_ANNUAL, List.of(p("2026-11-25", cdi)));
        port.data.put(HistorySeries.IPCA_12M, List.of(p("2026-10-01", ipca)));
    }

    @Test void comparisonUsesTheLegalSavingsRuleAndTheRealPublishedRates() {
        ratesFor("13.75", "13.65", "4.63");
        var c = service.view().comparison();
        assertThat(c).extracting(BrazilHistoryService.Comparison::key).containsExactly("savings", "treasurySelic", "cdb100", "ipca");
        assertThat(c.get(0).nominal()).as("0,5% ao mês composto: 1,005^12 - 1").isEqualByComparingTo("6.17");
        assertThat(c.get(0).rule()).contains("0,5% ao mês").contains("TR");
        assertThat(c.get(1).nominal()).isEqualByComparingTo("13.75");
        assertThat(c.get(1).real()).isEqualByComparingTo("8.72");
        assertThat(c.get(2).nominal()).isEqualByComparingTo("13.65");
        assertThat(c.get(3).real()).isEqualByComparingTo("0.00");
    }

    @Test void savingsPaysSeventyPercentOfSelicWhenSelicIsAtOrBelowEightPointFive() {
        ratesFor("8.00", "7.90", "4.00");
        assertThat(service.view().comparison().get(0).nominal()).isEqualByComparingTo("5.60");
        ratesFor("8.50", "8.40", "4.00");
        clock.now = clock.now.plusSeconds(31 * 60);
        assertThat(service.view().comparison().get(0).nominal()).as("no limite de 8,5% ainda vale 70% da Selic").isEqualByComparingTo("5.95");
    }

    @Test void withoutInflationThereIsNoComparisonAndTheMissingSeriesAreListedNeverEstimated() {
        port.data.put(HistorySeries.SELIC_TARGET, List.of(p("2026-11-26", "13.75")));
        var v = service.view();
        assertThat(v.comparison()).isEmpty();
        assertThat(v.unavailable()).containsExactlyInAnyOrder(HistorySeries.CDI_ANNUAL, HistorySeries.IPCA_12M, HistorySeries.IPCA_MONTHLY, HistorySeries.PTAX_SELL);
        assertThat(v.series()).containsOnlyKeys(HistorySeries.SELIC_TARGET);
    }

    @Test void historyIsCachedForThirtyMinutesAndTheLastGoodDataSurvivesAnOutage() {
        ratesFor("13.75", "13.65", "4.63");
        service.view();
        int first = port.calls.get();
        service.view();
        assertThat(port.calls.get()).as("dentro do prazo não consulta de novo").isEqualTo(first);
        clock.now = clock.now.plusSeconds(31 * 60);
        port.down = true;
        var v = service.view();
        assertThat(port.calls.get()).isGreaterThan(first);
        assertThat(v.series()).as("Banco Central fora do ar: vale o último dado bom").containsKeys(HistorySeries.SELIC_TARGET, HistorySeries.IPCA_12M);
        assertThat(v.series().get(HistorySeries.SELIC_TARGET).stale()).as("com 31 minutos ainda não é considerado velho").isFalse();
        clock.now = clock.now.plusSeconds(40 * 60);
        assertThat(service.view().series().get(HistorySeries.SELIC_TARGET).stale()).as("passou de duas vezes o prazo sem dado novo").isTrue();
    }

    @Test void aHistoryNeverExistsEmptyOrOutOfOrder() {
        assertThatThrownBy(() -> new RateHistory(HistorySeries.SELIC_TARGET, List.of(), clock.instant())).hasMessageContaining("nada é inventado");
        assertThatThrownBy(() -> new RateHistory(HistorySeries.SELIC_TARGET, List.of(p("2026-11-02", "1"), p("2026-11-01", "1")), clock.instant())).hasMessageContaining("fora de ordem");
    }

    @Test void aSeriesThatIsDownIsNotQueriedAgainOnEveryVisitOnlyAfterTheBackoff() {
        // medido: a série que falhou (aqui o dólar e o IPCA mensal) era consultada de novo a cada visita à página
        ratesFor("13.75", "13.65", "4.63");
        service.view();
        int afterFirst = port.calls.get();               // 5 séries consultadas, 2 sem dado
        service.view();
        service.view();
        assertThat(port.calls.get()).as("duas visitas seguidas não consultam ninguém").isEqualTo(afterFirst);
        clock.now = clock.now.plusSeconds(3 * 60);
        service.view();
        assertThat(port.calls.get()).as("passada a espera, só as duas séries que faltam são tentadas").isEqualTo(afterFirst + 2);
    }
}
