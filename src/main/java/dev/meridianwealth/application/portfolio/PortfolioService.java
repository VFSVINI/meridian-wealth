package dev.meridianwealth.application.portfolio;

import dev.meridianwealth.application.BrazilRatesService;
import dev.meridianwealth.application.DataStatus;
import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.TickBatch;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.IsolationGuard;
import dev.meridianwealth.application.portfolio.PortfolioException.Code;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.AssetCategory;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.RateSeries;
import dev.meridianwealth.domain.portfolio.AssetRef;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.Position;
import dev.meridianwealth.domain.portfolio.PositionValuation;
import dev.meridianwealth.domain.portfolio.PositionValuation.FxRate;
import dev.meridianwealth.domain.portfolio.PositionValuation.MarketPrice;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso da carteira do próprio usuário: abrir posição, compra (preço médio ponderado), venda, substituição manual, exclusão e
 * listagem valorizada com dado real. Toda operação parte do {@link AuthPrincipal} (o dono vem do token validado) e passa pelo
 * {@link IsolationGuard}; a escrita usa o bloqueio otimista da porta, então uma alteração concorrente nunca se perde em silêncio:
 * quem perde a corrida recebe {@code VERSION_CONFLICT} e relê.
 */
public final class PortfolioService {
    public static final int MAX_POSITIONS = 200;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record OpenRequest(String symbol, AssetType type, String label, BigDecimal quantity, BigDecimal averagePrice, Currency currency) {}

    public record Line(PositionValuation.Result valuation, String assetKey, String symbol, AssetType type) {}

    public record ClassWeight(AssetCategory category, BigDecimal percent) {}

    public record FxView(BigDecimal brlPerUsd, java.time.LocalDate date, String source, boolean stale) {}

    public record Summary(List<Line> lines, FxView fx, BigDecimal totalCurrentValueBrl, BigDecimal totalCostBasisBrl, int positionsWithoutBrlValue,
                          List<ClassWeight> byClass, List<String> notes) {}

    public record SaleResult(Position position, boolean closed) {}

    private final PositionStore store;
    private final MarketService market;
    private final BrazilRatesService rates;
    private final Clock clock;

    public PortfolioService(PositionStore store, MarketService market, BrazilRatesService rates, Clock clock) {
        this.store = store;
        this.market = market;
        this.rates = rates;
        this.clock = clock;
    }

    public Summary list(AuthPrincipal who) {
        List<Position> positions = store.findByUser(who.userId());
        for (Position p : positions) IsolationGuard.requireOwner(p.userId(), who);
        Map<String, Instrument> universe = new HashMap<>();
        market.universe().forEach(i -> universe.put(i.symbol(), i));
        Map<String, MarketPrice> prices = positions.stream().anyMatch(p -> p.asset().symbol() != null) ? quotes(universe) : Map.of();
        FxRate fx = fx();
        List<Line> lines = new ArrayList<>();
        for (Position p : positions) {
            MarketPrice price = p.asset().symbol() == null ? null : prices.get(p.asset().symbol());
            lines.add(new Line(PositionValuation.value(p, price, fx), p.asset().key(), p.asset().symbol(), p.asset().type()));
        }
        return summarize(lines, fx);
    }

    public Position open(AuthPrincipal who, OpenRequest r) {
        try {
            AssetRef asset;
            Currency currency;
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
            if (store.countByUser(who.userId()) >= MAX_POSITIONS) {
                throw new PortfolioException(Code.LIMIT_REACHED, "Limite de " + MAX_POSITIONS + " posições por carteira atingido");
            }
            Position p = Position.open(who.userId(), asset, r.quantity(), r.averagePrice(), currency, clock.instant());
            if (!store.insertIfAbsent(p)) {
                throw new PortfolioException(Code.POSITION_EXISTS, "Você já tem uma posição em " + asset.label() + ". Registre uma compra, uma venda ou substitua a posição");
            }
            return p;
        } catch (IllegalArgumentException e) {
            throw new PortfolioException(Code.INVALID_INPUT, e.getMessage());
        }
    }

    /** Compra: o preço médio passa a ser o ponderado pelo custo. */
    public Position purchase(AuthPrincipal who, UUID id, BigDecimal quantity, BigDecimal unitPrice, Long expectedVersion) {
        Position current = load(who, id, expectedVersion);
        Position next = apply(() -> current.purchase(quantity, unitPrice, clock.instant()));
        return store.update(next, current.version()).orElseThrow(PortfolioService::conflict);
    }

    /** Venda: reduz a quantidade e mantém o preço médio; zerar fecha a posição. */
    public SaleResult sale(AuthPrincipal who, UUID id, BigDecimal quantity, Long expectedVersion) {
        Position current = load(who, id, expectedVersion);
        Optional<Position> next = apply(() -> current.sale(quantity, clock.instant()));
        if (next.isEmpty()) {
            if (!store.delete(id, who.userId(), current.version())) throw conflict();
            return new SaleResult(current, true);
        }
        return new SaleResult(store.update(next.get(), current.version()).orElseThrow(PortfolioService::conflict), false);
    }

    /** Substituição manual da quantidade e do preço médio (por exemplo, para importar o que a corretora mostra). */
    public Position replace(AuthPrincipal who, UUID id, BigDecimal quantity, BigDecimal averagePrice, Long expectedVersion) {
        if (expectedVersion == null) throw new PortfolioException(Code.INVALID_INPUT, "Informe expectedVersion para substituir a posição");
        Position current = load(who, id, expectedVersion);
        Position next = apply(() -> current.declare(quantity, averagePrice, clock.instant()));
        return store.update(next, current.version()).orElseThrow(PortfolioService::conflict);
    }

    public void delete(AuthPrincipal who, UUID id, Long expectedVersion) {
        Position current = load(who, id, expectedVersion);
        if (!store.delete(id, who.userId(), current.version())) throw conflict();
    }

    private Position load(AuthPrincipal who, UUID id, Long expectedVersion) {
        if (id == null) throw new PortfolioException(Code.NOT_FOUND, "Posição não encontrada");
        Position p = store.find(id, who.userId()).orElseThrow(() -> new PortfolioException(Code.NOT_FOUND, "Posição não encontrada"));
        IsolationGuard.requireOwner(p.userId(), who);
        if (expectedVersion != null && expectedVersion != p.version()) throw conflict();
        return p;
    }

    private static <T> T apply(java.util.function.Supplier<T> op) {
        try {
            return op.get();
        } catch (IllegalArgumentException e) {
            throw new PortfolioException(Code.INVALID_INPUT, e.getMessage());
        }
    }

    private static PortfolioException conflict() {
        return new PortfolioException(Code.VERSION_CONFLICT, "A posição foi alterada por outra operação. Releia a carteira e tente de novo");
    }

    /** Cotações reais do painel agora: só ativos com dado e em moeda suportada; sem cotação, a posição fica avaliada pelo custo. */
    private Map<String, MarketPrice> quotes(Map<String, Instrument> universe) {
        Map<String, MarketPrice> out = new HashMap<>();
        for (TickBatch.Tick t : market.overview().ticks()) {
            if (t.status() == DataStatus.UNAVAILABLE || t.candle() == null) continue;
            Instrument inst = universe.get(t.symbol());
            Optional<Currency> cur = inst == null ? Optional.empty() : Currency.fromCode(inst.currency());
            if (cur.isEmpty() || !Double.isFinite(t.candle().close()) || t.candle().close() <= 0) continue;
            out.put(t.symbol(), new MarketPrice(BigDecimal.valueOf(t.candle().close()), cur.get(), t.source(), java.time.Instant.ofEpochMilli(t.quoteTime()),
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
            notes.add("Valores em reais de posições em dólar usam o " + fx.source() + " de " + fx.date() + (fx.stale() ? " (desatualizado)" : "")
                    + ". O custo em reais usa o câmbio de hoje, não o da compra, e por isso não há resultado em reais.");
        } else if (lines.stream().anyMatch(l -> l.valuation().position().currency() == Currency.USD)) {
            notes.add("Sem PTAX do Banco Central agora: posições em dólar ficam sem valor em reais (não informado).");
        }
        notes.add("Resultado não realizado na moeda da posição, sem corretagem, custos e impostos que você não incluiu no preço médio.");
        return new Summary(List.copyOf(lines), fx == null ? null : new FxView(fx.brlPerUsd(), fx.date(), fx.source(), fx.stale()), lines.isEmpty() ? null : totalCurrent,
                lines.isEmpty() ? null : totalCost, withoutBrl, List.copyOf(weights), List.copyOf(notes));
    }
}
