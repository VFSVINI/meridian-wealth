package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.portfolio.Position;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Posições em memória, para a execução sem Docker (as posições somem no reinício, como as contas). Mesma semântica da versão JDBC:
 * unicidade por usuário e ativo e bloqueio otimista pela versão, com um monitor único tornando cada operação atômica.
 */
public class InMemoryPositionStore implements PositionStore {
    private final Map<UUID, Position> byId = new HashMap<>();

    @Override
    public synchronized List<Position> findByUser(UUID userId) {
        return byId.values().stream().filter(p -> p.userId().equals(userId)).sorted(Comparator.comparing(Position::createdAt).thenComparing(Position::id)).toList();
    }

    @Override
    public synchronized Optional<Position> find(UUID id, UUID userId) {
        return Optional.ofNullable(byId.get(id)).filter(p -> p.userId().equals(userId));
    }

    @Override
    public synchronized int countByUser(UUID userId) { return (int) byId.values().stream().filter(p -> p.userId().equals(userId)).count(); }

    @Override
    public synchronized boolean insertIfAbsent(Position position) {
        boolean exists = byId.values().stream().anyMatch(p -> p.userId().equals(position.userId()) && p.asset().key().equals(position.asset().key()));
        if (exists || byId.containsKey(position.id())) return false;
        byId.put(position.id(), position.withVersion(0));
        return true;
    }

    @Override
    public synchronized Optional<Position> update(Position next, long expectedVersion) {
        Position current = byId.get(next.id());
        if (current == null || !current.userId().equals(next.userId()) || current.version() != expectedVersion) return Optional.empty();
        Position saved = next.withVersion(expectedVersion + 1);
        byId.put(saved.id(), saved);
        return Optional.of(saved);
    }

    @Override
    public synchronized boolean delete(UUID id, UUID userId, long expectedVersion) {
        Position current = byId.get(id);
        if (current == null || !current.userId().equals(userId) || current.version() != expectedVersion) return false;
        byId.remove(id);
        return true;
    }
}
