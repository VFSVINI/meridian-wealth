package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.portfolio.PortfolioException;
import dev.meridianwealth.application.portfolio.PortfolioService;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.Position;
import dev.meridianwealth.domain.portfolio.PositionValuation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Carteira do próprio usuário, sob {@code /api/me} (o filtro de autenticação a protege e o dono vem do token). A valorização usa
 * cotações e PTAX reais, cada uma com fonte, horário e selo de atraso; o que não tem dado fica "não informado" (nulo), nunca
 * estimado. Não é recomendação de investimento e a plataforma não executa ordens.
 */
@RestController
@RequestMapping("/api/me/portfolio")
public class PortfolioController {
    static final String DISCLAIMER = "Ferramenta analítica e educacional. A carteira é um registro seu, sem corretagem, custos e impostos que você não incluiu no preço médio. "
            + "Não é extrato de corretora, não executa ordens e não é recomendação de investimento.";

    public record OpenBody(String symbol, AssetType type, String label, BigDecimal quantity, BigDecimal averagePrice, Currency currency) {}

    public record PurchaseBody(BigDecimal quantity, BigDecimal unitPrice, Long expectedVersion) {}

    public record SaleBody(BigDecimal quantity, Long expectedVersion) {}

    public record ReplaceBody(BigDecimal quantity, BigDecimal averagePrice, Long expectedVersion) {}

    public record PositionView(UUID id, String assetKey, String symbol, String type, String typeLabel, String category, String label, BigDecimal quantity,
                               BigDecimal averagePrice, String currency, BigDecimal costBasis, long version, Instant createdAt, Instant updatedAt) {
        static PositionView of(Position p) {
            return new PositionView(p.id(), p.asset().key(), p.asset().symbol(), p.asset().type().name(), p.asset().type().label(),
                    p.asset().type().category().name(), p.asset().label(), p.quantity(), p.averagePrice(), p.currency().name(), p.costBasis(), p.version(),
                    p.createdAt(), p.updatedAt());
        }
    }

    public record PriceView(BigDecimal price, String currency, String source, Instant quoteTime, String freshness, boolean stale) {}

    public record LineView(PositionView position, String basis, PriceView price, BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent,
                           BigDecimal costBasisBrl, BigDecimal currentValueBrl) {
        static LineView of(PortfolioService.Line l) {
            PositionValuation.Result v = l.valuation();
            PositionValuation.MarketPrice p = v.price();
            return new LineView(PositionView.of(v.position()), v.basis().name(),
                    p == null ? null : new PriceView(p.price(), p.currency().name(), p.source(), p.quoteTime(), p.freshnessLabel(), p.stale()), v.marketValue(),
                    v.unrealizedPnl(), v.unrealizedPnlPercent(), v.costBasisBrl(), v.currentValueBrl());
        }
    }

    public record FxView(BigDecimal brlPerUsd, LocalDate date, String source, boolean stale) {}

    public record ClassView(String category, String label, BigDecimal percent) {}

    public record SummaryView(List<LineView> positions, FxView fx, BigDecimal totalCurrentValueBrl, BigDecimal totalCostBasisBrl, int positionsWithoutBrlValue,
                              List<ClassView> byClass, List<String> notes, String disclaimer) {}

    public record SaleView(boolean closed, PositionView position) {}

    private final PortfolioService portfolio;

    public PortfolioController(PortfolioService portfolio) { this.portfolio = portfolio; }

    @GetMapping
    public SummaryView list(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who) {
        PortfolioService.Summary s = portfolio.list(who);
        return new SummaryView(s.lines().stream().map(LineView::of).toList(), s.fx() == null ? null : new FxView(s.fx().brlPerUsd(), s.fx().date(), s.fx().source(), s.fx().stale()),
                s.totalCurrentValueBrl(), s.totalCostBasisBrl(), s.positionsWithoutBrlValue(),
                s.byClass().stream().map(w -> new ClassView(w.category().name(), w.category().label(), w.percent())).toList(), s.notes(), DISCLAIMER);
    }

    @PostMapping("/positions")
    public ResponseEntity<PositionView> open(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestBody OpenBody b) {
        Position p = portfolio.open(who, new PortfolioService.OpenRequest(b.symbol(), b.type(), b.label(), b.quantity(), b.averagePrice(), b.currency()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PositionView.of(p));
    }

    @PostMapping("/positions/{id}/purchases")
    public PositionView purchase(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id, @RequestBody PurchaseBody b) {
        return PositionView.of(portfolio.purchase(who, id, b.quantity(), b.unitPrice(), b.expectedVersion()));
    }

    @PostMapping("/positions/{id}/sales")
    public SaleView sale(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id, @RequestBody SaleBody b) {
        PortfolioService.SaleResult r = portfolio.sale(who, id, b.quantity(), b.expectedVersion());
        return new SaleView(r.closed(), r.closed() ? null : PositionView.of(r.position()));
    }

    @PutMapping("/positions/{id}")
    public PositionView replace(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id, @RequestBody ReplaceBody b) {
        return PositionView.of(portfolio.replace(who, id, b.quantity(), b.averagePrice(), b.expectedVersion()));
    }

    @DeleteMapping("/positions/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id,
                                       @RequestParam(required = false) Long expectedVersion) {
        portfolio.delete(who, id, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @RestControllerAdvice(assignableTypes = PortfolioController.class)
    static class Errors {
        @ExceptionHandler(PortfolioException.class)
        ResponseEntity<Map<String, String>> portfolio(PortfolioException e) {
            HttpStatus status = switch (e.code()) {
                case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case POSITION_EXISTS, VERSION_CONFLICT, LIMIT_REACHED -> HttpStatus.CONFLICT;
            };
            return ResponseEntity.status(status).body(Map.of("error", e.getMessage(), "code", e.code().name()));
        }
    }
}
