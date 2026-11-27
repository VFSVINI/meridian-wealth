package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.testsupport.PostgresDb;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class JdbcRefreshTokenStoreTest extends RefreshTokenStoreContract {
    private final JdbcTemplate jdbc = PostgresDb.jdbc();
    private final JdbcRefreshTokenStore store = new JdbcRefreshTokenStore(jdbc, new DataSourceTransactionManager(PostgresDb.dataSource()));
    private final JdbcUserStore users = new JdbcUserStore(jdbc);

    @BeforeEach
    void clean() { PostgresDb.reset(); }

    @Override
    RefreshTokenStore store() { return store; }

    @Override
    UUID newUser() {
        UUID id = UUID.randomUUID();
        users.createIfAbsent(new UserAccount(id, id + "@exemplo.com", "U", null, AuthProvider.DEMO, null, null, Instant.now(), null));
        return id;
    }

    @Test void onlyTheHashIsStoredAndExpiredRowsAreSweptOnSave() {
        UUID u = newUser();
        store.save(new RefreshTokenStore.Entry(hash("vivo"), "f1", u, Instant.now().plusSeconds(600)));
        store.save(new RefreshTokenStore.Entry(hash("velho"), "f2", u, Instant.now().minusSeconds(3600)));
        store.save(new RefreshTokenStore.Entry(hash("novo"), "f3", u, Instant.now().plusSeconds(600)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).as("o expirado saiu na gravação seguinte").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT max(length(token_hash)) FROM refresh_tokens", Integer.class)).isEqualTo(64);
    }

    /** Transações que falham com deadlock nas primeiras {@code failures} aberturas e depois funcionam, como o PostgreSQL faz com a perdedora de uma corrida. */
    private static org.springframework.transaction.PlatformTransactionManager flaky(int failures) {
        var real = new DataSourceTransactionManager(PostgresDb.dataSource());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        return new org.springframework.transaction.PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition d) {
                if (calls.getAndIncrement() < failures) throw new org.springframework.dao.CannotAcquireLockException("ERROR: deadlock detected");
                return real.getTransaction(d);
            }

            @Override
            public void commit(org.springframework.transaction.TransactionStatus s) { real.commit(s); }

            @Override
            public void rollback(org.springframework.transaction.TransactionStatus s) { real.rollback(s); }
        };
    }

    @Test void aDeadlockOnRotationIsRetriedInsteadOfSurfacingAsAnError() {
        UUID u = newUser();
        store.save(new RefreshTokenStore.Entry(hash("a"), "fam", u, Instant.now().plusSeconds(600)));
        JdbcRefreshTokenStore once = new JdbcRefreshTokenStore(jdbc, flaky(2));
        assertThat(once.rotate(hash("a"), hash("b"), Instant.now().plusSeconds(900), Instant.now())).as("duas falhas de deadlock e a terceira tentativa vale").isInstanceOf(RefreshTokenStore.Rotation.Rotated.class);
        assertThat(store.rotate(hash("a"), hash("c"), Instant.now().plusSeconds(900), Instant.now())).as("e o token foi consumido uma única vez").isInstanceOf(RefreshTokenStore.Rotation.Reused.class);
    }

    @Test void aPersistentDeadlockIsNotSwallowedForever() {
        JdbcRefreshTokenStore never = new JdbcRefreshTokenStore(jdbc, flaky(Integer.MAX_VALUE));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> never.rotate(hash("x"), hash("y"), Instant.now().plusSeconds(900), Instant.now()))
                .isInstanceOf(org.springframework.dao.PessimisticLockingFailureException.class);
    }

    /**
     * Prova da serialização: uma transação segura o lock consultivo do token; a rotação desse mesmo token espera por ele (nenhuma linha é tocada antes)
     * e só termina depois que o lock é solto. Rotações de OUTRO token não esperam. É isto que impede os perdedores de uma corrida de apagarem a
     * mesma família ao mesmo tempo e se travarem (deadlock medido no CI).
     */
    @Test void rotationsOfTheSameTokenQueueBehindEachOtherWhileOtherTokensDoNot() throws Exception {
        UUID u = newUser();
        store.save(new RefreshTokenStore.Entry(hash("mesmo"), "fam1", u, Instant.now().plusSeconds(600)));
        store.save(new RefreshTokenStore.Entry(hash("outro"), "fam2", u, Instant.now().plusSeconds(600)));
        var tm = new DataSourceTransactionManager(PostgresDb.dataSource());
        var holder = new org.springframework.transaction.support.TransactionTemplate(tm);
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
        var exec = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var holding = exec.submit(() -> holder.executeWithoutResult(st -> {
                jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, hash("mesmo"));
                locked.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var waiting = exec.submit(() -> store.rotate(hash("mesmo"), hash("m2"), Instant.now().plusSeconds(900), Instant.now()));
            assertThat(store.rotate(hash("outro"), hash("o2"), Instant.now().plusSeconds(900), Instant.now())).as("outro token não espera").isInstanceOf(RefreshTokenStore.Rotation.Rotated.class);
            Thread.sleep(700);
            assertThat(waiting.isDone()).as("a rotação do mesmo token espera o lock").isFalse();
            release.countDown();
            holding.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(waiting.get(10, java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(RefreshTokenStore.Rotation.Rotated.class);
        } finally {
            release.countDown();
            exec.shutdownNow();
        }
    }
}
