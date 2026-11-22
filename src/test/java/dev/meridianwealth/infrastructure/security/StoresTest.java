package dev.meridianwealth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import dev.meridianwealth.application.port.RefreshTokenStore.Entry;
import dev.meridianwealth.application.port.RefreshTokenStore.Rotation;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import java.io.IOException;
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

class StoresTest {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private static UserAccount user(String email, AuthProvider p, String sub, Instant created) {
        return new UserAccount(UUID.randomUUID(), email, "N", null, p, p == AuthProvider.PASSWORD ? "hash" : null, sub, created, null);
    }

    @Test void userStoreKeepsEmailAndGoogleSubjectUnique() {
        InMemoryUserStore s = new InMemoryUserStore();
        UserAccount a = user("a@x.com", AuthProvider.PASSWORD, null, NOW);
        assertThat(s.createIfAbsent(a)).isTrue();
        assertThat(s.createIfAbsent(user("a@x.com", AuthProvider.PASSWORD, null, NOW))).isFalse();
        UserAccount g = user("g@x.com", AuthProvider.GOOGLE, "sub-1", NOW);
        assertThat(s.createIfAbsent(g)).isTrue();
        assertThat(s.createIfAbsent(user("g2@x.com", AuthProvider.GOOGLE, "sub-1", NOW))).as("mesmo sub, e-mail novo").isFalse();
        assertThat(s.findByGoogleSubject("sub-1")).contains(g);
        assertThat(s.findByEmail("a@x.com")).contains(a);
        assertThat(s.findById(g.id())).contains(g);
        assertThat(s.findByEmail("nada@x.com")).isEmpty();
        assertThat(s.countPermanent()).isEqualTo(2);
    }

    @Test void userStoreUpdateKeepsTheEmailAndRejectsUnknownAccounts() {
        InMemoryUserStore s = new InMemoryUserStore();
        UserAccount a = user("a@x.com", AuthProvider.PASSWORD, null, NOW);
        s.createIfAbsent(a);
        s.update(a.withProfile(dev.meridianwealth.domain.suitability.InvestorProfile.BOLD));
        assertThat(s.findById(a.id())).get().extracting(UserAccount::profile).isEqualTo(dev.meridianwealth.domain.suitability.InvestorProfile.BOLD);
        assertThatThrownBy(() -> s.update(user("b@x.com", AuthProvider.PASSWORD, null, NOW))).hasMessageContaining("inexistente");
        UserAccount changed = new UserAccount(a.id(), "outro@x.com", "N", null, AuthProvider.PASSWORD, "hash", null, NOW, null);
        assertThatThrownBy(() -> s.update(changed)).hasMessageContaining("e-mail");
    }

    @Test void demoAccountsArePurgedWithoutTouchingPermanentOnes() {
        InMemoryUserStore s = new InMemoryUserStore();
        s.createIfAbsent(user("a@x.com", AuthProvider.PASSWORD, null, NOW.minusSeconds(99999)));
        s.createIfAbsent(user("d1@demo.invalid", AuthProvider.DEMO, null, NOW.minusSeconds(7200)));
        s.createIfAbsent(user("d2@demo.invalid", AuthProvider.DEMO, null, NOW));
        assertThat(s.countDemo()).isEqualTo(2);
        assertThat(s.purgeDemoBefore(NOW.minusSeconds(3600))).isEqualTo(1);
        assertThat(s.countDemo()).isEqualTo(1);
        assertThat(s.countPermanent()).isEqualTo(1);
        assertThat(s.findByEmail("d1@demo.invalid")).isEmpty();
    }

    @Test void concurrentCreationOfTheSameEmailSucceedsOnce() throws Exception {
        InMemoryUserStore s = new InMemoryUserStore();
        AtomicInteger created = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    if (s.createIfAbsent(user("mesmo@x.com", AuthProvider.PASSWORD, null, NOW))) created.incrementAndGet();
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
        }
        assertThat(created.get()).isEqualTo(1);
    }

    private static Entry entry(String hash, String family, UUID user, Instant exp) { return new Entry(hash, family, user, exp); }

    @Test void refreshStoreRotatesKeepingTheFamilyAndUser() {
        InMemoryRefreshTokenStore s = new InMemoryRefreshTokenStore();
        UUID u = UUID.randomUUID();
        s.save(entry("h1", "fam", u, NOW.plusSeconds(100)));
        Rotation r = s.rotate("h1", "h2", NOW.plusSeconds(200), NOW);
        assertThat(r).isInstanceOf(Rotation.Rotated.class);
        assertThat(((Rotation.Rotated) r).previous().familyId()).isEqualTo("fam");
        assertThat(s.rotate("h2", "h3", NOW.plusSeconds(300), NOW)).isInstanceOf(Rotation.Rotated.class);
        assertThat(s.rotate("naoexiste", "hx", NOW.plusSeconds(300), NOW)).isInstanceOf(Rotation.Invalid.class);
    }

    @Test void reusingAConsumedTokenRevokesEveryTokenOfTheFamily() {
        InMemoryRefreshTokenStore s = new InMemoryRefreshTokenStore();
        UUID u = UUID.randomUUID();
        s.save(entry("h1", "fam", u, NOW.plusSeconds(100)));
        s.rotate("h1", "h2", NOW.plusSeconds(200), NOW);
        Rotation reuse = s.rotate("h1", "h9", NOW.plusSeconds(200), NOW);
        assertThat(reuse).isEqualTo(new Rotation.Reused("fam", u));
        assertThat(s.rotate("h2", "h3", NOW.plusSeconds(300), NOW)).as("o token vivo da família caiu junto").isInstanceOf(Rotation.Invalid.class);
    }

    @Test void expiredTokensAreInvalidAndRevocationByUserOrLogoutWorks() {
        InMemoryRefreshTokenStore s = new InMemoryRefreshTokenStore();
        UUID u = UUID.randomUUID();
        s.save(entry("old", "f0", u, NOW.minusSeconds(1)));
        assertThat(s.rotate("old", "x", NOW.plusSeconds(10), NOW)).isInstanceOf(Rotation.Invalid.class);
        s.save(entry("a", "fa", u, NOW.plusSeconds(100)));
        s.save(entry("b", "fb", u, NOW.plusSeconds(100)));
        s.save(entry("c", "fc", UUID.randomUUID(), NOW.plusSeconds(100)));
        s.revokeFamilyOf("a");
        s.revokeFamilyOf("desconhecido");
        assertThat(s.rotate("a", "a2", NOW.plusSeconds(10), NOW)).isInstanceOf(Rotation.Invalid.class);
        s.revokeAllOf(u);
        assertThat(s.rotate("b", "b2", NOW.plusSeconds(10), NOW)).isInstanceOf(Rotation.Invalid.class);
        assertThat(s.rotate("c", "c2", NOW.plusSeconds(10), NOW)).as("outro usuário não é afetado").isInstanceOf(Rotation.Rotated.class);
    }

    @Test void googleJwksAreCachedRefetchedOnUnknownKidAtMostOncePerMinuteAndSurviveOutages() throws Exception {
        RSAKey k1 = new RSAKeyGenerator(2048).keyID("k1").generate();
        String json = new com.nimbusds.jose.jwk.JWKSet(k1.toPublicJWK()).toString();
        AtomicInteger fetches = new AtomicInteger();
        GoogleJwksSource src = new GoogleJwksSource((url, headers) -> {
            fetches.incrementAndGet();
            assertThat(url).isEqualTo(GoogleJwksSource.CERTS_URL);
            return json;
        });
        JWKSelector known = new JWKSelector(new JWKMatcher.Builder().keyID("k1").build());
        JWKSelector unknown = new JWKSelector(new JWKMatcher.Builder().keyID("outra").build());
        assertThat(src.get(known, null)).hasSize(1);
        assertThat(src.get(known, null)).hasSize(1);
        assertThat(fetches.get()).as("dois usos, uma busca").isEqualTo(1);
        assertThat(src.get(unknown, null)).isEmpty();
        assertThat(src.get(unknown, null)).isEmpty();
        assertThat(fetches.get()).as("kid desconhecido repetido não vira uma busca por token falso").isEqualTo(1);

        GoogleJwksSource down = new GoogleJwksSource((url, headers) -> { throw new IOException("fora do ar"); });
        assertThatThrownBy(() -> down.get(known, null)).isInstanceOf(KeySourceException.class);
    }
}
