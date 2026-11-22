package dev.meridianwealth.application.portfolio;

import dev.meridianwealth.application.BrazilRatesService;
import dev.meridianwealth.application.DataStatus;
import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.TickBatch;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.IsolationGuard;
import dev.meridianwealth.application.port.EventPublisher;
import dev.meridianwealth.application.port.FxHistoryPort;
import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PortfolioLedger.Change;
import dev.meridianwealth.application.port.PortfolioLedger.Commit;
import dev.meridianwealth.application.port.PortfolioLedger.Op;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.application.portfolio.PortfolioException.Code;
import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.MoneyBounds;
import dev.meridianwealth.domain.RateSeries;
import dev.meridianwealth.domain.portfolio.AssetRef;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.EntryType;
import dev.meridianwealth.domain.portfolio.FxSource;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.PortfolioEvent;
import dev.meridianwealth.domain.portfolio.Position;
import dev.meridianwealth.domain.portfolio.PositionValuation;
import dev.meridianwealth.domain.portfolio.PositionValuation.FxRate;
import dev.meridianwealth.domain.portfolio.PositionValuation.MarketPrice;
import dev.meridianwealth.domain.portfolio.TaxReport;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Casos de uso da carteira do próprio usuário: abrir posição, compra (preço médio ponderado), venda (resultado realizado), ajuste
 * manual, baixa, listagem valorizada, consulta ao livro e apoio ao imposto de renda. Toda operação parte do {@link AuthPrincipal} (o dono
 * vem do token validado) e passa pelo {@link IsolationGuard}.
 *
 * <p>Toda escrita vai pelo {@link PortfolioLedger}: a entrada do livro e a mudança da posição acontecem na mesma transação, e só depois
 * do commit o evento é publicado. A escrita usa bloqueio otimista: quem perde a corrida recebe {@code VERSION_CONFLICT} e relê.
 *
 * <p><b>Idempotência:</b> com {@code Idempotency-Key}, repetir a mesma requisição devolve o resultado da primeira sem aplicar de novo; a
 * mesma chave com outro conteúdo é recusada ({@code IDEMPOTENCY_KEY_REUSED}); duas requisições simultâneas com a mesma chave aplicam uma só.
 *
 * <p><b>Datas:</b> a data da operação não pode estar no futuro nem ser anterior à última operação do mesmo ativo (o livro é cronológico por
 * ativo; registre do mais antigo para o mais novo). <b>Câmbio:</b> numa operação em dólar, o câmbio é o informado pelo usuário ou o PTAX
 * (venda) do último dia útil até a data; sem nenhum dos dois a operação é recusada, nunca estimada.
 */
public final class PortfolioService {
    public static final int MAX_POSITIONS = 200;
    public static final int MAX_PAGE = 200;
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

    public record OpenRequest(String symbol, AssetType type, String label, BigDecimal quantity, BigDecimal averagePrice, Currency currency, LocalDate tradeDate,
                              BigDecimal fxRate, String idempotencyKey) {}

    public record PurchaseRequest(UUID id, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees, LocalDate tradeDate, BigDecimal fxRate, Long expectedVersion,
                                  String idempotencyKey) {}

    public record SaleRequest(UUID id, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fees, LocalDate tradeDate, BigDecimal fxRate, Long expectedVersion,
                              String idempotencyKey) {}

    public record ReplaceRequest(UUID id, BigDecimal quantity, BigDecimal averagePrice, LocalDate tradeDate, BigDecimal fxRate, Long expectedVersion,
                                 String idempotencyKey) {}

    public record DeleteRequest(UUID id, LocalDate tradeDate, Long expectedVersion, String idempotencyKey) {}

    /** Resultado de uma escrita: a entrada do livro e a posição depois dela (nula se foi removida). {@code replayed} marca uma repetição idempotente. */
    public record Operation(LedgerEntry entry, Position position, boolean replayed) {}

    public record Line(PositionValuation.Result valuation, String assetKey, String symbol, AssetType type) {}

    public record ClassWeight(AssetCategory category, BigDecimal percent) {}

    public record FxView(BigDecimal brlPerUsd, LocalDate date, String source, boolean stale) {}

    public record Summary(List<Line> lines, FxView fx, BigDecimal totalCurrentValueBrl, BigDecimal totalCostBasisBrl, int positionsWithoutBrlValue,
                          List<ClassWeight> byClass, List<String> notes) {}

    private record Fx(BigDecimal rate, FxSource source, LocalDate date) {}

    private final PositionStore positions;
    private final PortfolioLedger ledger;
    private final MarketService market;
    private final BrazilRatesService rates;
    private final FxHistoryPort fxHistory;
    private final EventPublisher events;
    private final Clock clock;

    public PortfolioService(PositionStore positions, PortfolioLedger ledger, MarketService market, BrazilRatesService rates, FxHistoryPort fxHistory,
                            EventPublisher events, Clock clock) {
        this.positions = positions;
        this.ledger = ledger;
        this.market = market;
        this.rates = rates;
        this.fxHistory = fxHistory;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- leitura

    public Summary list(AuthPrincipal who) {
        List<Position> all = positions.findByUser(who.userId());
        for (Position p : all) IsolationGuard.requireOwner(p.userId(), who);
        Map<String, Instrument> universe = new HashMap<>();
        market.universe().forEach(i -> universe.put(i.symbol(), i));
        Map<String, MarketPrice> prices = all.stream().anyMatch(p -> p.asset().symbol() != null) ? quotes(universe) : Map.of();
        FxRate fx = fx();
        List<Line> lines = new ArrayList<>();
        for (Position p : all) {
            MarketPrice price = p.asset().symbol() == null ? null : prices.get(p.asset().symbol());
            lines.add(new Line(PositionValuation.value(p, price, fx), p.asset().key(), p.asset().symbol(), p.asset().type()));
        }
        return summarize(lines, fx);
    }

    /** Entradas do livro do usuário, mais antigas primeiro, com filtros e paginação. */
    public List<LedgerEntry> entries(AuthPrincipal who, String assetKey, LocalDate from, LocalDate to, Integer limit, Integer offset) {
        int lim = limit == null ? 50 : limit;
        int off = offset == null ? 0 : offset;
        if (lim < 1 || lim > MAX_PAGE) throw new PortfolioException(Code.INVALID_INPUT, "limit deve ficar entre 1 e " + MAX_PAGE);
        if (off < 0) throw new PortfolioException(Code.INVALID_INPUT, "offset não pode ser negativo");
        if (from != null && to != null && from.isAfter(to)) throw new PortfolioException(Code.INVALID_INPUT, "O início do intervalo é depois do fim");
        return ledger.entries(who.userId(), assetKey == null || assetKey.isBlank() ? null : assetKey, from, to, lim, off);
    }

    /** Apoio ao imposto de renda de um ano (ver {@link TaxReport}). */
    public TaxReport.Result taxReport(AuthPrincipal who, int year) {
        int current = LocalDate.now(clock).getYear();
        if (year < 2000 || year > current) throw new PortfolioException(Code.INVALID_INPUT, "O ano precisa estar entre 2000 e " + current);
        return TaxReport.build(ledger.all(who.userId()), year);
    }

    // ---------------------------------------------------------------- escrita

    /** Abre a posição (saldo inicial informado): entrada OPENING. */
    public Operation open(AuthPrincipal who, OpenRequest r) {
        String key = checkKey(r.idempotencyKey());
        AssetRef asset;
        Currency currency;
        try {
            if (r.symbol() != null && !r.symbol().isBlank()) {
                String symbol = r.symbol().trim().toUpperCase(Locale.ROOT);
                Instrument inst = market.universe().stream().filter(i -> i.symbol().equals(symbol)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Ativo fora do universo: " + symbol));
                currency = Currency.fromCode(inst.currency()).orElseThrow(() -> new IllegalArgumentException("A moeda de " + symbol + " (" + inst.currency()
                        + ") não é suportada: a carteira aceita ativos em BRL e USD"));
                if (r.currency() != null && r.currency() != currency) {
                    throw new IllegalArgumentException(symbol + " é cotado em " + currency + "; a posição precisa ser em " + currency);
                }
                if (r.type() != null && r.type() != inst.type()) throw new IllegalArgumentException("O tipo informado não confere com " + symbol);
                asset = new AssetRef(symbol, inst.type(), symbol + " · " + inst.name());
            } else {
                if (r.type() == null) throw new IllegalArgumentException("Informe o símbolo ou o tipo do ativo");
                asset = new AssetRef(null, r.type(), r.label());
                currency = r.currency() == null ? Currency.BRL : r.currency();
            }
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
        String hash = hash("open", who, asset.key(), currency, r.quantity(), r.averagePrice(), r.tradeDate(), r.fxRate());
        Optional<Operation> replay = replay(who, key, hash);
        if (replay.isPresent()) return replay.get();
        if (positions.countByUser(who.userId()) >= MAX_POSITIONS) throw new PortfolioException(Code.LIMIT_REACHED, "Limite de " + MAX_POSITIONS + " posições por carteira atingido");
        LocalDate date = tradeDate(r.tradeDate());
        checkOrder(who, asset.key(), date);
        Instant now = clock.instant();
        attempt(() -> Position.open(who.userId(), asset, r.quantity(), r.averagePrice(), currency, null, now)); // valida antes de consultar o câmbio
        Fx fx = fx(currency, date, r.fxRate());
        Position p = attempt(() -> Position.open(who.userId(), asset, r.quantity(), r.averagePrice(), currency, fx == null ? null : fx.rate(), now));
        LedgerEntry entry = entry(who, p.id(), asset, EntryType.OPENING, date, p.quantity(), p.averagePrice(), BigDecimal.ZERO, currency, fx, p, null, null, 0, key, key == null ? null : hash, now);
        return finish(ledger.commit(new Change(Op.OPEN, p, p.id(), 0, entry)), who, key, hash);
    }

    /** Compra: o preço médio passa a ser o ponderado pelo custo, com as taxas dentro dele. */
    public Operation purchase(AuthPrincipal who, PurchaseRequest r) {
        String key = checkKey(r.idempotencyKey());
        BigDecimal fees = r.fees() == null ? BigDecimal.ZERO : r.fees();
        String hash = hash("buy", who, r.id(), r.quantity(), r.unitPrice(), fees, r.tradeDate(), r.fxRate(), r.expectedVersion());
        Optional<Operation> replay = replay(who, key, hash);
        if (replay.isPresent()) return replay.get();
        Position current = load(who, r.id(), r.expectedVersion());
        LocalDate date = tradeDate(r.tradeDate());
        checkOrder(who, current.asset().key(), date);
        Instant now = clock.instant();
        attempt(() -> current.purchase(r.quantity(), r.unitPrice(), fees, null, now)); // valida antes de consultar o câmbio: o erro do usuário vence e nada é gasto à toa
        Fx fx = fx(current.currency(), date, r.fxRate());
        Position next = attempt(() -> current.purchase(r.quantity(), r.unitPrice(), fees, fx == null ? null : fx.rate(), now));
        long after = current.version() + 1;
        LedgerEntry entry = entry(who, current.id(), current.asset(), EntryType.BUY, date, r.quantity(), r.unitPrice(), fees, current.currency(), fx, next.withVersion(after),
                null, null, after, key, key == null ? null : hash, now);
        return finish(ledger.commit(new Change(Op.UPDATE, next, current.id(), current.version(), entry)), who, key, hash);
    }

    /** Venda: reduz a quantidade, mantém o preço médio e realiza o resultado; zerar remove a posição. */
    public Operation sale(AuthPrincipal who, SaleRequest r) {
        String key = checkKey(r.idempotencyKey());
        BigDecimal fees = r.fees() == null ? BigDecimal.ZERO : r.fees();
        String hash = hash("sell", who, r.id(), r.quantity(), r.unitPrice(), fees, r.tradeDate(), r.fxRate(), r.expectedVersion());
        Optional<Operation> replay = replay(who, key, hash);
        if (replay.isPresent()) return replay.get();
        Position current = load(who, r.id(), r.expectedVersion());
        LocalDate date = tradeDate(r.tradeDate());
        checkOrder(who, current.asset().key(), date);
        Instant now = clock.instant();
        attempt(() -> current.sale(r.quantity(), r.unitPrice(), fees, null, now)); // valida antes de consultar o câmbio
        Fx fx = fx(current.currency(), date, r.fxRate());
        Position.SaleOutcome out = attempt(() -> current.sale(r.quantity(), r.unitPrice(), fees, fx == null ? null : fx.rate(), now));
        boolean closed = out.remaining().isEmpty();
        long after = closed ? current.version() : current.version() + 1;
        Position state = closed ? null : out.remaining().get().withVersion(after);
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID(), who.userId(), current.id(), current.asset(), EntryType.SELL, date, r.quantity(), r.unitPrice(), fees, current.currency(),
                fx == null ? null : fx.rate(), fx == null ? null : fx.source(), fx == null ? null : fx.date(), state == null ? BigDecimal.ZERO : state.quantity(),
                state == null ? null : state.averagePrice(), state == null ? null : state.averageCostBrl(), out.realized(), out.realizedBrl(), after, key, key == null ? null : hash, now);
        Change change = closed ? new Change(Op.DELETE, null, current.id(), current.version(), entry) : new Change(Op.UPDATE, out.remaining().get(), current.id(), current.version(), entry);
        return finish(ledger.commit(change), who, key, hash);
    }

    /** Ajuste manual: substitui quantidade e preço médio (por exemplo, para acertar com o que a corretora mostra). */
    public Operation replace(AuthPrincipal who, ReplaceRequest r) {
        if (r.expectedVersion() == null) throw new PortfolioException(Code.INVALID_INPUT, "Informe expectedVersion para substituir a posição");
        String key = checkKey(r.idempotencyKey());
        String hash = hash("adjust", who, r.id(), r.quantity(), r.averagePrice(), r.tradeDate(), r.fxRate(), r.expectedVersion());
        Optional<Operation> replay = replay(who, key, hash);
        if (replay.isPresent()) return replay.get();
        Position current = load(who, r.id(), r.expectedVersion());
        LocalDate date = tradeDate(r.tradeDate());
        checkOrder(who, current.asset().key(), date);
        Instant now = clock.instant();
        attempt(() -> current.declare(r.quantity(), r.averagePrice(), null, now)); // valida antes de consultar o câmbio
        Fx fx = fx(current.currency(), date, r.fxRate());
        Position next = attempt(() -> current.declare(r.quantity(), r.averagePrice(), fx == null ? null : fx.rate(), now));
        long after = current.version() + 1;
        LedgerEntry entry = entry(who, current.id(), current.asset(), EntryType.ADJUSTMENT, date, next.quantity(), next.averagePrice(), BigDecimal.ZERO, current.currency(), fx,
                next.withVersion(after), null, null, after, key, key == null ? null : hash, now);
        return finish(ledger.commit(new Change(Op.UPDATE, next, current.id(), current.version(), entry)), who, key, hash);
    }

    /** Baixa: remove a posição sem venda (sem resultado realizado). O livro guarda a história. */
    public Operation delete(AuthPrincipal who, DeleteRequest r) {
        String key = checkKey(r.idempotencyKey());
        String hash = hash("writeoff", who, r.id(), r.tradeDate(), r.expectedVersion());
        Optional<Operation> replay = replay(who, key, hash);
        if (replay.isPresent()) return replay.get();
        Position current = load(who, r.id(), r.expectedVersion());
        LocalDate date = tradeDate(r.tradeDate());
        checkOrder(who, current.asset().key(), date);
        Instant now = clock.instant();
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID(), who.userId(), current.id(), current.asset(), EntryType.WRITE_OFF, date, current.quantity(), null, BigDecimal.ZERO,
                current.currency(), null, null, null, BigDecimal.ZERO, null, null, null, null, current.version(), key, key == null ? null : hash, now);
        return finish(ledger.commit(new Change(Op.DELETE, null, current.id(), current.version(), entry)), who, key, hash);
    }

    // ---------------------------------------------------------------- apoio

    private Operation finish(Commit c, AuthPrincipal who, String key, String hash) {
        if (c instanceof Commit.Committed done) {
            publish(done.entry(), done.position() == null);
            return new Operation(done.entry(), done.position(), false);
        }
        if (c instanceof Commit.Duplicate dup) return replayOf(who, dup.existing(), hash);
        if (c instanceof Commit.PositionExists) {
            throw new PortfolioException(Code.POSITION_EXISTS, "Você já tem uma posição neste ativo. Registre uma compra, uma venda ou ajuste a posição");
        }
        throw conflict();
    }

    private Optional<Operation> replay(AuthPrincipal who, String key, String hash) {
        if (key == null) return Optional.empty();
        return ledger.findByKey(who.userId(), key).map(e -> replayOf(who, e, hash));
    }

    private Operation replayOf(AuthPrincipal who, LedgerEntry existing, String hash) {
        if (!existing.userId().equals(who.userId())) throw new PortfolioException(Code.NOT_FOUND, "Posição não encontrada");
        if (!hash.equals(existing.requestHash())) {
            throw new PortfolioException(Code.IDEMPOTENCY_KEY_REUSED, "Esta Idempotency-Key já foi usada com outro conteúdo. Use uma chave nova para uma operação nova");
        }
        return new Operation(existing, positions.find(existing.positionId(), who.userId()).orElse(null), true);
    }

    private static String checkKey(String key) {
        if (key == null) return null;
        if (!KEY.matcher(key).matches()) {
            throw new PortfolioException(Code.INVALID_INPUT, "Idempotency-Key deve ter de 1 a 64 caracteres entre letras, números, '_', '.', ':' e '-'");
        }
        return key;
    }

    private Position load(AuthPrincipal who, UUID id, Long expectedVersion) {
        if (id == null) throw new PortfolioException(Code.NOT_FOUND, "Posição não encontrada");
        Position p = positions.find(id, who.userId()).orElseThrow(() -> new PortfolioException(Code.NOT_FOUND, "Posição não encontrada"));
        IsolationGuard.requireOwner(p.userId(), who);
        if (expectedVersion != null && expectedVersion != p.version()) throw conflict();
        return p;
    }

    private LocalDate tradeDate(LocalDate requested) {
        LocalDate today = LocalDate.now(clock);
        LocalDate d = requested == null ? today : requested;
        if (d.isAfter(today)) throw new PortfolioException(Code.INVALID_INPUT, "A data da operação (" + d + ") está no futuro");
        if (d.isBefore(EARLIEST)) throw new PortfolioException(Code.INVALID_INPUT, "A data da operação não pode ser anterior a " + EARLIEST);
        return d;
    }

    /** O livro é cronológico por ativo: não aceita operação anterior à última do mesmo ativo. */
    private void checkOrder(AuthPrincipal who, String assetKey, LocalDate date) {
        ledger.lastTradeDate(who.userId(), assetKey).ifPresent(last -> {
            if (date.isBefore(last)) {
                throw new PortfolioException(Code.OUT_OF_ORDER, "A última operação deste ativo é de " + last + "; registre as operações da mais antiga para a mais nova (data informada: " + date + ")");
            }
        });
    }

    /** Câmbio de uma operação: nenhum em BRL; em USD, o informado ou o PTAX (venda) do último dia útil até a data, senão a operação é recusada. */
    private Fx fx(Currency currency, LocalDate date, BigDecimal userRate) {
        if (currency == Currency.BRL) return null;
        if (userRate != null) {
            try {
                return new Fx(MoneyBounds.requirePositive(userRate, "câmbio", Position.PRICE_DECIMALS), FxSource.USER, date);
            } catch (IllegalArgumentException e) {
                throw invalid(e);
            }
        }
        return fxHistory.ptaxSellOnOrBefore(date).map(q -> new Fx(q.brlPerUsd(), FxSource.PTAX, q.referenceDate()))
                .orElseThrow(() -> new PortfolioException(Code.FX_UNAVAILABLE, "O PTAX de " + date + " não está disponível agora. Informe o câmbio da operação (fxRate, reais por dólar) ou tente de novo mais tarde"));
    }

    private LedgerEntry entry(AuthPrincipal who, UUID positionId, AssetRef asset, EntryType type, LocalDate date, BigDecimal quantity, BigDecimal price, BigDecimal fees, Currency currency,
                              Fx fx, Position after, BigDecimal realized, BigDecimal realizedBrl, long versionAfter, String key, String hash, Instant now) {
        return new LedgerEntry(UUID.randomUUID(), who.userId(), positionId, asset, type, date, quantity, price, fees, currency, fx == null ? null : fx.rate(), fx == null ? null : fx.source(),
                fx == null ? null : fx.date(), after.quantity(), after.averagePrice(), after.averageCostBrl(), realized, realizedBrl, versionAfter, key, key == null ? null : hash, now);
    }

    private void publish(LedgerEntry e, boolean closed) {
        Instant at = e.createdAt();
        String k = e.asset().key();
        PortfolioEvent event = switch (e.type()) {
            case OPENING -> new PortfolioEvent.PositionOpened(e.userId(), e.id(), k, at, e.tradeDate());
            case BUY -> new PortfolioEvent.PurchaseRecorded(e.userId(), e.id(), k, at, e.tradeDate(), e.quantity(), e.averagePriceAfter());
            case SELL -> new PortfolioEvent.SaleRecorded(e.userId(), e.id(), k, at, e.tradeDate(), e.quantity(), e.realizedPnl(), e.realizedPnlBrl(), closed);
            case ADJUSTMENT -> new PortfolioEvent.PositionAdjusted(e.userId(), e.id(), k, at, e.tradeDate());
            case WRITE_OFF -> new PortfolioEvent.PositionClosed(e.userId(), e.id(), k, at, e.tradeDate());
        };
        events.publish(event);
    }

    private static <T> T attempt(Supplier<T> op) {
        try {
            return op.get();
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        } catch (NullPointerException e) {
            throw new PortfolioException(Code.INVALID_INPUT, "Campo obrigatório ausente: " + e.getMessage());
        }
    }

    private static PortfolioException invalid(IllegalArgumentException e) { return new PortfolioException(Code.INVALID_INPUT, e.getMessage()); }

    private static PortfolioException conflict() {
        return new PortfolioException(Code.VERSION_CONFLICT, "A posição foi alterada por outra operação. Releia a carteira e tente de novo");
    }

    /**
     * Forma canônica de um número para o hash. A ordem de grandeza é conferida <b>antes</b> de {@code toPlainString}: {@code 1E999999999}
     * viraria uma string de um gigabyte e derrubaria o servidor por falta de memória antes de qualquer validação de valor.
     */
    private static String canonical(BigDecimal b) {
        if (Math.abs((long) b.scale()) > 64 || (long) b.precision() - b.scale() > 64) {
            throw new PortfolioException(Code.INVALID_INPUT, "Valor numérico fora do intervalo aceito");
        }
        return b.signum() == 0 ? "0" : b.stripTrailingZeros().toPlainString();
    }

    /** Hash do conteúdo da requisição (operação, usuário e parâmetros normalizados): é o que distingue uma repetição de uma chave reutilizada. */
    static String hash(String op, AuthPrincipal who, Object... parts) {
        StringBuilder sb = new StringBuilder(op).append('|').append(who.userId());
        for (Object p : parts) {
            sb.append('|');
            if (p instanceof BigDecimal b) sb.append(canonical(b));
            else sb.append(p);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cotações reais do painel agora: só ativos com dado e em moeda suportada; sem cotação, a posição fica avaliada pelo custo. */
    private Map<String, MarketPrice> quotes(Map<String, Instrument> universe) {
        Map<String, MarketPrice> out = new HashMap<>();
        for (TickBatch.Tick t : market.overview().ticks()) {
            if (t.status() == DataStatus.UNAVAILABLE || t.candle() == null) continue;
            Instrument inst = universe.get(t.symbol());
            Optional<Currency> cur = inst == null ? Optional.empty() : Currency.fromCode(inst.currency());
            if (cur.isEmpty() || !Double.isFinite(t.candle().close()) || t.candle().close() <= 0) continue;
            out.put(t.symbol(), new MarketPrice(BigDecimal.valueOf(t.candle().close()), cur.get(), t.source(), Instant.ofEpochMilli(t.quoteTime()),
                    t.freshness() == null ? "—" : t.freshness().label(), t.stale() || t.status() == DataStatus.STORED));
        }
        return out;
    }

    private FxRate fx() {
        for (BrazilRatesService.Item i : rates.items()) {
            if (i.series() == RateSeries.PTAX_SELL && i.observation() != null) {
                return new FxRate(i.observation().value(), i.observation().date(), "PTAX (venda), Banco Central do Brasil", i.status() == BrazilRatesService.Status.STALE);
            }
        }
        return null;
    }

    private static Summary summarize(List<Line> lines, FxRate fx) {
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int withoutBrl = 0;
        boolean anyCostBasis = false;
        Map<AssetCategory, BigDecimal> byClass = new EnumMap<>(AssetCategory.class);
        for (Line l : lines) {
            PositionValuation.Result v = l.valuation();
            if (v.basis() == PositionValuation.Basis.COST) anyCostBasis = true;
            if (v.currentValueBrl() == null || v.costBasisBrl() == null) {
                withoutBrl++;
                continue;
            }
            totalCurrent = totalCurrent.add(v.currentValueBrl());
            totalCost = totalCost.add(v.costBasisBrl());
            byClass.merge(l.type().category(), v.currentValueBrl(), BigDecimal::add);
        }
        List<ClassWeight> weights = new ArrayList<>();
        if (totalCurrent.signum() > 0) {
            for (var e : byClass.entrySet()) {
                weights.add(new ClassWeight(e.getKey(), e.getValue().multiply(HUNDRED, MathContext.DECIMAL128).divide(totalCurrent, 2, RoundingMode.HALF_EVEN)));
            }
            weights.sort(Comparator.comparing(ClassWeight::percent).reversed().thenComparing(w -> w.category().name()));
        }
        List<String> notes = new ArrayList<>();
        if (lines.isEmpty()) notes.add("Carteira vazia.");
        if (anyCostBasis) notes.add("Posições sem cotação de mercado entram pelo custo (preço médio x quantidade); não há valor de mercado nem resultado para elas.");
        if (fx != null) {
            notes.add("Valores atuais em reais de posições em dólar usam o " + fx.source() + " de " + fx.date() + (fx.stale() ? " (desatualizado)" : "")
                    + ". O custo em reais usa o câmbio da data de cada compra, registrado no livro, então o resultado em reais inclui a variação cambial.");
        } else if (lines.stream().anyMatch(l -> l.valuation().position().currency() == Currency.USD)) {
            notes.add("Sem PTAX do Banco Central agora: posições em dólar ficam sem valor atual em reais (não informado).");
        }
        if (lines.stream().anyMatch(l -> l.valuation().position().currency() == Currency.USD && l.valuation().costBasisBrl() == null)) {
            notes.add("Posições em dólar abertas antes do livro de movimentações não têm o câmbio da compra: o custo e o resultado em reais ficam não informados até um ajuste com câmbio.");
        }
        notes.add("Resultado não realizado sem corretagem, custos e impostos que você não incluiu no preço médio. O resultado realizado das vendas está no livro de movimentações.");
        return new Summary(List.copyOf(lines), fx == null ? null : new FxView(fx.brlPerUsd(), fx.date(), fx.source(), fx.stale()), lines.isEmpty() ? null : totalCurrent,
                lines.isEmpty() ? null : totalCost, withoutBrl, List.copyOf(weights), List.copyOf(notes));
    }
}
