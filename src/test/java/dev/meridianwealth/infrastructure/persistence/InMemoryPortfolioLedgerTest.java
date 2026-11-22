package dev.meridianwealth.infrastructure.persistence;

import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PositionStore;
import java.util.UUID;

class InMemoryPortfolioLedgerTest extends PortfolioLedgerContract {
    private final InMemoryPositionStore positions = new InMemoryPositionStore();
    private final InMemoryPortfolioLedger ledger = new InMemoryPortfolioLedger(positions);

    @Override
    PortfolioLedger ledger() { return ledger; }

    @Override
    PositionStore positions() { return positions; }

    @Override
    UUID newUser() { return UUID.randomUUID(); }
}
