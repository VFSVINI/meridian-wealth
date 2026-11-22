package dev.meridianwealth.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.application.auth.AuthException.Code;
import dev.meridianwealth.application.port.GoogleIdentityVerifier;
import dev.meridianwealth.application.port.PasswordHasher;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import dev.meridianwealth.infrastructure.security.BcryptPasswordHasher;
import dev.meridianwealth.infrastructure.security.InMemoryRefreshTokenStore;
import dev.meridianwealth.infrastructure.security.InMemoryUserStore;
import dev.meridianwealth.infrastructure.security.JwtTokenService;
import dev.meridianwealth.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final String PASSWORD = "uma frase longa e boa";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-21T12:00:00Z"));
    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryRefreshTokenStore refresh = new InMemoryRefreshTokenStore();
    private final JwtTokenService tokens = new JwtTokenService(JwtTokenService.randomSecret());
    private final AtomicInteger hashChecks = new AtomicInteger();
    private final BcryptPasswordHasher bcrypt = new BcryptPasswordHasher(4);
    private final PasswordHasher countingHasher = new PasswordHasher() {
        @Override
        public String hash(String p) { return bcrypt.hash(p); }

        @Override
        public boolean matches(String p, String h) {
            hashChecks.incrementAndGet();
            return bcrypt.matches(p, h);
        }
    };
    private GoogleIdentityVerifier.GoogleIdentity nextGoogle;
    private boolean googleOn = true;
    private final GoogleIdentityVerifier google = new GoogleIdentityVerifier() {
        @Override
        public boolean enabled() { return googleOn; }

        @Override
        public GoogleIdentity verify(String idToken, Instant now) {
            if (!"valido".equals(idToken)) throw new InvalidGoogleToken("ruim");
            return nextGoogle;
        }
    };
    private AuthService auth;

    @BeforeEach
    void setUp() { auth = service(true, true, 100, 3); }

    private AuthService service(boolean demo, boolean registration, int maxUsers, int maxDemo) {
        return service(demo, registration, maxUsers, maxDemo, 50);
    }

    private AuthService service(boolean demo, boolean registration, int maxUsers, int maxDemo, int ipFailures) {
        return new AuthService(users, refresh, countingHasher, tokens, google, new LoginAttemptLimiter(5, Duration.ofMinutes(15), clock),
                new LoginAttemptLimiter(ipFailures, Duration.ofMinutes(15), clock),
                new AuthService.Settings(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofMinutes(60), maxUsers, maxDemo, demo, registration), clock);
    }

    private static Code code(Runnable r) {
        try {
            r.run();
        } catch (AuthException e) {
            return e.code();
        }
        return null;
    }

    @Test void registerThenLoginIssuesTheSameKindOfSessionAndNeverStoresThePassword() {
        AuthService.Session s = auth.register("  Ana@Exemplo.com ", PASSWORD, "Ana");
        assertThat(s.newUser()).isTrue();
        assertThat(s.user().email()).isEqualTo("ana@exemplo.com");
        assertThat(s.user().needsSuitability()).isTrue();
        assertThat(s.refreshToken()).isNotBlank();
        assertThat(users.findByEmail("ana@exemplo.com")).get().satisfies(u -> {
            assertThat(u.passwordHash()).startsWith("$2").doesNotContain(PASSWORD);
            assertThat(u.provider()).isEqualTo(AuthProvider.PASSWORD);
        });
        AuthService.Session l = auth.login("ANA@exemplo.com", PASSWORD, "1.2.3.4");
        assertThat(l.newUser()).isFalse();
        assertThat(l.user().id()).isEqualTo(s.user().id());
        assertThat(tokens.verifyAccess(l.accessToken(), clock.instant())).get().extracting(AuthPrincipal::userId).isEqualTo(s.user().id());
    }

    @Test void registerValidatesInputAndRefusesDuplicatesIgnoringCase() {
        assertThat(code(() -> auth.register("sem-arroba", PASSWORD, "x"))).isEqualTo(Code.INVALID_INPUT);
        assertThat(code(() -> auth.register("a@exemplo.com", "curta", "x"))).isEqualTo(Code.INVALID_INPUT);
        auth.register("ana@exemplo.com", PASSWORD, "Ana");
        assertThat(code(() -> auth.register("ANA@exemplo.com", PASSWORD, "Outra"))).isEqualTo(Code.EMAIL_REGISTERED_WITH_PASSWORD);
        assertThat(users.countPermanent()).isEqualTo(1);
    }

    @Test void registrationCanBeDisabledAndCapacityIsLimited() {
        assertThat(code(() -> service(true, false, 100, 3).register("a@exemplo.com", PASSWORD, "A"))).isEqualTo(Code.CAPACITY_REACHED);
        AuthService small = service(true, true, 1, 3);
        small.register("a@exemplo.com", PASSWORD, "A");
        assertThat(code(() -> small.register("b@exemplo.com", PASSWORD, "B"))).isEqualTo(Code.CAPACITY_REACHED);
    }

    @Test void loginErrorsNeverRevealWhetherTheEmailExists() {
        auth.register("ana@exemplo.com", PASSWORD, "Ana");
        int before = hashChecks.get();
        assertThat(code(() -> auth.login("ana@exemplo.com", "senha errada demais", "ip"))).isEqualTo(Code.INVALID_CREDENTIALS);
        int knownCost = hashChecks.get() - before;
        before = hashChecks.get();
        assertThat(code(() -> auth.login("ninguem@exemplo.com", "senha errada demais", "ip"))).isEqualTo(Code.INVALID_CREDENTIALS);
        assertThat(hashChecks.get() - before).as("e-mail desconhecido paga o mesmo custo de hash").isEqualTo(knownCost).isEqualTo(1);
        assertThat(code(() -> auth.login("lixo", PASSWORD, "ip"))).isEqualTo(Code.INVALID_CREDENTIALS);
        try {
            auth.login("ana@exemplo.com", "errada errada errada", "ip");
        } catch (AuthException e) {
            assertThat(e.getMessage()).isEqualTo("E-mail ou senha incorretos");
        }
    }

    @Test void repeatedWrongPasswordsLockTheAccountEvenForTheRightPasswordUntilTheWindowPasses() {
        auth.register("ana@exemplo.com", PASSWORD, "Ana");
        for (int i = 0; i < 5; i++) assertThat(code(() -> auth.login("ana@exemplo.com", "errada errada errada", "ip" + System.nanoTime()))).isEqualTo(Code.INVALID_CREDENTIALS);
        assertThat(code(() -> auth.login("ana@exemplo.com", PASSWORD, "ip-novo"))).isEqualTo(Code.TOO_MANY_ATTEMPTS);
        clock.advance(Duration.ofMinutes(16));
        assertThat(auth.login("ana@exemplo.com", PASSWORD, "ip-novo").user().email()).isEqualTo("ana@exemplo.com");
    }

    @Test void theIpLockIsMuchLooserThanTheAccountLockSoASharedNatIsNotLockedOutByOneAttacker() {
        AuthService strictIp = service(true, true, 100, 3, 8);
        strictIp.register("ana@exemplo.com", PASSWORD, "Ana");
        for (int i = 0; i < 7; i++) assertThat(code(() -> strictIp.login("alvo" + System.nanoTime() + "@exemplo.com", "errada errada errada", "nat"))).isEqualTo(Code.INVALID_CREDENTIALS);
        assertThat(strictIp.login("ana@exemplo.com", PASSWORD, "nat").user().email()).as("5 erros de e-mails diferentes não travam o IP").isEqualTo("ana@exemplo.com");
        for (int i = 0; i < 8; i++) code(() -> strictIp.login("alvo" + System.nanoTime() + "@exemplo.com", "errada errada errada", "nat"));
        assertThat(code(() -> strictIp.login("ana@exemplo.com", PASSWORD, "nat"))).as("passado o limite do IP, bloqueia").isEqualTo(Code.TOO_MANY_ATTEMPTS);
        assertThat(strictIp.login("ana@exemplo.com", PASSWORD, "outro-ip").user().email()).isEqualTo("ana@exemplo.com");
    }

    @Test void refreshRotatesAndAReusedTokenKillsTheWholeFamily() {
        AuthService.Session first = auth.register("ana@exemplo.com", PASSWORD, "Ana");
        AuthService.Session second = auth.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.user().id()).isEqualTo(first.user().id());
        // o token antigo reaparece: alguém o copiou
        assertThat(code(() -> auth.refresh(first.refreshToken()))).isEqualTo(Code.REFRESH_TOKEN_REUSED);
        // e o token novo, da mesma família, também foi revogado
        assertThat(code(() -> auth.refresh(second.refreshToken()))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
    }

    @Test void refreshRejectsUnknownBlankExpiredAndOversizedTokens() {
        assertThat(code(() -> auth.refresh(null))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
        assertThat(code(() -> auth.refresh(" "))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
        assertThat(code(() -> auth.refresh("x".repeat(500)))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
        assertThat(code(() -> auth.refresh("token-que-nunca-existiu"))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
        AuthService.Session s = auth.register("ana@exemplo.com", PASSWORD, "Ana");
        clock.advance(Duration.ofDays(8));
        assertThat(code(() -> auth.refresh(s.refreshToken()))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
    }

    @Test void logoutRevokesTheSessionAndIsIdempotent() {
        AuthService.Session s = auth.register("ana@exemplo.com", PASSWORD, "Ana");
        auth.logout(s.refreshToken());
        auth.logout(s.refreshToken());
        auth.logout(null);
        assertThat(code(() -> auth.refresh(s.refreshToken()))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
    }

    @Test void concurrentRefreshWithTheSameTokenLetsExactlyOneThrough() throws Exception {
        for (int round = 0; round < 20; round++) {
            AuthService.Session s = auth.register("ana" + round + "@exemplo.com", PASSWORD, "Ana");
            int threads = 8;
            CountDownLatch go = new CountDownLatch(1);
            List<AuthService.Session> winners = new ArrayList<>();
            List<Code> losers = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    fs.add(pool.submit(() -> {
                        go.await();
                        try {
                            AuthService.Session n = auth.refresh(s.refreshToken());
                            synchronized (winners) { winners.add(n); }
                        } catch (AuthException e) {
                            synchronized (losers) { losers.add(e.code()); }
                        }
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : fs) f.get();
            }
            assertThat(winners).as("rodada %d: só uma troca pode vencer", round).hasSize(1);
            assertThat(losers).hasSize(threads - 1).allMatch(c -> c == Code.REFRESH_TOKEN_REUSED || c == Code.INVALID_REFRESH_TOKEN);
            // o ladrão que perdeu a corrida derrubou a família: o token do vencedor também não vale mais
            assertThat(code(() -> auth.refresh(winners.get(0).refreshToken()))).isEqualTo(Code.INVALID_REFRESH_TOKEN);
        }
    }

    @Test void concurrentRegistrationWithTheSameEmailCreatesOneAccount() throws Exception {
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    try {
                        auth.register("corrida@exemplo.com", PASSWORD, "C");
                        ok.incrementAndGet();
                    } catch (AuthException e) {
                        assertThat(e.code()).isIn(Code.EMAIL_REGISTERED_WITH_PASSWORD, Code.EMAIL_TAKEN);
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
        }
        assertThat(ok.get()).isEqualTo(1);
        assertThat(users.countPermanent()).isEqualTo(1);
    }

    @Test void googleIsDisabledWithoutAClientIdAndTheAppStillWorks() {
        googleOn = false;
        assertThat(auth.googleEnabled()).isFalse();
        assertThat(code(() -> auth.loginWithGoogle("valido"))).isEqualTo(Code.GOOGLE_NOT_CONFIGURED);
        assertThat(auth.register("ana@exemplo.com", PASSWORD, "Ana").user().email()).isEqualTo("ana@exemplo.com");
    }

    @Test void firstGoogleLoginProvisionsTheAccountAndLaterOnesReuseIt() {
        nextGoogle = new GoogleIdentityVerifier.GoogleIdentity("sub-123", "Bia@Exemplo.com", "Bia Souza", "https://lh3.googleusercontent.com/a/x");
        AuthService.Session first = auth.loginWithGoogle("valido");
        assertThat(first.newUser()).isTrue();
        assertThat(first.user().provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(first.user().email()).isEqualTo("bia@exemplo.com");
        assertThat(first.user().displayName()).isEqualTo("Bia Souza");
        assertThat(first.user().avatarUrl()).isEqualTo("https://lh3.googleusercontent.com/a/x");
        assertThat(first.user().needsSuitability()).as("sem perfil, segue para o suitability").isTrue();
        assertThat(first.refreshToken()).isNotBlank();
        AuthService.Session again = auth.loginWithGoogle("valido");
        assertThat(again.newUser()).isFalse();
        assertThat(again.user().id()).isEqualTo(first.user().id());
        assertThat(users.countPermanent()).isEqualTo(1);
        // a conta Google não aceita login por senha
        assertThat(code(() -> auth.login("bia@exemplo.com", PASSWORD, "ip"))).isEqualTo(Code.INVALID_CREDENTIALS);
    }

    @Test void googleNeverLinksSilentlyToAnExistingPasswordAccount() {
        AuthService.Session pw = auth.register("ana@exemplo.com", PASSWORD, "Ana");
        nextGoogle = new GoogleIdentityVerifier.GoogleIdentity("sub-ana", "ana@exemplo.com", "Ana G", null);
        assertThat(code(() -> auth.loginWithGoogle("valido"))).isEqualTo(Code.EMAIL_REGISTERED_WITH_PASSWORD);
        assertThat(users.findByGoogleSubject("sub-ana")).as("nada foi vinculado").isEmpty();
        assertThat(users.findById(pw.user().id())).get().extracting(u -> u.provider()).isEqualTo(AuthProvider.PASSWORD);
        assertThat(users.countPermanent()).isEqualTo(1);
    }

    @Test void passwordSignupWithAGoogleEmailIsAConflictToo() {
        nextGoogle = new GoogleIdentityVerifier.GoogleIdentity("sub-bia", "bia@exemplo.com", "Bia", null);
        auth.loginWithGoogle("valido");
        assertThat(code(() -> auth.register("bia@exemplo.com", PASSWORD, "Bia"))).isEqualTo(Code.EMAIL_REGISTERED_WITH_GOOGLE);
    }

    @Test void invalidGoogleTokensAreRejectedAndUnsafeAvatarsAreDropped() {
        assertThat(code(() -> auth.loginWithGoogle("falso"))).isEqualTo(Code.INVALID_GOOGLE_TOKEN);
        assertThat(code(() -> auth.loginWithGoogle(""))).isEqualTo(Code.INVALID_GOOGLE_TOKEN);
        assertThat(code(() -> auth.loginWithGoogle("x".repeat(5000)))).isEqualTo(Code.INVALID_GOOGLE_TOKEN);
        nextGoogle = new GoogleIdentityVerifier.GoogleIdentity("sub-x", "x@exemplo.com", "X", "javascript:alert(1)");
        assertThat(auth.loginWithGoogle("valido").user().avatarUrl()).isNull();
        nextGoogle = new GoogleIdentityVerifier.GoogleIdentity("sub-y", "y@exemplo.com", "Y", "http://inseguro/a.png");
        assertThat(auth.loginWithGoogle("valido").user().avatarUrl()).isNull();
        nextGoogle = new GoogleIdentityVerifier.GoogleIdentity("sub-z", "z@exemplo.com", "Z", "https://ok/a\".png");
        assertThat(auth.loginWithGoogle("valido").user().avatarUrl()).isNull();
    }

    @Test void demoSessionIsEphemeralHasNoRefreshAndExpires() {
        AuthService.Session d = auth.demo();
        assertThat(d.refreshToken()).isNull();
        assertThat(d.user().demo()).isTrue();
        assertThat(d.user().email()).endsWith("@demo.invalid");
        assertThat(d.accessExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(60)));
        assertThat(users.countPermanent()).isZero();
        assertThat(users.countDemo()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(61));
        auth.demo(); // a próxima demonstração varre as expiradas
        assertThat(users.countDemo()).isEqualTo(1);
        assertThat(code(() -> auth.me(new AuthPrincipal(d.user().id(), AuthProvider.DEMO)))).isEqualTo(Code.INVALID_CREDENTIALS);
    }

    @Test void demoCanBeDisabledAndHasACapacity() {
        assertThat(code(() -> service(false, true, 100, 3).demo())).isEqualTo(Code.DEMO_DISABLED);
        auth.demo();
        auth.demo();
        auth.demo();
        assertThat(code(() -> auth.demo())).isEqualTo(Code.CAPACITY_REACHED);
    }

    @Test void usersCannotTouchEachOthersProfileAndTheGuardRefusesAMismatch() {
        AuthService.Session a = auth.register("a@exemplo.com", PASSWORD, "A");
        AuthService.Session b = auth.register("b@exemplo.com", PASSWORD, "B");
        AuthPrincipal pa = new AuthPrincipal(a.user().id(), AuthProvider.PASSWORD);
        AuthPrincipal pb = new AuthPrincipal(b.user().id(), AuthProvider.PASSWORD);
        auth.saveProfile(pa, InvestorProfile.AGGRESSIVE);
        assertThat(auth.profile(pa)).contains(InvestorProfile.AGGRESSIVE);
        assertThat(auth.profile(pb)).as("B não vê o perfil de A").isEmpty();
        auth.saveProfile(pb, InvestorProfile.CONSERVATIVE);
        assertThat(auth.profile(pa)).contains(InvestorProfile.AGGRESSIVE);
        assertThat(auth.me(pb).needsSuitability()).isFalse();
        assertThatThrownBy(() -> IsolationGuard.requireOwner(a.user().id(), pb)).isInstanceOf(AuthException.class).extracting(e -> ((AuthException) e).code())
                .isEqualTo(Code.FORBIDDEN);
        IsolationGuard.requireOwner(a.user().id(), pa);
        assertThat(code(() -> IsolationGuard.requireOwner(a.user().id(), null))).isEqualTo(Code.FORBIDDEN);
        assertThat(code(() -> auth.saveProfile(pa, null))).isEqualTo(Code.INVALID_INPUT);
        assertThat(code(() -> auth.me(null))).isEqualTo(Code.INVALID_CREDENTIALS);
    }
}
