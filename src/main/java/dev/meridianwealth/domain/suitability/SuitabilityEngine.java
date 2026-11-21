package dev.meridianwealth.domain.suitability;

import dev.meridianwealth.domain.suitability.Questionnaire.Cap;
import dev.meridianwealth.domain.suitability.Questionnaire.Question;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Motor de suitability: nota de 0 a 100 pela soma dos pontos (todas as perguntas têm o mesmo peso), perfil pela faixa
 * da nota e <b>tetos</b> por respostas incompatíveis com risco alto. O perfil final é o menor entre o da nota e os
 * tetos; cada teto aplicado vem com o motivo. Faixas: abaixo de 30 conservador, de 30 a 54,99 moderado, de 55 a 79,99
 * arrojado e 80 ou mais agressivo (definições do produto). Resposta faltando ou inválida é erro, nunca um palpite.
 */
public final class SuitabilityEngine {
    public record AnswerScore(String questionId, String optionId, int points, int maxPoints) {}

    public record Result(BigDecimal score, InvestorProfile scoreProfile, InvestorProfile profile, List<String> appliedCaps,
                         List<AnswerScore> answers) {
        public Result {
            appliedCaps = List.copyOf(appliedCaps);
            answers = List.copyOf(answers);
        }
    }

    static final BigDecimal MODERATE_FROM = new BigDecimal("30");
    static final BigDecimal BOLD_FROM = new BigDecimal("55");
    static final BigDecimal AGGRESSIVE_FROM = new BigDecimal("80");

    private SuitabilityEngine() {}

    /** @param answers pergunta -> opção escolhida; precisa ter todas as perguntas e só opções existentes */
    public static Result evaluate(Map<String, String> answers) {
        if (answers == null) throw new IllegalArgumentException("Respostas ausentes");
        TreeSet<String> unknown = new TreeSet<>(answers.keySet());
        Questionnaire.QUESTIONS.forEach(q -> unknown.remove(q.id()));
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Pergunta desconhecida: " + unknown.first());
        List<AnswerScore> scores = new ArrayList<>();
        int total = 0;
        for (Question q : Questionnaire.QUESTIONS) {
            String chosen = answers.get(q.id());
            if (chosen == null) throw new IllegalArgumentException("Pergunta sem resposta: " + q.id());
            var option = q.option(chosen).orElseThrow(() -> new IllegalArgumentException("Opção inválida para " + q.id() + ": " + chosen));
            total += option.points();
            scores.add(new AnswerScore(q.id(), option.id(), option.points(), q.maxPoints()));
        }
        BigDecimal score = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Questionnaire.maxTotalPoints()), 2, RoundingMode.HALF_EVEN);
        InvestorProfile byScore = profileFor(score);
        InvestorProfile profile = byScore;
        List<String> applied = new ArrayList<>();
        for (Cap cap : Questionnaire.CAPS) {
            if (cap.optionId().equals(answers.get(cap.questionId())) && byScore.compareTo(cap.maxProfile()) > 0) {
                profile = InvestorProfile.min(profile, cap.maxProfile());
                applied.add(cap.reason() + " Teto: " + cap.maxProfile().label() + ".");
            }
        }
        return new Result(score, byScore, profile, applied, scores);
    }

    static InvestorProfile profileFor(BigDecimal score) {
        if (score.compareTo(AGGRESSIVE_FROM) >= 0) return InvestorProfile.AGGRESSIVE;
        if (score.compareTo(BOLD_FROM) >= 0) return InvestorProfile.BOLD;
        if (score.compareTo(MODERATE_FROM) >= 0) return InvestorProfile.MODERATE;
        return InvestorProfile.CONSERVATIVE;
    }
}
