package dev.meridianwealth.domain.suitability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.suitability.PortfolioAdherence.Holding;
import dev.meridianwealth.domain.suitability.Questionnaire.Option;
import dev.meridianwealth.domain.suitability.Questionnaire.Question;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SuitabilityDomainTest {
    /** Respostas com a opção de maior pontuação em tudo, trocando as perguntas indicadas pela de menor pontuação. */
    private static Map<String, String> answers(String... lowest) {
        Set<String> low = Set.of(lowest);
        Map<String, String> m = new HashMap<>();
        for (Question q : Questionnaire.QUESTIONS) {
            Option pick = low.contains(q.id()) ? q.options().get(0) : q.options().get(q.options().size() - 1);
            m.put(q.id(), pick.id());
        }
        return m;
    }

    private static Map<String, String> allLowest() {
        return answers(Questionnaire.QUESTIONS.stream().map(Question::id).toArray(String[]::new));
    }

    @Test void questionnaireCoversTheThreeLegalDimensionsWithConsistentOptions() {
        assertThat(Questionnaire.QUESTIONS).hasSize(9);
        assertThat(Questionnaire.QUESTIONS.stream().map(q -> q.dimension()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(Questionnaire.Dimension.values());
        for (Question q : Questionnaire.QUESTIONS) {
            assertThat(q.legalRef()).startsWith("Resolução CVM 30, art. 3");
            assertThat(q.options().stream().map(Option::id).distinct()).hasSameSizeAs(q.options());
            assertThat(q.options().get(0).points()).as(q.id() + " começa em 0").isZero();
            assertThat(q.maxPoints()).as(q.id()).isEqualTo(4);
            // pontos crescem com o risco aceito
            for (int i = 1; i < q.options().size(); i++) assertThat(q.options().get(i).points()).isGreaterThan(q.options().get(i - 1).points());
        }
        assertThat(Questionnaire.maxTotalPoints()).isEqualTo(36);
    }

    @Test void everyCapPointsToARealQuestionAndOption() {
        for (Questionnaire.Cap cap : Questionnaire.CAPS) {
            Question q = Questionnaire.question(cap.questionId()).orElseThrow();
            assertThat(q.options()).extracting(Option::id).contains(cap.optionId());
            assertThat(cap.maxProfile()).isIn(InvestorProfile.CONSERVATIVE, InvestorProfile.MODERATE);
            assertThat(cap.reason()).isNotBlank();
        }
    }

    @Test void modelPortfoliosSumToOneHundredAndUseOnlyTradableClasses() {
        assertThat(ModelPortfolio.CLASSES).hasSize(6).doesNotContain(AssetCategory.REFERENCIA);
        for (InvestorProfile p : InvestorProfile.values()) {
            Map<AssetCategory, BigDecimal> t = ModelPortfolio.targets(p);
            assertThat(t.keySet()).containsExactlyInAnyOrderElementsOf(ModelPortfolio.CLASSES);
            assertThat(t.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).as(p.label()).isEqualByComparingTo("100");
            assertThat(t.values()).allSatisfy(v -> assertThat(v.signum()).isGreaterThanOrEqualTo(0));
        }
    }

    @Test void riskGrowsMonotonicallyWithTheProfile() {
        BigDecimal previous = BigDecimal.valueOf(-1);
        for (InvestorProfile p : InvestorProfile.values()) {
            BigDecimal risky = ModelPortfolio.targets(p).get(AssetCategory.RENDA_VARIAVEL).add(ModelPortfolio.targets(p).get(AssetCategory.CRIPTO));
            assertThat(risky).isGreaterThan(previous);
            previous = risky;
        }
        assertThat(ModelPortfolio.targets(InvestorProfile.CONSERVATIVE).get(AssetCategory.CRIPTO)).isEqualByComparingTo("0");
    }

    @Test void extremeAnswersGiveTheExtremeScores() {
        SuitabilityEngine.Result max = SuitabilityEngine.evaluate(answers());
        assertThat(max.score()).isEqualByComparingTo("100.00");
        assertThat(max.profile()).isEqualTo(InvestorProfile.AGGRESSIVE);
        assertThat(max.appliedCaps()).isEmpty();
        SuitabilityEngine.Result min = SuitabilityEngine.evaluate(allLowest());
        assertThat(min.score()).isEqualByComparingTo("0");
        assertThat(min.profile()).isEqualTo(InvestorProfile.CONSERVATIVE);
        assertThat(min.answers()).hasSize(9).allSatisfy(a -> assertThat(a.points()).isZero());
    }

    @Test void scoreBandsAreHalfOpenAtTheDocumentedLimits() {
        assertThat(SuitabilityEngine.profileFor(new BigDecimal("29.99"))).isEqualTo(InvestorProfile.CONSERVATIVE);
        assertThat(SuitabilityEngine.profileFor(new BigDecimal("30"))).isEqualTo(InvestorProfile.MODERATE);
        assertThat(SuitabilityEngine.profileFor(new BigDecimal("54.99"))).isEqualTo(InvestorProfile.MODERATE);
        assertThat(SuitabilityEngine.profileFor(new BigDecimal("55"))).isEqualTo(InvestorProfile.BOLD);
        assertThat(SuitabilityEngine.profileFor(new BigDecimal("79.99"))).isEqualTo(InvestorProfile.BOLD);
        assertThat(SuitabilityEngine.profileFor(new BigDecimal("80"))).isEqualTo(InvestorProfile.AGGRESSIVE);
    }

    @Test void nearBoundaryPointTotalsLandOnTheExpectedSideWithTwoDecimals() {
        // 36 pontos = 100%: 10 -> 27,78 (conservador); 11 -> 30,56 (moderado); 19 -> 52,78; 20 -> 55,56 (arrojado); 28 -> 77,78; 29 -> 80,56
        assertThat(withTotal(10).score()).isEqualByComparingTo("27.78");
        assertThat(withTotal(10).profile()).isEqualTo(InvestorProfile.CONSERVATIVE);
        assertThat(withTotal(11).profile()).isEqualTo(InvestorProfile.MODERATE);
        assertThat(withTotal(19).profile()).isEqualTo(InvestorProfile.MODERATE);
        assertThat(withTotal(20).score()).isEqualByComparingTo("55.56");
        assertThat(withTotal(20).profile()).isEqualTo(InvestorProfile.BOLD);
        assertThat(withTotal(28).profile()).isEqualTo(InvestorProfile.BOLD);
        assertThat(withTotal(29).profile()).isEqualTo(InvestorProfile.AGGRESSIVE);
    }

    /** Busca respostas que somam exatamente {@code total} pontos sem acionar nenhum teto (para isolar a faixa da nota). */
    private static SuitabilityEngine.Result withTotal(int total) {
        Map<String, String> found = search(0, total, new HashMap<>());
        assertThat(found).as("total " + total + " alcançável sem teto").isNotNull();
        return SuitabilityEngine.evaluate(found);
    }

    private static Map<String, String> search(int index, int remaining, Map<String, String> chosen) {
        if (index == Questionnaire.QUESTIONS.size()) return remaining == 0 ? new HashMap<>(chosen) : null;
        Question q = Questionnaire.QUESTIONS.get(index);
        for (Option o : q.options()) {
            boolean triggersCap = Questionnaire.CAPS.stream().anyMatch(c -> c.questionId().equals(q.id()) && c.optionId().equals(o.id()));
            if (triggersCap || o.points() > remaining) continue;
            chosen.put(q.id(), o.id());
            Map<String, String> r = search(index + 1, remaining - o.points(), chosen);
            if (r != null) return r;
        }
        chosen.remove(q.id());
        return null;
    }

    @Test void aShortHorizonCapsAnOtherwiseAggressiveProfileAndSaysWhy() {
        SuitabilityEngine.Result r = SuitabilityEngine.evaluate(answers("HORIZON"));
        assertThat(r.score()).isEqualByComparingTo("88.89");
        assertThat(r.scoreProfile()).isEqualTo(InvestorProfile.AGGRESSIVE);
        assertThat(r.profile()).isEqualTo(InvestorProfile.CONSERVATIVE);
        assertThat(r.appliedCaps()).hasSize(1).first().asString().contains("Horizonte de até 1 ano").contains("Teto: CONSERVADOR");
    }

    @Test void theLowestCapWinsAndEachAppliedCapIsListed() {
        SuitabilityEngine.Result r = SuitabilityEngine.evaluate(answers("EMERGENCY_RESERVE", "LIQUIDITY_NEED"));
        assertThat(r.profile()).isEqualTo(InvestorProfile.CONSERVATIVE);
        assertThat(r.appliedCaps()).hasSize(2);
        SuitabilityEngine.Result moderate = SuitabilityEngine.evaluate(answers("EMERGENCY_RESERVE"));
        assertThat(moderate.profile()).isEqualTo(InvestorProfile.MODERATE);
        assertThat(SuitabilityEngine.evaluate(answers("DRAWDOWN")).profile()).isEqualTo(InvestorProfile.MODERATE);
    }

    @Test void aCapAboveTheScoreProfileIsNotReportedAsApplied() {
        // nota baixa já dá conservador: teto de conservador não precisa ser citado
        Map<String, String> m = allLowest();
        SuitabilityEngine.Result r = SuitabilityEngine.evaluate(m);
        assertThat(r.profile()).isEqualTo(InvestorProfile.CONSERVATIVE);
        assertThat(r.appliedCaps()).isEmpty();
    }

    @Test void missingUnknownOrInvalidAnswersAreErrorsNeverGuesses() {
        Map<String, String> missing = answers();
        missing.remove("INCOME");
        assertThatThrownBy(() -> SuitabilityEngine.evaluate(missing)).hasMessageContaining("sem resposta").hasMessageContaining("INCOME");
        Map<String, String> unknown = answers();
        unknown.remove("INCOME");  // mantém 9 chaves para chegar à checagem de pergunta desconhecida
        unknown.put("EXTRA", "X");
        assertThatThrownBy(() -> SuitabilityEngine.evaluate(unknown)).hasMessageContaining("desconhecida");
        Map<String, String> invalid = answers();
        invalid.put("HORIZON", "FOREVER");
        assertThatThrownBy(() -> SuitabilityEngine.evaluate(invalid)).hasMessageContaining("Opção inválida");
        assertThatThrownBy(() -> SuitabilityEngine.evaluate(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Holding h(AssetType t, String v) { return new Holding(t, new BigDecimal(v)); }

    @Test void aPortfolioExactlyOnTargetScoresOneHundred() {
        PortfolioAdherence.Result r = PortfolioAdherence.evaluate(InvestorProfile.MODERATE, List.of(h(AssetType.TESOURO_SELIC, "300"),
                h(AssetType.LCI, "250"), h(AssetType.FUNDO_MULTIMERCADO, "150"), h(AssetType.ACAO, "250"), h(AssetType.COMMODITY, "50")));
        assertThat(r.score()).isEqualByComparingTo("100");
        assertThat(r.classesOutsideBand()).isZero();
        assertThat(r.classes()).hasSize(6).allSatisfy(c -> assertThat(c.deviationPoints()).isEqualByComparingTo("0"));
    }

    @Test void everythingInOneClassIsFarFromTheTargetAndDeviationsAreExact() {
        PortfolioAdherence.Result r = PortfolioAdherence.evaluate(InvestorProfile.CONSERVATIVE, List.of(h(AssetType.ACAO, "1000")));
        PortfolioAdherence.ClassDeviation equity = r.classes().stream().filter(c -> c.category() == AssetCategory.RENDA_VARIAVEL).findFirst().orElseThrow();
        assertThat(equity.currentPercent()).isEqualByComparingTo("100");
        assertThat(equity.deviationPoints()).isEqualByComparingTo("95");  // 100 - meta 5
        assertThat(equity.status()).isEqualTo(PortfolioAdherence.Status.ABOVE);
        // soma dos desvios absolutos = 95 + 50 + 30 + 10 + 0 + 5 = 190; nota = 100 - 95 = 5
        assertThat(r.score()).isEqualByComparingTo("5");
        assertThat(r.classesOutsideBand()).isEqualTo(4);  // câmbio, a 5 pontos da meta, fica dentro da faixa
        assertThat(r.classes().stream().filter(c -> c.category() == AssetCategory.RENDA_FIXA_PUBLICA).findFirst().orElseThrow().status())
                .isEqualTo(PortfolioAdherence.Status.BELOW);
    }

    @Test void holdingsOfDifferentTypesAreMergedIntoTheirClass() {
        PortfolioAdherence.Result r = PortfolioAdherence.evaluate(InvestorProfile.BOLD, List.of(h(AssetType.ACAO, "300"), h(AssetType.FII, "150"),
                h(AssetType.ETF, "0.01"), h(AssetType.LCI, "100"), h(AssetType.CRI, "50")));
        BigDecimal equity = r.classes().stream().filter(c -> c.category() == AssetCategory.RENDA_VARIAVEL).findFirst().orElseThrow().valueBrl();
        BigDecimal credit = r.classes().stream().filter(c -> c.category() == AssetCategory.RENDA_FIXA_PRIVADA).findFirst().orElseThrow().valueBrl();
        assertThat(equity).isEqualByComparingTo("450.01");
        assertThat(credit).isEqualByComparingTo("150");
        assertThat(r.totalBrl()).isEqualByComparingTo("600.01");
    }

    @Test void theToleranceBandIsInclusiveAtFivePoints() {
        // conservador: renda variável meta 5; 10% = desvio de exatamente 5 pontos -> dentro; 10,01% -> acima
        PortfolioAdherence.Result inside = PortfolioAdherence.evaluate(InvestorProfile.CONSERVATIVE, List.of(h(AssetType.ACAO, "10"),
                h(AssetType.TESOURO_SELIC, "50"), h(AssetType.LCI, "30"), h(AssetType.FUNDO_RENDA_FIXA, "10")));
        assertThat(inside.classes().stream().filter(c -> c.category() == AssetCategory.RENDA_VARIAVEL).findFirst().orElseThrow().status())
                .isEqualTo(PortfolioAdherence.Status.WITHIN);
        PortfolioAdherence.Result outside = PortfolioAdherence.evaluate(InvestorProfile.CONSERVATIVE, List.of(h(AssetType.ACAO, "1001"),
                h(AssetType.TESOURO_SELIC, "5000"), h(AssetType.LCI, "3000"), h(AssetType.FUNDO_RENDA_FIXA, "999")));
        var equity = outside.classes().stream().filter(c -> c.category() == AssetCategory.RENDA_VARIAVEL).findFirst().orElseThrow();
        assertThat(equity.currentPercent()).isEqualByComparingTo("10.01");
        assertThat(equity.status()).isEqualTo(PortfolioAdherence.Status.ABOVE);
    }

    @Test void holdingInAClassWhoseTargetIsZeroIsAboveTarget() {
        PortfolioAdherence.Result r = PortfolioAdherence.evaluate(InvestorProfile.CONSERVATIVE, List.of(h(AssetType.CRIPTO_ATIVO, "100"), h(AssetType.TESOURO_SELIC, "900")));
        var crypto = r.classes().stream().filter(c -> c.category() == AssetCategory.CRIPTO).findFirst().orElseThrow();
        assertThat(crypto.targetPercent()).isEqualByComparingTo("0");
        assertThat(crypto.status()).isEqualTo(PortfolioAdherence.Status.ABOVE);
    }

    @Test void invalidPortfoliosAreRejected() {
        assertThatThrownBy(() -> PortfolioAdherence.evaluate(InvestorProfile.MODERATE, List.of())).hasMessageContaining("vazia");
        assertThatThrownBy(() -> h(AssetType.INDICE, "100")).hasMessageContaining("referência");
        assertThatThrownBy(() -> h(AssetType.ACAO, "0")).hasMessageContaining("positivo");
        assertThatThrownBy(() -> h(AssetType.ACAO, "-5")).hasMessageContaining("positivo");
    }

    @Test void astronomicalExponentsAreRejectedInstantlyInsteadOfFreezingTheSum() {
        // medido antes da correção: 1E5000000 levou 7,6 s, 1E100000000 passou de 90 s e 1E999999999 deu 500
        for (String v : List.of("1E5000000", "1E100000000", "1E999999999", "1E16", "1E-7", "0.0000001")) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> h(AssetType.ACAO, v)).as(v).hasMessageContaining("fora do intervalo");
            assertThat((System.nanoTime() - start) / 1_000_000).as(v + " em ms").isLessThan(500);
        }
    }

    @Test void saneScientificAndPlainValuesAreAcceptedAndNormalized() {
        assertThat(h(AssetType.ACAO, "1E3").valueBrl()).isEqualByComparingTo("1000");
        assertThat(h(AssetType.ACAO, "1E3").valueBrl().scale()).isGreaterThanOrEqualTo(0);
        assertThat(h(AssetType.ACAO, "999999999999999").valueBrl()).isEqualByComparingTo("999999999999999");
        assertThat(h(AssetType.ACAO, "0.000001").valueBrl()).isEqualByComparingTo("0.000001");
        assertThat(h(AssetType.ACAO, "100.00").valueBrl()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> h(AssetType.ACAO, "1000000000000000")).hasMessageContaining("fora do intervalo");
    }

    @Test void moreAnswersThanQuestionsIsRejectedBeforeAnyWork() {
        Map<String, String> huge = answers();
        for (int i = 0; i < 1000; i++) huge.put("EXTRA" + i, "X");
        assertThatThrownBy(() -> SuitabilityEngine.evaluate(huge)).hasMessageContaining("Respostas demais");
    }
}
