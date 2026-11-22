package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Livro em memória, para a execução sem Docker: mesma semântica da versão JDBC (mudança da posição e gravação da entrada juntas, só
 * acrescenta, {@code Idempotency-Key} única por usuário) com um monitor único tornando cada {@link #commit} atômico.
 */
public class InMemoryPortfolioLedger implements PortfolioLedger {
    private final InMemoryPositionStore positions;
    private final List<LedgerEntry> entries = new ArrayList<>();

    public InMemoryPortfolioLedger(InMemoryPositionStore positions) { this.positions = positions; }

    @Override
    public synchronized int deleteAllOf(UUID userId) {
        int before = entries.size();
        entries.removeIf(e -> e.userId().equals(userId));
        return before - entries.size();
    }

    @Override
    public synchronized Commit commit(Change change) {
        LedgerEntry e = change.entry();
        Optional<LedgerEntry> existing = findByKey(e.userId(), e.idempotencyKey());
        if (existing.isPresent()) return new Commit.Duplicate(existing.get());
        Position saved = null;
        switch (change.op()) {
            case OPEN -> {
                if (!positions.insertIfAbsent(change.next())) return new Commit.PositionExists();
                saved = change.next();
            }
            case UPDATE -> {
                Optional<Position> updated = positions.update(change.next(), change.expectedVersion());
                if (updated.isEmpty()) return new Commit.VersionConflict();
                saved = updated.get();
            }
            case DELETE -> {
                if (!positions.delete(change.positionId(), e.userId(), change.expectedVersion())) return new Commit.VersionConflict();
            }
            default -> throw new IllegalStateException("Operação desconhecida");
        }
        entries.add(e);
        return new Commit.Committed(saved, e);
    }

    @Override
    public synchronized Optional<LedgerEntry> findByKey(UUID userId, String key) {
        if (key == null) return Optional.empty();
        return entries.stream().filter(e -> e.userId().equals(userId) && key.equals(e.idempotencyKey())).findFirst();
    }

    @Override
    public synchronized List<LedgerEntry> entries(UUID userId, String assetKey, LocalDate from, LocalDate to, int limit, int offset) {
        return entries.stream().filter(e -> e.userId().equals(userId) && (assetKey == null || e.asset().key().equals(assetKey))
                && (from == null || !e.tradeDate().isBefore(from)) && (to == null || !e.tradeDate().isAfter(to))).skip(offset).limit(limit).toList();
    }

    @Override
    public synchronized List<LedgerEntry> all(UUID userId) { return entries.stream().filter(e -> e.userId().equals(userId)).toList(); }

    @Override
    public synchronized Optional<LocalDate> lastTradeDate(UUID userId, String assetKey) {
        return entries.stream().filter(e -> e.userId().equals(userId) && e.asset().key().equals(assetKey)).map(LedgerEntry::tradeDate).max(LocalDate::compareTo);
    }
}
