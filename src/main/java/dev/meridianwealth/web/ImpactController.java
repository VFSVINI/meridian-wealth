package dev.meridianwealth.web;

import dev.meridianwealth.application.OrderImpactService;
import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.impact.PreOrderImpact;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import dev.meridianwealth.domain.suitability.PortfolioAdherence;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulação de impacto pré-ordem. É só cálculo: não existe rota que envie ordem, e a resposta se declara simulação. Carteira e
 * ordem vêm na requisição e não são gravadas nem registradas em log (dados pessoais).
 */
@RestController
@RequestMapping("/api/impact")
public class ImpactController {
    static final String LABEL = "SIMULAÇÃO: não é cotação nem ordem. A plataforma não executa ordens.";
    static final List<String> ASSUMPTIONS = List.of(
            "Os valores são os em reais que você informou: nenhuma cotação é usada para converter ou precificar.",
            "Não inclui custos, corretagem, impostos, spread nem o preço de execução.",
            "A volatilidade é a anualizada dos últimos 60 períodos de dado real, quando existe; sem dado, a posição fica fora do cálculo de risco.",
            "O risco da carteira é a média ponderada das volatilidades (limite superior): ignora a correlação entre os ativos.");
    static final String DISCLAIMER = "Ferramenta analítica e educacional. Nada aqui é recomendação de investimento; o resultado real depende "
            + "do preço no momento da ordem e dos custos.";

    public record PositionRequest(String symbol, AssetType type, BigDecimal valueBrl) {}

    public record OrderRequest(PreOrderImpact.Side side, String symbol, AssetType type, BigDecimal valueBrl) {}

    public record ImpactRequest(List<PositionRequest> positions, OrderRequest order, InvestorProfile profile) {}

    public record ClassPercent(String category, String label, BigDecimal percent) {}

    public record SnapshotView(boolean empty, BigDecimal totalBrl, int positions, String largestLabel, BigDecimal largestPercent, BigDecimal hhi,
                               BigDecimal effectivePositions, BigDecimal weightedVolatility, BigDecimal volatilityCoverage,
                               List<ClassPercent> classes) {
        static SnapshotView of(PreOrderImpact.Snapshot s) {
            List<ClassPercent> classes = s.classPercents().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(e -> new ClassPercent(e.getKey().name(), label(e.getKey()), e.getValue())).toList();
            return new SnapshotView(s.empty(), s.totalBrl(), s.positions(), s.largestLabel(), s.largestPercent(), s.hhi(),
                    s.effectivePositions(), s.weightedVolatility(), s.volatilityCoverage(), classes);
        }
    }

    public record AdherenceSummary(BigDecimal score, int classesOutsideBand) {
        static AdherenceSummary of(PortfolioAdherence.Result r) { return r == null ? null : new AdherenceSummary(r.score(), r.classesOutsideBand()); }
    }

    public record FindingView(String level, String text) {}

    public record ImpactView(boolean simulation, String label, String side, String orderLabel, BigDecimal orderAmountBrl, SnapshotView before,
                                 SnapshotView after, AdherenceSummary adherenceBefore, AdherenceSummary adherenceAfter, List<FindingView> findings,
                                 List<String> assumptions, String disclaimer) {}

    private final OrderImpactService service;

    public ImpactController(OrderImpactService service) { this.service = service; }

    @PostMapping("/pre-order")
    public ImpactView preOrder(@RequestBody ImpactRequest request) {
        if (request == null || request.order() == null) throw new IllegalArgumentException("Informe a ordem a simular");
        if (request.positions() == null) throw new IllegalArgumentException("Informe a carteira (pode ser vazia para uma compra inicial)");
        OrderRequest o = request.order();
        List<OrderImpactService.Item> positions = request.positions().stream()
                .map(p -> {
                    if (p == null) throw new IllegalArgumentException("Posição inválida");
                    return new OrderImpactService.Item(p.symbol(), p.type(), p.valueBrl());
                }).toList();
        PreOrderImpact.Result r = service.analyze(positions, o.side(), new OrderImpactService.Item(o.symbol(), o.type(), o.valueBrl()),
                request.profile());
        return new ImpactView(true, LABEL, r.side().name(), r.orderLabel(), r.orderAmountBrl(), SnapshotView.of(r.before()),
                SnapshotView.of(r.after()), AdherenceSummary.of(r.adherenceBefore()), AdherenceSummary.of(r.adherenceAfter()),
                r.findings().stream().map(f -> new FindingView(f.level().name(), f.text())).toList(), ASSUMPTIONS, DISCLAIMER);
    }

    private static String label(AssetCategory c) { return c.label(); }
}
