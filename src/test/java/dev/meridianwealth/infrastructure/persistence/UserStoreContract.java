package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.domain.suitability.InvestorProfile;
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

/** Contrato do {@link UserStore}, igual para a versão em memória e para a JDBC. */
abstract class UserStoreContract {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    abstract UserStore store();

    static UserAccount password(String email) { return new UserAccount(UUID.randomUUID(), email, "Nome", null, AuthProvider.PASSWORD, "$2a$04$hash", null, NOW, null); }

    static UserAccount google(String email, String sub) {
        return new UserAccount(UUID.randomUUID(), email, "Google", "https://lh3.googleusercontent.com/a/x", AuthProvider.GOOGLE, null, sub, NOW, null);
    }

    static UserAccount demo(String email, Instant created) { return new UserAccount(UUID.randomUUID(), email, "Demo", null, AuthProvider.DEMO, null, null, created, null); }

    @Test void createAndFindByEveryKeyWithAllFieldsRoundTripping() {
        UserAccount a = password("ana@exemplo.com");
        UserAccount g = google("bia@exemplo.com", "sub-1");
        assertThat(store().createIfAbsent(a)).isTrue();
        assertThat(store().createIfAbsent(g)).isTrue();
        assertThat(store().findById(a.id())).contains(a);
        assertThat(store().findByEmail("ana@exemplo.com")).contains(a);
        assertThat(store().findByGoogleSubject("sub-1")).contains(g);
        assertThat(store().findByGoogleSubject("sub-1").orElseThrow().avatarUrl()).isEqualTo("https://lh3.googleusercontent.com/a/x");
        assertThat(store().findByEmail("nada@exemplo.com")).isEmpty();
        assertThat(store().findById(UUID.randomUUID())).isEmpty();
        assertThat(store().findByGoogleSubject("outro")).isEmpty();
        assertThat(store().countPermanent()).isEqualTo(2);
    }

    @Test void deleteRemovesTheAccountFromEveryKeyAndFreesTheEmailAndSubject() {
        UserAccount g = google("bia@exemplo.com", "sub-del");
        UserAccount keep = password("keep@exemplo.com");
        store().createIfAbsent(g);
        store().createIfAbsent(keep);
        store().delete(g.id());
        assertThat(store().findById(g.id())).isEmpty();
        assertThat(store().findByEmail("bia@exemplo.com")).isEmpty();
        assertThat(store().findByGoogleSubject("sub-del")).isEmpty();
        assertThat(store().findById(keep.id())).as("as outras contas ficam").isPresent();
        assertThat(store().createIfAbsent(google("bia@exemplo.com", "sub-del"))).as("e-mail e sub voltam a ficar livres").isTrue();
        store().delete(UUID.randomUUID()); // conta inexistente: nada acontece
    }

    @Test void emailAndGoogleSubjectAreUnique() {
        assertThat(store().createIfAbsent(password("ana@exemplo.com"))).isTrue();
        assertThat(store().createIfAbsent(password("ana@exemplo.com"))).isFalse();
        assertThat(store().createIfAbsent(google("g@exemplo.com", "sub-x"))).isTrue();
        assertThat(store().createIfAbsent(google("g2@exemplo.com", "sub-x"))).as("mesmo sub do Google, e-mail novo").isFalse();
        assertThat(store().countPermanent()).isEqualTo(2);
    }

    @Test void updateChangesOnlyTheMutableFieldsAndRejectsUnknownOrRenamedAccounts() {
        UserAccount a = password("ana@exemplo.com");
        store().createIfAbsent(a);
        store().update(a.withProfile(InvestorProfile.BOLD));
        UserAccount stored = store().findById(a.id()).orElseThrow();
        assertThat(stored.profile()).isEqualTo(InvestorProfile.BOLD);
        assertThat(stored.email()).isEqualTo("ana@exemplo.com");
        assertThat(stored.passwordHash()).isEqualTo("$2a$04$hash");
        assertThatThrownBy(() -> store().update(password("outra@exemplo.com"))).isInstanceOf(IllegalArgumentException.class);
        UserAccount renamed = new UserAccount(a.id(), "novo@exemplo.com", "Nome", null, AuthProvider.PASSWORD, "$2a$04$hash", null, NOW, null);
        assertThatThrownBy(() -> store().update(renamed)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void demoAccountsAreCountedSeparatelyAndPurgedByAge() {
        store().createIfAbsent(password("ana@exemplo.com"));
        store().createIfAbsent(demo("d1@demo.invalid", NOW.minusSeconds(7200)));
        store().createIfAbsent(demo("d2@demo.invalid", NOW));
        assertThat(store().countDemo()).isEqualTo(2);
        assertThat(store().countPermanent()).isEqualTo(1);
        assertThat(store().purgeDemoBefore(NOW.minusSeconds(3600))).isEqualTo(1);
        assertThat(store().countDemo()).isEqualTo(1);
        assertThat(store().findByEmail("d1@demo.invalid")).isEmpty();
        assertThat(store().findByEmail("ana@exemplo.com")).isPresent();
    }

    @Test void concurrentSignupsWithTheSameEmailCreateOneAccount() throws Exception {
        AtomicInteger created = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    if (store().createIfAbsent(password("corrida@exemplo.com"))) created.incrementAndGet();
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
        }
        assertThat(created.get()).isEqualTo(1);
        assertThat(store().countPermanent()).isEqualTo(1);
    }
}
