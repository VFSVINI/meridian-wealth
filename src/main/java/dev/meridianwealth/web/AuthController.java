package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthException;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.domain.auth.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cadastro, login (senha e Google), refresh com rotação, logout e sessão de demonstração. Todos devolvem o mesmo JWT interno e,
 * quando há renovação, o mesmo refresh token: a autorização da API não depende do provedor de login. Senhas e tokens nunca são
 * registrados em log.
 *
 * <p><b>Modo cookie.</b> O navegador manda {@code X-Session-Mode: cookie}: o refresh token vai (e volta) num cookie {@code HttpOnly},
 * {@code SameSite=Strict}, restrito a {@code /api/auth} e {@code Secure} sob HTTPS, e <b>não aparece no corpo</b>; nenhum JavaScript,
 * inclusive um injetado por XSS, consegue ler esse token. O cabeçalho é a defesa extra contra CSRF (um formulário de outro site não
 * consegue enviá-lo sem um preflight de CORS, que só as origens configuradas passam). Clientes de API sem cabeçalho seguem recebendo o
 * refresh token no corpo.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String MODE_HEADER = "X-Session-Mode";
    public static final String REFRESH_COOKIE = "mw_rt";

    public record ConfigView(boolean googleLoginEnabled, String googleClientId, boolean demoEnabled, boolean registrationEnabled, int passwordMinLength, String passwordHint) {}

    public record RegisterRequest(String email, String password, String displayName) {}

    public record LoginRequest(String email, String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record GoogleRequest(String idToken) {}

    public record SessionView(String tokenType, String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt,
                              AuthService.UserView user, boolean newUser, boolean needsSuitability, boolean demo) {
        static SessionView of(AuthService.Session s, boolean refreshInBody) {
            return new SessionView("Bearer", s.accessToken(), s.accessExpiresAt(), refreshInBody ? s.refreshToken() : null, s.refreshExpiresAt(), s.user(),
                    s.newUser(), s.user().needsSuitability(), s.user().demo());
        }
    }

    private final AuthService auth;
    private final String googleClientId;

    public AuthController(AuthService auth, @Value("${meridianwealth.google.client-id:}") String googleClientId) {
        this.auth = auth;
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
    }

    private static boolean cookieMode(HttpServletRequest req) { return "cookie".equalsIgnoreCase(req.getHeader(MODE_HEADER)); }

    private static ResponseCookie cookie(String value, Duration maxAge, HttpServletRequest req) {
        boolean https = Boolean.TRUE.equals(req.getAttribute(AuthPrincipal.HTTPS_ATTRIBUTE));
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(https).sameSite("Strict").path("/api/auth").maxAge(maxAge).build();
    }

    /** Monta a resposta da sessão; em modo cookie o refresh token sai no {@code Set-Cookie} e some do corpo. */
    private static ResponseEntity<SessionView> respond(HttpStatus status, AuthService.Session s, HttpServletRequest req) {
        boolean cookie = cookieMode(req);
        ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
        if (cookie && s.refreshToken() != null) {
            Duration ttl = Duration.between(Instant.now(), s.refreshExpiresAt());
            b.header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), ttl.isNegative() ? Duration.ZERO : ttl, req).toString());
        }
        return b.body(SessionView.of(s, !cookie));
    }

    /** Público: diz ao cliente o que este ambiente oferece (o botão do Google some ou fica desabilitado sem a chave). */
    @GetMapping("/config")
    public ConfigView config() {
        boolean google = auth.googleEnabled();
        // o ID do cliente OAuth é público por natureza (o navegador o envia ao Google); o segredo nunca passa por aqui
        return new ConfigView(google, google && !googleClientId.isEmpty() ? googleClientId : null, auth.settings().demoEnabled(), auth.settings().registrationEnabled(), PasswordPolicy.MIN_LENGTH,
                "Pelo menos " + PasswordPolicy.MIN_LENGTH + " caracteres; frases longas são melhores que símbolos.");
    }

    @PostMapping("/register")
    public ResponseEntity<SessionView> register(@RequestBody RegisterRequest r, HttpServletRequest req) {
        return respond(HttpStatus.CREATED, auth.register(r.email(), r.password(), r.displayName()), req);
    }

    @PostMapping("/login")
    public ResponseEntity<SessionView> login(@RequestBody LoginRequest r, HttpServletRequest req,
                                             @RequestAttribute(value = AuthPrincipal.CLIENT_IP_ATTRIBUTE, required = false) String ip) {
        return respond(HttpStatus.OK, auth.login(r.email(), r.password(), ip), req);
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionView> refresh(@RequestBody(required = false) RefreshRequest r, @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
                                               HttpServletRequest req, HttpServletResponse res) {
        boolean cookie = cookieMode(req);
        String token = cookie ? cookieToken : r == null ? null : r.refreshToken();
        try {
            return respond(HttpStatus.OK, auth.refresh(token), req);
        } catch (AuthException e) {
            if (cookie) res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, req).toString()); // token inválido ou reusado: some do navegador
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest r, @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
                                       HttpServletRequest req) {
        boolean cookie = cookieMode(req);
        auth.logout(cookie ? cookieToken : r == null ? null : r.refreshToken());
        ResponseEntity.HeadersBuilder<?> b = ResponseEntity.noContent();
        if (cookie) b.header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, req).toString());
        return b.build();
    }

    @PostMapping("/google")
    public ResponseEntity<SessionView> google(@RequestBody GoogleRequest r, HttpServletRequest req) {
        return respond(HttpStatus.OK, auth.loginWithGoogle(r.idToken()), req);
    }

    @PostMapping("/demo")
    public ResponseEntity<SessionView> demo(HttpServletRequest req) { return respond(HttpStatus.OK, auth.demo(), req); }

    /** Converte as falhas de autenticação em respostas com status e código estáveis (nunca com detalhe interno). */
    @RestControllerAdvice(assignableTypes = {AuthController.class, MeController.class, PortfolioController.class, TaxController.class})
    static class Errors {
        @ExceptionHandler(AuthException.class)
        ResponseEntity<Map<String, String>> auth(AuthException e) {
            HttpStatus status = switch (e.code()) {
                case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
                case INVALID_CREDENTIALS, INVALID_REFRESH_TOKEN, REFRESH_TOKEN_REUSED, INVALID_GOOGLE_TOKEN -> HttpStatus.UNAUTHORIZED;
                case EMAIL_TAKEN, EMAIL_REGISTERED_WITH_PASSWORD, EMAIL_REGISTERED_WITH_GOOGLE -> HttpStatus.CONFLICT;
                case TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
                case GOOGLE_NOT_CONFIGURED, CAPACITY_REACHED -> HttpStatus.SERVICE_UNAVAILABLE;
                case DEMO_DISABLED, FORBIDDEN -> HttpStatus.NOT_FOUND;
            };
            ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
            if (status == HttpStatus.TOO_MANY_REQUESTS) b.header("Retry-After", "900");
            if (status == HttpStatus.UNAUTHORIZED) b.header("WWW-Authenticate", "Bearer");
            return b.body(Map.of("error", e.getMessage(), "code", e.code().name()));
        }
    }
}
