package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.PositionStore;
import java.util.UUID;

class InMemoryPositionStoreTest extends PositionStoreContract {
    private final InMemoryPositionStore store = new InMemoryPositionStore();

    @Override
    PositionStore store() { return store; }

    @Override
    UUID newUser() { return UUID.randomUUID(); }
}
