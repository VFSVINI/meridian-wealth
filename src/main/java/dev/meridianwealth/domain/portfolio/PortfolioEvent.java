package dev.meridianwealth.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fatos que aconteceram na carteira, publicados depois que a entrada do livro foi gravada com sucesso (nunca antes do commit e nunca
 * para uma repetição idempotente). Cada evento traz só identificadores e o que o fato produziu; quem assina decide o que fazer.
 */
public sealed interface PortfolioEvent {
    UUID userId();

    UUID entryId();

    String assetKey();

    Instant occurredAt();

    /** Nome curto e estável do tipo, usado em métricas. */
    String type();

    record PositionOpened(UUID userId, UUID entryId, String assetKey, Instant occurredAt, LocalDate tradeDate) implements PortfolioEvent {
        @Override public String type() { return "position_opened"; }
    }

    record PurchaseRecorded(UUID userId, UUID entryId, String assetKey, Instant occurredAt, LocalDate tradeDate, BigDecimal quantity,
                            BigDecimal averagePriceAfter) implements PortfolioEvent {
        @Override public String type() { return "purchase_recorded"; }
    }

    /** {@code realizedPnl} na moeda da posição; {@code realizedPnlBrl} nulo quando o câmbio não foi registrado. */
    record SaleRecorded(UUID userId, UUID entryId, String assetKey, Instant occurredAt, LocalDate tradeDate, BigDecimal quantity, BigDecimal realizedPnl,
                        BigDecimal realizedPnlBrl, boolean closedPosition) implements PortfolioEvent {
        @Override public String type() { return "sale_recorded"; }
    }

    record PositionAdjusted(UUID userId, UUID entryId, String assetKey, Instant occurredAt, LocalDate tradeDate) implements PortfolioEvent {
        @Override public String type() { return "position_adjusted"; }
    }

    record PositionClosed(UUID userId, UUID entryId, String assetKey, Instant occurredAt, LocalDate tradeDate) implements PortfolioEvent {
        @Override public String type() { return "position_closed"; }
    }
}
