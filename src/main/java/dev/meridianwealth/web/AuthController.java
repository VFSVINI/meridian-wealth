package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthException;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.domain.auth.PasswordPolicy;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record ConfigView(boolean googleLoginEnabled, boolean demoEnabled, boolean registrationEnabled, int passwordMinLength, String passwordHint) {}

    public record RegisterRequest(String email, String password, String displayName) {}

    public record LoginRequest(String email, String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record GoogleRequest(String idToken) {}

    public record SessionView(String tokenType, String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt,
                              AuthService.UserView user, boolean newUser, boolean needsSuitability, boolean demo) {
        static SessionView of(AuthService.Session s) {
            return new SessionView("Bearer", s.accessToken(), s.accessExpiresAt(), s.refreshToken(), s.refreshExpiresAt(), s.user(), s.newUser(),
                    s.user().needsSuitability(), s.user().demo());
        }
    }

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    /** Público: diz ao cliente o que este ambiente oferece (o botão do Google some ou fica desabilitado sem a chave). */
    @GetMapping("/config")
    public ConfigView config() {
        return new ConfigView(auth.googleEnabled(), auth.settings().demoEnabled(), auth.settings().registrationEnabled(), PasswordPolicy.MIN_LENGTH,
                "Pelo menos " + PasswordPolicy.MIN_LENGTH + " caracteres; frases longas são melhores que símbolos.");
    }

    @PostMapping("/register")
    public ResponseEntity<SessionView> register(@RequestBody RegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionView.of(auth.register(r.email(), r.password(), r.displayName())));
    }

    @PostMapping("/login")
    public SessionView login(@RequestBody LoginRequest r, @RequestAttribute(value = AuthPrincipal.CLIENT_IP_ATTRIBUTE, required = false) String ip) {
        return SessionView.of(auth.login(r.email(), r.password(), ip));
    }

    @PostMapping("/refresh")
    public SessionView refresh(@RequestBody RefreshRequest r) { return SessionView.of(auth.refresh(r.refreshToken())); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest r) {
        auth.logout(r.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/google")
    public SessionView google(@RequestBody GoogleRequest r) { return SessionView.of(auth.loginWithGoogle(r.idToken())); }

    @PostMapping("/demo")
    public SessionView demo() { return SessionView.of(auth.demo()); }

    /** Converte as falhas de autenticação em respostas com status e código estáveis (nunca com detalhe interno). */
    @RestControllerAdvice(assignableTypes = {AuthController.class, MeController.class})
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
