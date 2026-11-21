package dev.meridianwealth.infrastructure.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.domain.reports.IssuerKind;
import dev.meridianwealth.domain.reports.IssuerReports;
import dev.meridianwealth.domain.reports.Report;
import dev.meridianwealth.domain.reports.ReportAnalyzer;
import dev.meridianwealth.domain.reports.ReportMetric;
import dev.meridianwealth.domain.reports.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Invariantes e vereditos dos relatórios REAIS da CVM que vão para produção. */
class JsonReportsRepositoryTest {
    private final JsonReportsRepository repo = new JsonReportsRepository();

    private Report report(String symbol, String label) {
        return repo.find(symbol).orElseThrow().reports().stream().filter(r -> r.label().equals(label)).findFirst().orElseThrow();
    }

    @Test void loadsTheSevenCuratedIssuers() {
        assertThat(repo.all()).extracting(IssuerReports::symbol)
                .containsExactly("HGLG11", "ITUB4", "KNRI11", "MXRF11", "PETR4", "VALE3", "XPML11");
        assertThat(repo.find("AAPL")).isEmpty();
    }

    @Test void everyIssuerCitesAnOfficialHttpsSourceWithFileHashes() {
        for (IssuerReports i : repo.all()) {
            assertThat(i.sources()).isNotEmpty().allSatisfy(s -> {
                assertThat(s.url()).startsWith("https://dados.cvm.gov.br/");
                assertThat(s.kind().name()).isEqualTo("OFFICIAL");
                assertThat(s.fileSha256()).isNotEmpty().allSatisfy((file, sha) -> assertThat(sha).hasSize(64));
            });
        }
    }

    @Test void firstDeliveryNeverComesAfterTheDeliveryOfTheVersionRead() {
        for (IssuerReports i : repo.all()) {
            assertThat(i.reports()).allSatisfy(r -> assertThat(r.deliveredOn()).isBeforeOrEqualTo(r.versionDeliveredOn()));
        }
        // o 1T2026 da Petrobras foi reentregue (versão 2) no mesmo dia da primeira entrega
        assertThat(report("PETR4", "1T2026").version()).isEqualTo(2);
    }

    @Test void companiesHaveSevenReportsEachWithBothMetricsAndABase() {
        for (String s : List.of("PETR4", "VALE3", "ITUB4")) {
            IssuerReports i = repo.find(s).orElseThrow();
            assertThat(i.kind()).isEqualTo(IssuerKind.COMPANY);
            assertThat(i.reports()).hasSize(7);
            for (Report r : i.reports()) {
                assertThat(r.link()).startsWith("https://www.rad.cvm.gov.br/");
                assertThat(r.metrics()).extracting(ReportMetric::code).containsExactly("RECEITA", "RESULTADO_LIQUIDO");
                assertThat(r.metrics()).allSatisfy(m -> assertThat(m.base()).isNotNull());
                assertThat(ReportAnalyzer.assess(r).verdict()).isNotNull();
            }
        }
    }

    @Test void realVerdictsFollowTheRulesWithTheirNumbers() {
        // Petrobras 3T2026: receita +32,40% e resultado +21,12% sobre o 3T2025
        var petr = ReportAnalyzer.assess(report("PETR4", "3T2026"));
        assertThat(petr.verdict()).isEqualTo(Verdict.POSITIVE);
        assertThat(petr.signals().get(0).variationPercent()).isEqualByComparingTo("32.40");
        assertThat(petr.signals().get(1).variationPercent()).isEqualByComparingTo("21.12");
        // Vale 3T2026: receita -3,95% (neutra), resultado -55,45%
        var vale = ReportAnalyzer.assess(report("VALE3", "3T2026"));
        assertThat(vale.verdict()).isEqualTo(Verdict.NEGATIVE);
        assertThat(vale.signals().get(0).signal()).isZero();
        assertThat(vale.signals().get(1).variationPercent()).isEqualByComparingTo("-55.45");
        // Itaú 3T2026 (banco: receita de intermediação e conta 3.09)
        assertThat(ReportAnalyzer.assess(report("ITUB4", "3T2026")).verdict()).isEqualTo(Verdict.POSITIVE);
        // Petrobras 1T2026: receita estável (+0,44%) e resultado -7,27%
        assertThat(ReportAnalyzer.assess(report("PETR4", "1T2026")).verdict()).isEqualTo(Verdict.NEGATIVE);
    }

    @Test void petrobras2t2025TurnedAroundFromALossAndSaysSo() {
        var a = ReportAnalyzer.assess(report("PETR4", "2T2025"));
        assertThat(a.signals().get(1).metric().base()).isNegative();
        assertThat(a.verdict()).isEqualTo(Verdict.POSITIVE);
    }

    @Test void fiiMonthsAreMonthEndsDeliveredAfterTheMonthAndTheNewestEightHaveVerdicts() {
        for (String s : List.of("HGLG11", "KNRI11", "XPML11", "MXRF11")) {
            IssuerReports i = repo.find(s).orElseThrow();
            assertThat(i.kind()).isEqualTo(IssuerKind.FII);
            assertThat(i.fiscalYearEnd()).matches("\\d{2}/\\d{2}");
            for (Report r : i.reports()) {
                assertThat(r.periodEnd().plusDays(1).getDayOfMonth()).as(r.label()).isEqualTo(1);
                assertThat(r.deliveredOn()).isAfter(r.periodEnd());
            }
            assertThat(i.reports().subList(0, 8)).allSatisfy(r -> assertThat(ReportAnalyzer.assess(r).verdict()).isNotNull());
        }
        assertThat(ReportAnalyzer.assess(report("KNRI11", "out/2026")).verdict()).isEqualTo(Verdict.NEUTRAL);
    }

    @Test void duplicatesAndBrokenFilesFailLoudlyWithTheirName() throws Exception {
        IssuerReports one = repo.find("PETR4").orElseThrow();
        assertThatThrownBy(() -> new JsonReportsRepository(List.of(one, one))).hasMessageContaining("repetido");
        var bad = new ObjectMapper().readTree("{\"versaoDoEsquema\":1,\"ativo\":\"X\",\"tipo\":\"EMPRESA\",\"fontes\":[]}");
        assertThatThrownBy(() -> JsonReportsRepository.parse(bad, "x.json")).hasMessageContaining("x.json").hasMessageContaining("Sem fonte");
    }

    @Test void reportsWhoseOriginalDeliveryIsMissingFromTheSourceAreFlagged() {
        // XPML11: a base só traz a versão 2 (reentrega de 15/10/2026) para jun a ago de 2026
        assertThat(report("XPML11", "jun/2026").originalInSource()).isFalse();
        assertThat(report("XPML11", "ago/2026").originalInSource()).isFalse();
        assertThat(report("XPML11", "out/2026").originalInSource()).isTrue();
        assertThat(report("PETR4", "3T2026").originalInSource()).isTrue();
    }
}
