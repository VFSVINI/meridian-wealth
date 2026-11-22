package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.infrastructure.security.InMemoryUserStore;

class InMemoryUserStoreTest extends UserStoreContract {
    private final InMemoryUserStore store = new InMemoryUserStore();

    @Override
    UserStore store() { return store; }
}
