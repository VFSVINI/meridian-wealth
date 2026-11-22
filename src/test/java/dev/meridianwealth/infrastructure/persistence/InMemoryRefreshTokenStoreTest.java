package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.infrastructure.security.InMemoryRefreshTokenStore;
import java.util.UUID;

class InMemoryRefreshTokenStoreTest extends RefreshTokenStoreContract {
    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Override
    RefreshTokenStore store() { return store; }

    @Override
    UUID newUser() { return UUID.randomUUID(); }
}
