package dev.meridianwealth.web;

import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import dev.meridianwealth.domain.suitability.ModelPortfolio;
import dev.meridianwealth.domain.suitability.PortfolioAdherence;
import dev.meridianwealth.domain.suitability.Questionnaire;
import dev.meridianwealth.domain.suitability.SuitabilityEngine;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suitability e carteira modelo. Sem estado e sem persistência: as respostas e a carteira vêm na requisição, viram um
 * resultado e não são gravadas nem registradas em log (são dados pessoais). Ferramenta analítica e educacional.
 */
@RestController
@RequestMapping("/api/suitability")
public class SuitabilityController {
    static final String DISCLAIMER = "Ferramenta analítica e educacional. O questionário não substitui a análise de um profissional "
            + "habilitado, as metas são ilustrativas do produto e nada aqui é recomendação de investimento.";
    static final int MAX_HOLDINGS = 50;

    public record OptionView(String id, String text, int points) {}

    public record QuestionView(String id, String dimension, String dimensionLabel, String legalRef, String text, int maxPoints,
                               List<OptionView> options) {}

    public record CapView(String questionId, String optionId, String maxProfile, String maxProfileLabel, String reason) {}

    public record QuestionnaireView(List<QuestionView> questions, List<CapView> caps, int maxTotalPoints, Map<String, String> scoreBands,
                                    String disclaimer) {}

    public record TargetView(String category, String label, BigDecimal percent) {}

    public record ModelPortfolioView(String profile, String profileLabel, List<TargetView> targets) {
        static ModelPortfolioView of(InvestorProfile p) {
            return new ModelPortfolioView(p.name(), p.label(), ModelPortfolio.CLASSES.stream()
                    .map(c -> new TargetView(c.name(), c.label(), ModelPortfolio.targets(p).get(c))).toList());
        }
    }

    public record ModelPortfoliosView(List<ModelPortfolioView> portfolios, BigDecimal bandPoints, String disclaimer) {}

    public record EvaluateRequest(Map<String, String> answers) {}

    public record EvaluationView(BigDecimal score, String scoreProfile, String scoreProfileLabel, String profile, String profileLabel,
                                 List<String> appliedCaps, List<SuitabilityEngine.AnswerScore> answers, ModelPortfolioView modelPortfolio,
                                 String disclaimer) {}

    public record HoldingRequest(AssetType type, BigDecimal valueBrl) {}

    public record AdherenceRequest(InvestorProfile profile, List<HoldingRequest> holdings) {}

    public record ClassView(String category, String label, BigDecimal valueBrl, BigDecimal currentPercent, BigDecimal targetPercent,
                            BigDecimal deviationPoints, String status) {}

    public record AdherenceView(String profile, String profileLabel, BigDecimal totalBrl, BigDecimal score, int classesOutsideBand,
                                BigDecimal bandPoints, List<ClassView> classes, String disclaimer) {}

    @GetMapping("/questionnaire")
    public QuestionnaireView questionnaire() {
        List<QuestionView> questions = Questionnaire.QUESTIONS.stream().map(q -> new QuestionView(q.id(), q.dimension().name(),
                q.dimension().label(), q.legalRef(), q.text(), q.maxPoints(),
                q.options().stream().map(o -> new OptionView(o.id(), o.text(), o.points())).toList())).toList();
        List<CapView> caps = Questionnaire.CAPS.stream().map(c -> new CapView(c.questionId(), c.optionId(), c.maxProfile().name(),
                c.maxProfile().label(), c.reason())).toList();
        return new QuestionnaireView(questions, caps, Questionnaire.maxTotalPoints(), Map.of("CONSERVATIVE", "abaixo de 30", "MODERATE",
                "de 30 a 54,99", "BOLD", "de 55 a 79,99", "AGGRESSIVE", "80 ou mais"), DISCLAIMER);
    }

    @PostMapping("/evaluate")
    public EvaluationView evaluate(@RequestBody EvaluateRequest request) {
        SuitabilityEngine.Result r = SuitabilityEngine.evaluate(request == null ? null : request.answers());
        return new EvaluationView(r.score(), r.scoreProfile().name(), r.scoreProfile().label(), r.profile().name(), r.profile().label(),
                r.appliedCaps(), r.answers(), ModelPortfolioView.of(r.profile()), DISCLAIMER);
    }

    @GetMapping("/model-portfolios")
    public ModelPortfoliosView modelPortfolios() {
        return new ModelPortfoliosView(Arrays.stream(InvestorProfile.values()).map(ModelPortfolioView::of).toList(),
                PortfolioAdherence.BAND_POINTS, DISCLAIMER);
    }

    @PostMapping("/adherence")
    public AdherenceView adherence(@RequestBody AdherenceRequest request) {
        if (request == null || request.profile() == null) throw new IllegalArgumentException("Informe o perfil");
        if (request.holdings() == null || request.holdings().isEmpty()) throw new IllegalArgumentException("A carteira está vazia");
        if (request.holdings().size() > MAX_HOLDINGS) throw new IllegalArgumentException("No máximo " + MAX_HOLDINGS + " posições");
        List<PortfolioAdherence.Holding> holdings = request.holdings().stream().map(h -> {
            if (h == null) throw new IllegalArgumentException("Posição inválida");
            return new PortfolioAdherence.Holding(h.type(), h.valueBrl());
        }).toList();
        PortfolioAdherence.Result r = PortfolioAdherence.evaluate(request.profile(), holdings);
        List<ClassView> classes = r.classes().stream().map(c -> new ClassView(c.category().name(), label(c.category()), c.valueBrl(),
                c.currentPercent(), c.targetPercent(), c.deviationPoints(), c.status().name())).toList();
        return new AdherenceView(r.profile().name(), r.profile().label(), r.totalBrl(), r.score(), r.classesOutsideBand(), r.bandPoints(),
                classes, DISCLAIMER);
    }

    private static String label(AssetCategory c) { return c.label(); }
}
