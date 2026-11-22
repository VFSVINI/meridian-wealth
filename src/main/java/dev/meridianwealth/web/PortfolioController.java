package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.portfolio.PortfolioException;
import dev.meridianwealth.application.portfolio.PortfolioService;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import dev.meridianwealth.domain.portfolio.PositionValuation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    static final String KEY_HEADER = "Idempotency-Key";
    static final String DISCLAIMER = "Ferramenta analítica e educacional. A carteira é um registro seu, sem corretagem, custos e impostos que você não incluiu no preço médio. "
            + "Não é extrato de corretora, não executa ordens e não é recomendação de investimento.";

    public record OpenBody(String symbol, AssetType type, String label, BigDecimal quantity, BigDecimal averagePrice, Currency currency, LocalDate tradeDate,
                           BigDecimal fxRate) {}

    public record PurchaseBody(BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees, LocalDate tradeDate, BigDecimal fxRate, Long expectedVersion) {}

    public record SaleBody(BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees, LocalDate tradeDate, BigDecimal fxRate, Long expectedVersion) {}

    public record ReplaceBody(BigDecimal quantity, BigDecimal averagePrice, LocalDate tradeDate, BigDecimal fxRate, Long expectedVersion) {}

    public record PositionView(UUID id, String assetKey, String symbol, String type, String typeLabel, String category, String label, BigDecimal quantity,
                               BigDecimal averagePrice, BigDecimal averageCostBrl, String currency, BigDecimal costBasis, long version, Instant createdAt,
                               Instant updatedAt) {
        static PositionView of(Position p) {
            return new PositionView(p.id(), p.asset().key(), p.asset().symbol(), p.asset().type().name(), p.asset().type().label(),
                    p.asset().type().category().name(), p.asset().label(), p.quantity(), p.averagePrice(), p.averageCostBrl(), p.currency().name(), p.costBasis(),
                    p.version(), p.createdAt(), p.updatedAt());
        }
    }

    /** Uma entrada do livro. {@code realizedPnl} (na moeda da posição) e {@code realizedPnlBrl} só existem nas vendas; nulo = "não informado". */
    public record EntryView(UUID id, UUID positionId, String assetKey, String symbol, String type, String label, String entryType, LocalDate tradeDate,
                            BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees, String currency, BigDecimal fxBrlPerUsd, String fxSource, LocalDate fxDate,
                            BigDecimal quantityAfter, BigDecimal averagePriceAfter, BigDecimal averageCostBrlAfter, BigDecimal realizedPnl, BigDecimal realizedPnlBrl,
                            long versionAfter, String idempotencyKey, Instant createdAt) {
        static EntryView of(LedgerEntry e) {
            return new EntryView(e.id(), e.positionId(), e.asset().key(), e.asset().symbol(), e.asset().type().name(), e.asset().label(), e.type().name(), e.tradeDate(),
                    e.quantity(), e.unitPrice(), e.fees(), e.currency().name(), e.fxBrlPerUsd(), e.fxSource() == null ? null : e.fxSource().name(), e.fxDate(),
                    e.quantityAfter(), e.averagePriceAfter(), e.averageCostBrlAfter(), e.realizedPnl(), e.realizedPnlBrl(), e.versionAfter(), e.idempotencyKey(), e.createdAt());
        }
    }

    /** Resultado de uma escrita: a entrada do livro e a posição depois dela ({@code null} se foi removida). {@code replayed}: repetição idempotente. */
    public record OperationView(EntryView entry, PositionView position, boolean closed, boolean replayed) {
        static OperationView of(PortfolioService.Operation o) {
            return new OperationView(EntryView.of(o.entry()), o.position() == null ? null : PositionView.of(o.position()), o.position() == null, o.replayed());
        }
    }

    public record EntriesView(List<EntryView> entries, int limit, int offset) {}

    public record PriceView(BigDecimal price, String currency, String source, Instant quoteTime, String freshness, boolean stale) {}

    public record LineView(PositionView position, String basis, PriceView price, BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent,
                           BigDecimal costBasisBrl, BigDecimal currentValueBrl, BigDecimal unrealizedPnlBrl, BigDecimal unrealizedPnlPercentBrl) {
        static LineView of(PortfolioService.Line l) {
            PositionValuation.Result v = l.valuation();
            PositionValuation.MarketPrice p = v.price();
            return new LineView(PositionView.of(v.position()), v.basis().name(),
                    p == null ? null : new PriceView(p.price(), p.currency().name(), p.source(), p.quoteTime(), p.freshnessLabel(), p.stale()), v.marketValue(),
                    v.unrealizedPnl(), v.unrealizedPnlPercent(), v.costBasisBrl(), v.currentValueBrl(), v.unrealizedPnlBrl(), v.unrealizedPnlPercentBrl());
        }
    }

    public record FxView(BigDecimal brlPerUsd, LocalDate date, String source, boolean stale) {}

    public record ClassView(String category, String label, BigDecimal percent) {}

    public record SummaryView(List<LineView> positions, FxView fx, BigDecimal totalCurrentValueBrl, BigDecimal totalCostBasisBrl, int positionsWithoutBrlValue,
                              List<ClassView> byClass, List<String> notes, String disclaimer) {}

    private final PortfolioService portfolio;

    public PortfolioController(PortfolioService portfolio) { this.portfolio = portfolio; }

    @GetMapping
    public SummaryView list(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who) {
        PortfolioService.Summary s = portfolio.list(who);
        return new SummaryView(s.lines().stream().map(LineView::of).toList(), s.fx() == null ? null : new FxView(s.fx().brlPerUsd(), s.fx().date(), s.fx().source(), s.fx().stale()),
                s.totalCurrentValueBrl(), s.totalCostBasisBrl(), s.positionsWithoutBrlValue(),
                s.byClass().stream().map(w -> new ClassView(w.category().name(), w.category().label(), w.percent())).toList(), s.notes(), DISCLAIMER);
    }

    private static ResponseEntity<OperationView> respond(PortfolioService.Operation o, HttpStatus fresh) {
        ResponseEntity.BodyBuilder b = ResponseEntity.status(o.replayed() ? HttpStatus.OK : fresh);
        if (o.replayed()) b.header("Idempotent-Replay", "true");
        return b.body(OperationView.of(o));
    }

    @PostMapping("/positions")
    public ResponseEntity<OperationView> open(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestBody OpenBody b,
                                              @RequestHeader(value = KEY_HEADER, required = false) String key) {
        return respond(portfolio.open(who, new PortfolioService.OpenRequest(b.symbol(), b.type(), b.label(), b.quantity(), b.averagePrice(), b.currency(), b.tradeDate(), b.fxRate(), key)),
                HttpStatus.CREATED);
    }

    @PostMapping("/positions/{id}/purchases")
    public ResponseEntity<OperationView> purchase(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id, @RequestBody PurchaseBody b,
                                                  @RequestHeader(value = KEY_HEADER, required = false) String key) {
        return respond(portfolio.purchase(who, new PortfolioService.PurchaseRequest(id, b.quantity(), b.unitPrice(), b.fees(), b.tradeDate(), b.fxRate(), b.expectedVersion(), key)),
                HttpStatus.OK);
    }

    @PostMapping("/positions/{id}/sales")
    public ResponseEntity<OperationView> sale(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id, @RequestBody SaleBody b,
                                              @RequestHeader(value = KEY_HEADER, required = false) String key) {
        return respond(portfolio.sale(who, new PortfolioService.SaleRequest(id, b.quantity(), b.unitPrice(), b.fees(), b.tradeDate(), b.fxRate(), b.expectedVersion(), key)),
                HttpStatus.OK);
    }

    @PutMapping("/positions/{id}")
    public ResponseEntity<OperationView> replace(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id, @RequestBody ReplaceBody b,
                                                 @RequestHeader(value = KEY_HEADER, required = false) String key) {
        return respond(portfolio.replace(who, new PortfolioService.ReplaceRequest(id, b.quantity(), b.averagePrice(), b.tradeDate(), b.fxRate(), b.expectedVersion(), key)),
                HttpStatus.OK);
    }

    /** Baixa: remove a posição sem venda. {@code expectedVersion} e {@code tradeDate} vão como parâmetros porque o DELETE não tem corpo. */
    @DeleteMapping("/positions/{id}")
    public ResponseEntity<OperationView> delete(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @PathVariable UUID id,
                                                @RequestParam(required = false) Long expectedVersion, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
                                                @RequestHeader(value = KEY_HEADER, required = false) String key) {
        return respond(portfolio.delete(who, new PortfolioService.DeleteRequest(id, tradeDate, expectedVersion, key)), HttpStatus.OK);
    }

    @GetMapping("/transactions")
    public EntriesView transactions(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestParam(required = false) String assetKey,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Integer limit,
                                    @RequestParam(required = false) Integer offset) {
        List<LedgerEntry> entries = portfolio.entries(who, assetKey, from, to, limit, offset);
        return new EntriesView(entries.stream().map(EntryView::of).toList(), limit == null ? 50 : limit, offset == null ? 0 : offset);
    }

    @RestControllerAdvice(assignableTypes = {PortfolioController.class, TaxController.class})
    static class Errors {
        @ExceptionHandler(PortfolioException.class)
        ResponseEntity<Map<String, String>> portfolio(PortfolioException e) {
            HttpStatus status = switch (e.code()) {
                case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case POSITION_EXISTS, VERSION_CONFLICT, LIMIT_REACHED -> HttpStatus.CONFLICT;
                case OUT_OF_ORDER, IDEMPOTENCY_KEY_REUSED -> HttpStatus.UNPROCESSABLE_ENTITY;
                case FX_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            return ResponseEntity.status(status).body(Map.of("error", e.getMessage(), "code", e.code().name()));
        }
    }
}
