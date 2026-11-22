package dev.meridianwealth.application.auth;

import dev.meridianwealth.application.auth.AuthException.Code;
import dev.meridianwealth.application.port.GoogleIdentityVerifier;
import dev.meridianwealth.application.port.GoogleIdentityVerifier.GoogleIdentity;
import dev.meridianwealth.application.port.PasswordHasher;
import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.RefreshTokenStore.Rotation;
import dev.meridianwealth.application.port.TokenService;
import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.Email;
import dev.meridianwealth.domain.auth.PasswordPolicy;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de autenticação: cadastro e login por senha, Login com Google, refresh com rotação, logout, sessão de
 * demonstração e perfil do próprio usuário. Todos os caminhos terminam no <b>mesmo</b> JWT interno + refresh token, então a
 * autorização da API não tem tratamento especial por provedor.
 *
 * <p>Regras que valem a pena ler: conta com o mesmo e-mail de outro provedor nunca é vinculada em silêncio (o usuário recebe um
 * conflito explícito); e-mail desconhecido paga o mesmo custo de hash que e-mail conhecido (sem canal de tempo); erros de login
 * não dizem se foi o e-mail ou a senha. A trava por e-mail é curta (protege cada conta); a trava por IP é bem mais folgada, porque
 * atrás de NAT ou de proxy muita gente legítima divide o mesmo IP e uma trava apertada derrubaria todos por causa de um.
 */
public final class AuthService {
    /** Parâmetros de tempo e capacidade, vindos da configuração. */
    public record Settings(Duration accessTtl, Duration refreshTtl, Duration demoTtl, int maxPermanentUsers, int maxDemoUsers,
                           boolean demoEnabled, boolean registrationEnabled) {}

    public record UserView(UUID id, String email, String displayName, String avatarUrl, AuthProvider provider, boolean demo,
                           InvestorProfile profile, boolean needsSuitability, Instant createdAt) {
        static UserView of(UserAccount u) {
            return new UserView(u.id(), u.email(), u.displayName(), u.avatarUrl(), u.provider(), u.demo(), u.profile(), u.profile() == null,
                    u.createdAt());
        }
    }

    /** Sessão devolvida ao cliente. {@code refreshToken} é nulo na sessão de demonstração (ela não renova). */
    public record Session(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt, UserView user,
                          boolean newUser) {}

    private final UserStore users;
    private final RefreshTokenStore refreshTokens;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final GoogleIdentityVerifier google;
    private final LoginAttemptLimiter emailLimiter;
    private final LoginAttemptLimiter ipLimiter;
    private final Settings settings;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserStore users, RefreshTokenStore refreshTokens, PasswordHasher hasher, TokenService tokens,
                       GoogleIdentityVerifier google, LoginAttemptLimiter emailLimiter, LoginAttemptLimiter ipLimiter, Settings settings,
                       Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.tokens = tokens;
        this.google = google;
        this.emailLimiter = emailLimiter;
        this.ipLimiter = ipLimiter;
        this.settings = settings;
        this.clock = clock;
        this.dummyHash = hasher.hash(tokens.newRefreshToken());
    }

    public boolean googleEnabled() { return google.enabled(); }

    public Settings settings() { return settings; }

    public Session register(String rawEmail, String password, String displayName) {
        if (!settings.registrationEnabled()) throw new AuthException(Code.CAPACITY_REACHED, "O cadastro está desativado neste ambiente");
        String email = normalizeEmail(rawEmail);
        String violation = PasswordPolicy.violation(password, email);
        if (violation != null) throw new AuthException(Code.INVALID_INPUT, violation);
        if (users.countPermanent() >= settings.maxPermanentUsers()) {
            throw new AuthException(Code.CAPACITY_REACHED, "Limite de contas deste ambiente atingido");
        }
        conflictIfExists(users.findByEmail(email));
        UserAccount account = new UserAccount(UUID.randomUUID(), email, displayName, null, AuthProvider.PASSWORD, hasher.hash(password), null,
                clock.instant(), null);
        if (!users.createIfAbsent(account)) {
            conflictIfExists(users.findByEmail(email));
            throw new AuthException(Code.EMAIL_TAKEN, "Este e-mail já está cadastrado");
        }
        return newSession(account, true, true);
    }

    /** @param clientKey identifica quem tenta (IP), para a trava de tentativas além da trava por e-mail */
    public Session login(String rawEmail, String password, String clientKey) {
        String email;
        try {
            email = Email.normalize(rawEmail);
        } catch (IllegalArgumentException e) {
            throw invalidCredentials();
        }
        String emailKey = "e:" + email;
        String ipKey = "i:" + (clientKey == null ? "?" : clientKey);
        if (emailLimiter.blocked(emailKey) || ipLimiter.blocked(ipKey)) {
            throw new AuthException(Code.TOO_MANY_ATTEMPTS, "Muitas tentativas. Aguarde alguns minutos e tente de novo");
        }
        Optional<UserAccount> found = users.findByEmail(email).filter(u -> u.provider() == AuthProvider.PASSWORD);
        String hash = found.map(UserAccount::passwordHash).orElse(dummyHash);
        boolean ok = password != null && hasher.matches(password, hash) && found.isPresent();
        if (!ok) {
            emailLimiter.recordFailure(emailKey);
            ipLimiter.recordFailure(ipKey);
            throw invalidCredentials();
        }
        emailLimiter.recordSuccess(emailKey);
        return newSession(found.get(), true, false);
    }

    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank() || rawRefreshToken.length() > 200) {
            throw new AuthException(Code.INVALID_REFRESH_TOKEN, "Sessão inválida ou expirada");
        }
        Instant now = clock.instant();
        String nextRaw = tokens.newRefreshToken();
        Instant nextExpires = now.plus(settings.refreshTtl());
        Rotation r = refreshTokens.rotate(tokens.hashRefreshToken(rawRefreshToken), tokens.hashRefreshToken(nextRaw), nextExpires, now);
        if (r instanceof Rotation.Reused) {
            throw new AuthException(Code.REFRESH_TOKEN_REUSED, "Esta sessão foi encerrada por segurança. Entre de novo");
        }
        if (!(r instanceof Rotation.Rotated rotated)) throw new AuthException(Code.INVALID_REFRESH_TOKEN, "Sessão inválida ou expirada");
        UserAccount user = users.findById(rotated.previous().userId()).orElse(null);
        if (user == null) {
            refreshTokens.revokeAllOf(rotated.previous().userId());
            throw new AuthException(Code.INVALID_REFRESH_TOKEN, "Sessão inválida ou expirada");
        }
        TokenService.AccessToken access = tokens.issueAccess(user, now, settings.accessTtl());
        return new Session(access.token(), access.expiresAt(), nextRaw, nextExpires, UserView.of(user), false);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank() && rawRefreshToken.length() <= 200) {
            refreshTokens.revokeFamilyOf(tokens.hashRefreshToken(rawRefreshToken));
        }
    }

    public Session loginWithGoogle(String idToken) {
        if (!google.enabled()) throw new AuthException(Code.GOOGLE_NOT_CONFIGURED, "O Login com Google não está configurado neste ambiente");
        if (idToken == null || idToken.isBlank() || idToken.length() > 4096) throw new AuthException(Code.INVALID_GOOGLE_TOKEN, "Token do Google inválido");
        GoogleIdentity id;
        try {
            id = google.verify(idToken, clock.instant());
        } catch (GoogleIdentityVerifier.InvalidGoogleToken e) {
            throw new AuthException(Code.INVALID_GOOGLE_TOKEN, "Token do Google inválido");
        }
        String email = normalizeEmail(id.email());
        Optional<UserAccount> bySubject = users.findByGoogleSubject(id.subject());
        if (bySubject.isPresent()) return newSession(bySubject.get(), true, false);
        Optional<UserAccount> byEmail = users.findByEmail(email);
        if (byEmail.isPresent()) {
            // Nunca vincular em silêncio: o mesmo e-mail em outro provedor é conflito explícito.
            conflictIfExists(byEmail);
        }
        if (users.countPermanent() >= settings.maxPermanentUsers()) throw new AuthException(Code.CAPACITY_REACHED, "Limite de contas deste ambiente atingido");
        UserAccount account = new UserAccount(UUID.randomUUID(), email, id.name(), safeAvatar(id.pictureUrl()), AuthProvider.GOOGLE, null,
                id.subject(), clock.instant(), null);
        if (!users.createIfAbsent(account)) {
            Optional<UserAccount> raced = users.findByGoogleSubject(id.subject());
            if (raced.isPresent()) return newSession(raced.get(), true, false);
            conflictIfExists(users.findByEmail(email));
            throw new AuthException(Code.EMAIL_TAKEN, "Este e-mail já está cadastrado");
        }
        return newSession(account, true, true);
    }

    /** Sessão de demonstração: conta efêmera, sem refresh, que expira e some sozinha. */
    public Session demo() {
        if (!settings.demoEnabled()) throw new AuthException(Code.DEMO_DISABLED, "O modo demonstração está desativado neste ambiente");
        Instant now = clock.instant();
        users.purgeDemoBefore(now.minus(settings.demoTtl()));
        if (users.countDemo() >= settings.maxDemoUsers()) throw new AuthException(Code.CAPACITY_REACHED, "Muitas sessões de demonstração no momento. Tente de novo em instantes");
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount(id, "demo-" + id.toString().substring(0, 8) + "@demo.invalid", "Visitante (demonstração)", null,
                AuthProvider.DEMO, null, null, now, null);
        if (!users.createIfAbsent(account)) throw new AuthException(Code.CAPACITY_REACHED, "Tente de novo");
        TokenService.AccessToken access = tokens.issueAccess(account, now, settings.demoTtl());
        return new Session(access.token(), access.expiresAt(), null, null, UserView.of(account), true);
    }

    public UserView me(AuthPrincipal principal) {
        UserAccount u = load(principal);
        IsolationGuard.requireOwner(u.id(), principal);
        return UserView.of(u);
    }

    public Optional<InvestorProfile> profile(AuthPrincipal principal) {
        UserAccount u = load(principal);
        IsolationGuard.requireOwner(u.id(), principal);
        return Optional.ofNullable(u.profile());
    }

    public UserView saveProfile(AuthPrincipal principal, InvestorProfile profile) {
        if (profile == null) throw new AuthException(Code.INVALID_INPUT, "Informe o perfil");
        UserAccount u = load(principal);
        IsolationGuard.requireOwner(u.id(), principal);
        UserAccount updated = u.withProfile(profile);
        users.update(updated);
        return UserView.of(updated);
    }

    private UserAccount load(AuthPrincipal principal) {
        if (principal == null) throw invalidCredentials();
        return users.findById(principal.userId()).orElseThrow(() -> new AuthException(Code.INVALID_CREDENTIALS, "Sessão inválida ou expirada"));
    }

    private Session newSession(UserAccount user, boolean withRefresh, boolean isNew) {
        Instant now = clock.instant();
        TokenService.AccessToken access = tokens.issueAccess(user, now, settings.accessTtl());
        if (!withRefresh) return new Session(access.token(), access.expiresAt(), null, null, UserView.of(user), isNew);
        String raw = tokens.newRefreshToken();
        Instant expires = now.plus(settings.refreshTtl());
        refreshTokens.save(new RefreshTokenStore.Entry(tokens.hashRefreshToken(raw), tokens.newFamilyId(), user.id(), expires));
        return new Session(access.token(), access.expiresAt(), raw, expires, UserView.of(user), isNew);
    }

    private static String normalizeEmail(String raw) {
        try {
            return Email.normalize(raw);
        } catch (IllegalArgumentException e) {
            throw new AuthException(Code.INVALID_INPUT, e.getMessage());
        }
    }

    private static void conflictIfExists(Optional<UserAccount> existing) {
        if (existing.isEmpty()) return;
        if (existing.get().provider() == AuthProvider.GOOGLE) {
            throw new AuthException(Code.EMAIL_REGISTERED_WITH_GOOGLE, "Este e-mail já tem conta pelo Login com Google. Entre com o Google");
        }
        if (existing.get().provider() == AuthProvider.PASSWORD) {
            throw new AuthException(Code.EMAIL_REGISTERED_WITH_PASSWORD, "Este e-mail já tem conta com senha. Entre com a senha: contas não são vinculadas automaticamente");
        }
        throw new AuthException(Code.EMAIL_TAKEN, "Este e-mail já está cadastrado");
    }

    private static AuthException invalidCredentials() { return new AuthException(Code.INVALID_CREDENTIALS, "E-mail ou senha incorretos"); }

    /** Só aceita avatar https de tamanho razoável: o valor vem de fora e vai parar numa tag de imagem. */
    private static String safeAvatar(String url) {
        return url != null && url.length() <= 500 && url.startsWith("https://") && url.chars().noneMatch(c -> c <= 32 || c == '"' || c == '<' || c == '>')
                ? url : null;
    }
}
