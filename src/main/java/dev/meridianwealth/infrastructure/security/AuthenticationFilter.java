package dev.meridianwealth.infrastructure.security;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.port.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica pelo cabeçalho {@code Authorization: Bearer}. Um token válido deixa o {@link AuthPrincipal} na requisição; um token
 * inválido ou expirado em rota pública é ignorado (os dados públicos de mercado continuam abertos, é o "modo demo" anônimo). As
 * rotas de dados do usuário ({@link #PROTECTED_PREFIXES}) exigem token válido e respondem 401 sem ele.
 *
 * <p>O principal só nasce aqui, de um token verificado: nenhuma rota lê o dono dos dados de parâmetro, corpo ou caminho.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationFilter extends OncePerRequestFilter {
    /** Prefixos de rotas que pertencem ao usuário autenticado. */
    public static final List<String> PROTECTED_PREFIXES = List.of("/api/me");

    private final TokenService tokens;
    private final Clock clock;
    private final ClientIpResolver ips;

    public AuthenticationFilter(TokenService tokens, Clock utcClock, ClientIpResolver ips) {
        this.tokens = tokens;
        this.clock = utcClock;
        this.ips = ips;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        req.setAttribute(AuthPrincipal.CLIENT_IP_ATTRIBUTE, ips.resolve(req.getRemoteAddr(), req.getHeader("X-Forwarded-For")));
        req.setAttribute(AuthPrincipal.HTTPS_ATTRIBUTE, ips.isHttps(req));
        Optional<AuthPrincipal> principal = bearer(req).flatMap(t -> tokens.verifyAccess(t, clock.instant()));
        principal.ifPresent(p -> req.setAttribute(AuthPrincipal.REQUEST_ATTRIBUTE, p));
        if (principal.isEmpty() && isProtected(RequestPaths.dispatchPath(req))) {
            res.setStatus(401);
            res.setHeader("WWW-Authenticate", "Bearer");
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Autenticação necessária\",\"code\":\"UNAUTHENTICATED\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean isProtected(String uri) {
        return PROTECTED_PREFIXES.stream().anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }

    private static Optional<String> bearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || h.length() < 8 || !h.regionMatches(true, 0, "Bearer ", 0, 7)) return Optional.empty();
        return Optional.of(h.substring(7).trim());
    }
}
