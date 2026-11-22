package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.RefreshTokenStore.Entry;
import dev.meridianwealth.application.port.RefreshTokenStore.Rotation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Contrato do {@link RefreshTokenStore}: rotação atômica, detecção de reuso e revogação, iguais em memória e no PostgreSQL. */
abstract class RefreshTokenStoreContract {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    abstract RefreshTokenStore store();

    abstract UUID newUser();

    static String hash(String s) { return String.format("%64s", s).replace(' ', '0'); }

    @Test void rotationKeepsTheFamilyAndTheUser() {
        UUID u = newUser();
        store().save(new Entry(hash("1"), "fam", u, NOW.plusSeconds(100)));
        Rotation r = store().rotate(hash("1"), hash("2"), NOW.plusSeconds(200), NOW);
        assertThat(r).isInstanceOf(Rotation.Rotated.class);
        Entry previous = ((Rotation.Rotated) r).previous();
        assertThat(previous.familyId()).isEqualTo("fam");
        assertThat(previous.userId()).isEqualTo(u);
        assertThat(previous.hash()).isEqualTo(hash("1"));
        assertThat(store().rotate(hash("2"), hash("3"), NOW.plusSeconds(300), NOW)).as("o token novo gira de novo").isInstanceOf(Rotation.Rotated.class);
        assertThat(store().rotate(hash("desconhecido"), hash("x"), NOW.plusSeconds(300), NOW)).isInstanceOf(Rotation.Invalid.class);
    }

    @Test void aConsumedTokenShownAgainRevokesTheWholeFamily() {
        UUID u = newUser();
        store().save(new Entry(hash("1"), "fam", u, NOW.plusSeconds(100)));
        store().rotate(hash("1"), hash("2"), NOW.plusSeconds(200), NOW);
        assertThat(store().rotate(hash("1"), hash("9"), NOW.plusSeconds(200), NOW)).isEqualTo(new Rotation.Reused("fam", u));
        assertThat(store().rotate(hash("2"), hash("3"), NOW.plusSeconds(300), NOW)).as("o token vivo da família caiu junto").isInstanceOf(Rotation.Invalid.class);
    }

    @Test void expiredTokensAreInvalidEvenWhenAlreadyUsed() {
        UUID u = newUser();
        store().save(new Entry(hash("old"), "f0", u, NOW.minusSeconds(1)));
        assertThat(store().rotate(hash("old"), hash("x"), NOW.plusSeconds(10), NOW)).isInstanceOf(Rotation.Invalid.class);
        assertThat(store().rotate(hash("old"), hash("x"), NOW.plusSeconds(10), NOW)).as("já removido").isInstanceOf(Rotation.Invalid.class);
    }

    @Test void revokingByLogoutOrByUserOnlyAffectsTheTarget() {
        UUID u = newUser();
        UUID other = newUser();
        store().save(new Entry(hash("a"), "fa", u, NOW.plusSeconds(100)));
        store().save(new Entry(hash("b"), "fb", u, NOW.plusSeconds(100)));
        store().save(new Entry(hash("c"), "fc", other, NOW.plusSeconds(100)));
        store().revokeFamilyOf(hash("a"));
        store().revokeFamilyOf(hash("desconhecido"));
        assertThat(store().rotate(hash("a"), hash("a2"), NOW.plusSeconds(10), NOW)).isInstanceOf(Rotation.Invalid.class);
        store().revokeAllOf(u);
        assertThat(store().rotate(hash("b"), hash("b2"), NOW.plusSeconds(10), NOW)).isInstanceOf(Rotation.Invalid.class);
        assertThat(store().rotate(hash("c"), hash("c2"), NOW.plusSeconds(10), NOW)).as("outro usuário não é afetado").isInstanceOf(Rotation.Rotated.class);
    }

    @Test void concurrentRotationsOfTheSameTokenLetExactlyOneThroughAndKillTheFamily() throws Exception {
        UUID u = newUser();
        for (int round = 0; round < 20; round++) {
            int rd = round;
            String first = hash("r" + round);
            store().save(new Entry(first, "fam" + round, u, NOW.plusSeconds(1000)));
            int threads = 8;
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger rotated = new AtomicInteger();
            AtomicInteger reusedOrInvalid = new AtomicInteger();
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    int n = i;
                    fs.add(pool.submit(() -> {
                        go.await();
                        Rotation r = store().rotate(first, hash("n" + rd + "_" + n), NOW.plusSeconds(2000), NOW);
                        if (r instanceof Rotation.Rotated) rotated.incrementAndGet();
                        else reusedOrInvalid.incrementAndGet();
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : fs) f.get();
            }
            assertThat(rotated.get()).as("rodada %d: só uma troca vence", round).isEqualTo(1);
            assertThat(reusedOrInvalid.get()).isEqualTo(threads - 1);
            // quem perdeu a corrida reapresentou um token consumido: a família inteira foi revogada, inclusive o token do vencedor
            for (int n = 0; n < threads; n++) {
                assertThat(store().rotate(hash("n" + round + "_" + n), hash("z" + round + "_" + n), NOW.plusSeconds(3000), NOW)).isInstanceOf(Rotation.Invalid.class);
            }
        }
    }
}
