package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PortfolioLedger.Change;
import dev.meridianwealth.application.port.PortfolioLedger.Commit;
import dev.meridianwealth.application.port.PortfolioLedger.Op;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.AssetRef;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.EntryType;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Contrato do {@link PortfolioLedger}, igual para a versão em memória e para a JDBC (PostgreSQL de verdade): mudança da posição e entrada
 * do livro juntas, só acrescenta, {@code Idempotency-Key} única por usuário, nenhuma entrada para uma escrita que perdeu a corrida.
 */
abstract class PortfolioLedgerContract {
    static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    abstract PortfolioLedger ledger();

    abstract PositionStore positions();

    abstract UUID newUser();

    static BigDecimal d(String v) { return new BigDecimal(v); }

    static AssetRef asset(String symbol) { return new AssetRef(symbol, AssetType.ACAO, symbol + " teste"); }

    Position open(UUID user, String symbol, String key) {
        Position p = Position.open(user, asset(symbol), d("10"), d("30"), Currency.BRL, null, NOW);
        Commit c = ledger().commit(new Change(Op.OPEN, p, p.id(), 0, entry(user, p, EntryType.OPENING, "2026-03-01", key, 0, null)));
        assertThat(c).isInstanceOf(Commit.Committed.class);
        return p;
    }

    static LedgerEntry entry(UUID user, Position after, EntryType type, String date, String key, long versionAfter, BigDecimal realized) {
        return new LedgerEntry(UUID.randomUUID(), user, after.id(), after.asset(), type, LocalDate.parse(date), after.quantity(), after.averagePrice(), BigDecimal.ZERO, Currency.BRL, null, null, null,
                after.quantity(), after.averagePrice(), after.averageCostBrl(), realized, realized, versionAfter, key, key == null ? null : "h".repeat(64), NOW);
    }

    Commit buy(UUID user, Position current, String key, String date) {
        Position next = current.purchase(d("1"), d("31"), BigDecimal.ZERO, null, NOW);
        return ledger().commit(new Change(Op.UPDATE, next, current.id(), current.version(), entry(user, next.withVersion(current.version() + 1), EntryType.BUY, date, key, current.version() + 1, null)));
    }

    @Test void openUpdateAndDeleteWriteTheEntriesInOrderAndKeepThePositionInSync() {
        UUID u = newUser();
        Position p = open(u, "PETR4", null);
        Commit.Committed second = (Commit.Committed) buy(u, p, null, "2026-03-02");
        assertThat(second.position().version()).isEqualTo(1);
        assertThat(positions().find(p.id(), u).orElseThrow().quantity()).isEqualByComparingTo("11");
        Position now = positions().find(p.id(), u).orElseThrow();
        LedgerEntry off = entry(u, now, EntryType.WRITE_OFF, "2026-03-03", null, now.version(), null);
        Commit c = ledger().commit(new Change(Op.DELETE, null, now.id(), now.version(), off));
        assertThat(c).isInstanceOfSatisfying(Commit.Committed.class, k -> assertThat(k.position()).isNull());
        assertThat(positions().find(p.id(), u)).isEmpty();
        assertThat(ledger().all(u)).extracting(LedgerEntry::type).containsExactly(EntryType.OPENING, EntryType.BUY, EntryType.WRITE_OFF);
        assertThat(ledger().lastTradeDate(u, "PETR4")).contains(LocalDate.parse("2026-03-03"));
        assertThat(ledger().lastTradeDate(u, "OUTRO")).isEmpty();
    }

    @Test void deleteAllOfErasesOnlyThatUsersPositionsAndLedger() {
        UUID a = newUser();
        UUID b = newUser();
        Position pa = open(a, "PETR4", "k-a");
        buy(a, pa, null, "2026-03-02");
        open(a, "VALE3", null);
        open(b, "PETR4", "k-b");
        assertThat(ledger().deleteAllOf(a)).isEqualTo(3);
        assertThat(positions().deleteAllOf(a)).isEqualTo(2);
        assertThat(ledger().all(a)).isEmpty();
        assertThat(positions().findByUser(a)).isEmpty();
        assertThat(ledger().findByKey(a, "k-a")).as("a chave de idempotência some junto").isEmpty();
        assertThat(ledger().all(b)).as("o outro usuário não é tocado").hasSize(1);
        assertThat(positions().findByUser(b)).hasSize(1);
        assertThat(ledger().deleteAllOf(a)).isZero();
    }

    @Test void filtersAndPaginationScopeTheListingToTheUser() {
        UUID a = newUser();
        UUID b = newUser();
        Position pa = open(a, "PETR4", null);
        buy(a, pa, null, "2026-03-05");
        open(a, "VALE3", null);
        open(b, "PETR4", null);
        assertThat(ledger().entries(a, null, null, null, 50, 0)).hasSize(3);
        assertThat(ledger().entries(a, "PETR4", null, null, 50, 0)).hasSize(2);
        assertThat(ledger().entries(a, null, LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-31"), 50, 0)).hasSize(1);
        assertThat(ledger().entries(a, null, null, null, 2, 1)).hasSize(2);
        assertThat(ledger().entries(b, null, null, null, 50, 0)).hasSize(1);
        assertThat(ledger().all(b)).hasSize(1);
        assertThat(ledger().entries(a, "PETR4", null, null, 50, 0).get(0).userId()).isEqualTo(a);
    }

    @Test void aRepeatedKeyIsADuplicateAndChangesNothing() {
        UUID u = newUser();
        Position p = open(u, "PETR4", "k-abertura");
        assertThat(ledger().findByKey(u, "k-abertura")).isPresent();
        assertThat(ledger().findByKey(u, "outra")).isEmpty();
        assertThat(ledger().findByKey(u, null)).isEmpty();
        Commit first = buy(u, p, "k-compra", "2026-03-02");
        assertThat(first).isInstanceOf(Commit.Committed.class);
        Commit again = buy(u, p, "k-compra", "2026-03-02");
        assertThat(again).isInstanceOfSatisfying(Commit.Duplicate.class, dup -> assertThat(dup.existing().idempotencyKey()).isEqualTo("k-compra"));
        assertThat(positions().find(p.id(), u).orElseThrow().quantity()).as("a repetição não alterou a posição").isEqualByComparingTo("11");
        assertThat(ledger().all(u)).hasSize(2);
    }

    @Test void theSameKeyOfAnotherUserIsNotADuplicate() {
        UUID a = newUser();
        UUID b = newUser();
        open(a, "PETR4", "chave");
        open(b, "PETR4", "chave");
        assertThat(ledger().findByKey(a, "chave").orElseThrow().userId()).isEqualTo(a);
        assertThat(ledger().findByKey(b, "chave").orElseThrow().userId()).isEqualTo(b);
    }

    @Test void aLostRaceLeavesNoEntry() {
        UUID u = newUser();
        Position p = open(u, "PETR4", null);
        assertThat(buy(u, p, null, "2026-03-02")).isInstanceOf(Commit.Committed.class);
        // segunda compra lida na versão antiga: perde
        assertThat(buy(u, p, null, "2026-03-03")).isInstanceOf(Commit.VersionConflict.class);
        // abrir de novo o mesmo ativo: já existe
        Position again = Position.open(u, asset("PETR4"), d("1"), d("1"), Currency.BRL, null, NOW);
        assertThat(ledger().commit(new Change(Op.OPEN, again, again.id(), 0, entry(u, again, EntryType.OPENING, "2026-03-04", null, 0, null)))).isInstanceOf(Commit.PositionExists.class);
        Position now = positions().find(p.id(), u).orElseThrow();
        assertThat(ledger().commit(new Change(Op.DELETE, null, p.id(), 0, entry(u, now, EntryType.WRITE_OFF, "2026-03-05", null, 0, null)))).as("exclusão com versão antiga").isInstanceOf(Commit.VersionConflict.class);
        assertThat(ledger().all(u)).as("só as duas entradas que valeram").hasSize(2);
    }

    @Test void concurrentCommitsWithTheSameKeyApplyOnce() throws Exception {
        UUID u = newUser();
        for (int round = 0; round < 10; round++) {
            Position p = open(u, "R" + round, null);
            String key = "corrida-" + round;
            int threads = 8;
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger committed = new AtomicInteger();
            AtomicInteger duplicates = new AtomicInteger();
            AtomicInteger other = new AtomicInteger();
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    fs.add(pool.submit(() -> {
                        go.await();
                        Commit c = buy(u, p, key, "2026-03-02");
                        if (c instanceof Commit.Committed) committed.incrementAndGet();
                        else if (c instanceof Commit.Duplicate) duplicates.incrementAndGet();
                        else other.incrementAndGet();
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : fs) f.get();
            }
            assertThat(committed.get()).as("rodada %d: uma só gravação", round).isEqualTo(1);
            assertThat(duplicates.get() + other.get()).isEqualTo(threads - 1);
            assertThat(other.get()).as("quem perde a corrida da mesma chave recebe Duplicate, não conflito").isZero();
            assertThat(ledger().entries(u, "R" + round, null, null, 50, 0)).hasSize(2);
            assertThat(positions().find(p.id(), u).orElseThrow().quantity()).isEqualByComparingTo("11");
        }
    }

    @Test void concurrentUpdatesOnTheSameVersionWriteExactlyOneEntry() throws Exception {
        UUID u = newUser();
        for (int round = 0; round < 10; round++) {
            Position p = open(u, "V" + round, null);
            int threads = 8;
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger committed = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    int n = i;
                    fs.add(pool.submit(() -> {
                        go.await();
                        Commit c = buy(u, p, "chave-" + System.nanoTime() + "-" + n, "2026-03-02");
                        if (c instanceof Commit.Committed) committed.incrementAndGet();
                        else if (c instanceof Commit.VersionConflict) conflicts.incrementAndGet();
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : fs) f.get();
            }
            assertThat(committed.get()).as("rodada %d", round).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(threads - 1);
            assertThat(ledger().entries(u, "V" + round, null, null, 50, 0)).as("uma entrada por escrita que venceu").hasSize(2);
            assertThat(positions().find(p.id(), u).orElseThrow().version()).isEqualTo(1);
        }
    }
}
