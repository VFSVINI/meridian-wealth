package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.risk.CorrelationService;
import dev.meridianwealth.application.risk.MonteCarloBusyException;
import dev.meridianwealth.application.risk.MonteCarloException;
import dev.meridianwealth.application.risk.MonteCarloService;
import dev.meridianwealth.domain.montecarlo.MonteCarlo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Projeção patrimonial por Monte Carlo. {@code /api/risk/monte-carlo} usa só as premissas da requisição (pública, não toca em dado
 * pessoal); {@code /api/me/risk/monte-carlo} usa a carteira salva do próprio usuário (o filtro de autenticação a protege, o dono vem do
 * token). Toda resposta se declara <b>projeção</b>: não é cotação, não promete retorno e a plataforma não executa ordens.
 */
@RestController
@RequestMapping("/api")
public class RiskController {
    static final String DISCLAIMER = "Ferramenta analítica e educacional. Projeção, não cotação: não é promessa de retorno nem recomendação de investimento.";

    public record MonteCarloRequest(BigDecimal initial, BigDecimal monthlyContribution, Integer horizonYears, BigDecimal expectedReturnPercent,
                                    BigDecimal volatilityPercent, BigDecimal goal, Long seed) {
        MonteCarloService.Request toService() {
            if (horizonYears == null) throw new IllegalArgumentException("Informe o horizonte em anos");
            return new MonteCarloService.Request(initial, monthlyContribution, horizonYears, expectedReturnPercent, volatilityPercent, goal, seed);
        }
    }

    public record Point(int month, BigDecimal p5, BigDecimal p25, BigDecimal p50, BigDecimal p75, BigDecimal p95) {}

    public record Histogram(List<BigDecimal> edges, List<Integer> counts) {}

    /** Densidades por ano (a paisagem de probabilidades): limites comuns e, para cada ano de 0 a N, quantas trajetórias caíram em cada faixa. */
    public record YearlyDensity(List<BigDecimal> edges, List<List<Integer>> counts) {}

    public record CoverageView(String label, String assetKey, BigDecimal valueBrl, BigDecimal weightPercent, String treatment, String reason, String currency) {}

    public record StatisticsView(int commonDays, LocalDate from, LocalDate to, BigDecimal annualReturnPercent, BigDecimal annualVolatilityPercent) {}

    public record ProjectionView(String kind, String label, String mode, int paths, int months, BigDecimal initial, BigDecimal monthlyContribution, BigDecimal goal, long seed,
                                 BigDecimal expectedReturnPercent, BigDecimal volatilityPercent, List<Point> points, Histogram histogram, BigDecimal goalProbabilityPercent,
                                 List<BigDecimal> goalProbabilityByYearPercent, BigDecimal probabilityBelowInvestedPercent, BigDecimal totalInvested, BigDecimal mean,
                                 List<CoverageView> coverage, BigDecimal coveredPercent, StatisticsView statistics, YearlyDensity density, List<String> assumptions, String disclaimer) {}

    public record MatrixDto(List<String> keys, List<String> labels, List<List<BigDecimal>> values, List<List<Integer>> days) {}

    public record RiskDto(int days, BigDecimal portfolioVolatilityPercent, BigDecimal weightedAverageVolatilityPercent, BigDecimal diversificationBenefitPercent, BigDecimal coveredPercent,
                          List<String> assets) {}

    public record ConcentrationDto(List<String> categories, List<String> categoryLabels, List<String> riskLevels, List<List<BigDecimal>> cells, BigDecimal assetHhi, BigDecimal classHhi,
                                   BigDecimal effectivePositions, BigDecimal largestPositionPercent, List<CorrelationService.Position> topPositions) {}

    public record CorrelationView(String kind, MatrixDto assets, MatrixDto classes, RiskDto risk, ConcentrationDto concentration, List<CorrelationService.Excluded> excluded, int window,
                                  int minDays, BigDecimal totalBrl, List<String> notes, String disclaimer) {}

    private final MonteCarloService service;
    private final CorrelationService correlation;

    public RiskController(MonteCarloService service, CorrelationService correlation) {
        this.service = service;
        this.correlation = correlation;
    }

    /** Correlação, risco com diversificação e concentração da carteira salva do próprio usuário: tudo sobre retornos reais; célula sem dado fica nula. */
    @GetMapping("/me/risk/correlation")
    public CorrelationView correlation(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who) { return correlationView(correlation.analyze(who)); }

    private static BigDecimal r2(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN); }

    private static BigDecimal r3(double v) { return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_EVEN); }

    private static MatrixDto matrix(CorrelationService.MatrixView m) {
        if (m == null) return null;
        List<List<BigDecimal>> values = new ArrayList<>();
        for (List<Double> row : m.values()) values.add(row.stream().map(v -> v == null ? null : r2(v)).toList());
        return new MatrixDto(m.keys(), m.labels(), values, m.days());
    }

    static CorrelationView correlationView(CorrelationService.Output o) {
        CorrelationService.Concentration c = o.concentration();
        List<List<BigDecimal>> cells = new ArrayList<>();
        for (List<Double> row : c.cells()) cells.add(row.stream().map(RiskController::r2).toList());
        CorrelationService.RiskView r = o.risk();
        return new CorrelationView("ANALYSIS", matrix(o.assets()), matrix(o.classes()),
                r == null ? null : new RiskDto(r.days(), r2(r.portfolioVolatilityPercent()), r2(r.weightedAverageVolatilityPercent()), r2(r.diversificationBenefitPercent()), r2(r.coveredPercent()), r.assets()),
                new ConcentrationDto(c.categories(), c.categoryLabels(), c.riskLevels(), cells, r3(c.assetHhi()), r3(c.classHhi()), r2(c.effectivePositions()), r2(c.largestPositionPercent()), c.topPositions()),
                o.excluded(), o.window(), o.minDays(), r2(o.totalBrl()), o.notes(),
                "Ferramenta analítica e educacional. Correlação mede movimento conjunto no passado e pode mudar em crises; não é recomendação de investimento.");
    }

    @PostMapping("/risk/monte-carlo")
    public ProjectionView parametric(@RequestBody MonteCarloRequest r) { return view(service.parametric(r.toService())); }

    @PostMapping("/me/risk/monte-carlo")
    public ProjectionView fromPortfolio(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestBody MonteCarloRequest r) {
        return view(service.fromPortfolio(who, r.toService()));
    }

    private static BigDecimal money(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN); }

    private static BigDecimal pct(double fraction) { return BigDecimal.valueOf(fraction * 100).setScale(2, RoundingMode.HALF_EVEN); }

    static ProjectionView view(MonteCarloService.Output o) {
        MonteCarlo.Result r = o.result();
        List<Point> points = new ArrayList<>();
        for (int m = 0; m <= r.months(); m++) {
            points.add(new Point(m, money(r.percentiles()[0][m]), money(r.percentiles()[1][m]), money(r.percentiles()[2][m]), money(r.percentiles()[3][m]), money(r.percentiles()[4][m])));
        }
        List<BigDecimal> edges = new ArrayList<>();
        for (double e : r.histogramEdges()) edges.add(money(e));
        List<Integer> counts = new ArrayList<>();
        for (int c : r.histogramCounts()) counts.add(c);
        List<BigDecimal> dEdges = new ArrayList<>();
        for (double e : r.densityEdges()) dEdges.add(money(e));
        List<List<Integer>> dCounts = new ArrayList<>();
        for (int[] row : r.densityCounts()) {
            List<Integer> line = new ArrayList<>();
            for (int n : row) line.add(n);
            dCounts.add(line);
        }
        List<BigDecimal> byYear = null;
        if (r.goalProbabilityByYear() != null) {
            byYear = new ArrayList<>();
            for (double v : r.goalProbabilityByYear()) byYear.add(pct(v));
        }
        List<CoverageView> coverage = o.coverage().stream().map(c -> new CoverageView(c.label(), c.assetKey(), c.valueBrl(), BigDecimal.valueOf(c.weightPercent()).setScale(2, RoundingMode.HALF_EVEN),
                c.treatment(), c.reason(), c.currency())).toList();
        StatisticsView stats = o.statistics() == null ? null : new StatisticsView(o.statistics().commonDays(), o.statistics().from(), o.statistics().to(),
                pct(o.statistics().annualReturn()), pct(o.statistics().annualVolatility()));
        return new ProjectionView("PROJECTION", o.label(), o.parametric() ? "PARAMETRIC" : "PORTFOLIO_BOOTSTRAP", MonteCarlo.PATHS, o.months(), money(o.initial()), money(o.monthlyContribution()),
                o.goal() == null ? null : money(o.goal()), o.seed(), pct(o.expectedReturnAnnual()), pct(o.volatilityAnnual()), points, new Histogram(edges, counts),
                r.goalProbabilityFinal() == null ? null : pct(r.goalProbabilityFinal()), byYear, pct(r.probabilityBelowInvested()), money(r.totalInvested()), money(r.mean()),
                coverage, o.coveredPercent() == null ? null : BigDecimal.valueOf(o.coveredPercent()).setScale(2, RoundingMode.HALF_EVEN), stats, new YearlyDensity(dEdges, dCounts), o.assumptions(), DISCLAIMER);
    }

    @RestControllerAdvice(assignableTypes = RiskController.class)
    static class Errors {
        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage(), "code", "INVALID_INPUT"));
        }

        @ExceptionHandler(MonteCarloBusyException.class)
        ResponseEntity<Map<String, String>> busy(MonteCarloBusyException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "5").body(Map.of("error", e.getMessage(), "code", "BUSY"));
        }

        @ExceptionHandler(MonteCarloException.class)
        ResponseEntity<Map<String, String>> insufficient(MonteCarloException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage(), "code", "INSUFFICIENT_HISTORY"));
        }
    }
}
