package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PortfolioLedger.Change;
import dev.meridianwealth.application.port.PortfolioLedger.Op;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.EntryType;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import dev.meridianwealth.testsupport.PostgresDb;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** O mesmo contrato contra o PostgreSQL de verdade, mais o que só o banco garante: atomicidade, gatilho de imutabilidade e restrições. */
class JdbcPortfolioLedgerTest extends PortfolioLedgerContract {
    private final JdbcTemplate jdbc = PostgresDb.jdbc();
    private final JdbcPositionStore positions = new JdbcPositionStore(jdbc);
    private final JdbcPortfolioLedger ledger = new JdbcPortfolioLedger(positions, jdbc, new DataSourceTransactionManager(PostgresDb.dataSource()));
    private final JdbcUserStore users = new JdbcUserStore(jdbc);

    @BeforeEach
    void clean() { PostgresDb.reset(); }

    @Override
    PortfolioLedger ledger() { return ledger; }

    @Override
    PositionStore positions() { return positions; }

    @Override
    UUID newUser() {
        UUID id = UUID.randomUUID();
        users.createIfAbsent(new UserAccount(id, id + "@exemplo.com", "U", null, AuthProvider.DEMO, null, null, Instant.now(), null));
        return id;
    }

    @Test void anEntryNeverChangesAfterItIsWritten() {
        UUID u = newUser();
        open(u, "PETR4", null);
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET fees = 99 WHERE user_id = ?", u)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("só acrescentado");
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET realized_pnl = 1 WHERE user_id = ?", u)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT fees FROM ledger_entries WHERE user_id = ?", BigDecimal.class, u)).isEqualByComparingTo("0");
    }

    @Test void deletingTheUserRemovesItsLedgerByCascade() {
        UUID u = newUser();
        open(u, "PETR4", null);
        jdbc.update("DELETE FROM users WHERE id = ?", u);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class)).isZero();
    }

    @Test void aFailedEntryRollsBackThePositionChange() {
        UUID u = newUser();
        Position p = open(u, "PETR4", null);
        Position next = p.purchase(d("1"), d("31"), BigDecimal.ZERO, null, NOW);
        // quantidade depois negativa: o CHECK do livro recusa a entrada depois que a posição já foi alterada na mesma transação
        LedgerEntry bad = new LedgerEntry(UUID.randomUUID(), u, p.id(), p.asset(), EntryType.BUY, java.time.LocalDate.parse("2026-03-02"), d("1"), d("31"), BigDecimal.ZERO, Currency.BRL,
                null, null, null, d("-1"), d("31"), d("31"), null, null, 1, null, null, NOW);
        assertThatThrownBy(() -> ledger.commit(new Change(Op.UPDATE, next, p.id(), 0, bad))).isInstanceOf(DataIntegrityViolationException.class);
        Position stored = positions.find(p.id(), u).orElseThrow();
        assertThat(stored.version()).as("a posição não mudou").isZero();
        assertThat(stored.quantity()).isEqualByComparingTo("10");
        assertThat(ledger.all(u)).as("e nenhuma entrada ficou").hasSize(1);
    }

    @Test void theDatabaseRefusesInconsistentEntries() {
        UUID u = newUser();
        Position p = open(u, "PETR4", null);
        String base = "INSERT INTO ledger_entries(id, user_id, position_id, asset_key, asset_type, label, entry_type, trade_date, quantity, unit_price, fees, currency, quantity_after, "
                + "realized_pnl, version_after, idempotency_key, request_hash, created_at) VALUES (gen_random_uuid(), ?, ?, 'PETR4', 'ACAO', 'x', ?, current_date, ?, ?, ?, ?, 1, ?, 0, ?, ?, now())";
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "BUY", 1, 1, 0, "BRL", 5, null, null)).as("só a venda tem resultado realizado").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "SELL", 1, 1, 0, "BRL", null, null, null)).as("a venda precisa de resultado").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "BUY", 0, 1, 0, "BRL", null, null, null)).as("quantidade positiva").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "BUY", 1, 1, -1, "BRL", null, null, null)).as("taxas não negativas").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "BUY", 1, 1, 0, "EUR", null, null, null)).as("só BRL e USD").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "XYZ", 1, 1, 0, "BRL", null, null, null)).as("tipo válido").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, u, p.id(), "BUY", 1, 1, 0, "BRL", null, "chave", null)).as("chave sem hash").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(base, UUID.randomUUID(), p.id(), "BUY", 1, 1, 0, "BRL", null, null, null)).as("dono inexistente").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class)).isEqualTo(1);
    }
}
