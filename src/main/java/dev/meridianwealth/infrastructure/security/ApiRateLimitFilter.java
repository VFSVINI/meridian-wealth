package dev.meridianwealth.infrastructure.security;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.port.CacheStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de chamadas REST por minuto, com contador no Redis (ou memória). O stream fica de fora.
 *
 * <ul>
 *   <li><b>Anônimo:</b> por IP real do cliente ({@link ClientIpResolver}: {@code X-Forwarded-For} só vale vindo de proxy
 *       confiável).
 *   <li><b>Autenticado:</b> por usuário, para que quem está atrás do mesmo NAT não divida o limite.
 *   <li><b>Rotas de autenticação:</b> além do limite geral, um limite bem menor por IP, que segura força bruta de senha.
 *   <li><b>Rotas de cálculo pesado</b> ({@code /api/risk/**} e {@code /api/me/risk/**}, o Monte Carlo): além do limite geral, um limite bem menor por
 *       IP (anônimo) ou por usuário. Uma simulação custa cerca de 0,3 s de CPU, então os 300 por minuto das leituras baratas deixariam um só cliente
 *       ocupar vários núcleos.
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final CacheStore cache;
    private final ClientIpResolver ips;
    private final int anonymousPerMinute;
    private final int userPerMinute;
    private final int demoPerMinute;
    private final int authPerMinute;
    private final int heavyPerMinute;

    public ApiRateLimitFilter(CacheStore cache, ClientIpResolver ips, @Value("${meridianwealth.rate-limit.api-per-minute:300}") int anonymousPerMinute,
                              @Value("${meridianwealth.rate-limit.user-per-minute:600}") int userPerMinute,
                              @Value("${meridianwealth.rate-limit.demo-per-minute:120}") int demoPerMinute,
                              @Value("${meridianwealth.rate-limit.auth-per-minute:20}") int authPerMinute,
                              @Value("${meridianwealth.rate-limit.heavy-per-minute:10}") int heavyPerMinute) {
        this.cache = cache;
        this.ips = ips;
        this.anonymousPerMinute = anonymousPerMinute;
        this.userPerMinute = userPerMinute;
        this.demoPerMinute = demoPerMinute;
        this.authPerMinute = authPerMinute;
        this.heavyPerMinute = heavyPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = RequestPaths.dispatchPath(request);
        return !p.startsWith("/api/") || p.startsWith("/api/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Object attr = req.getAttribute(AuthPrincipal.CLIENT_IP_ATTRIBUTE);
        String ip = attr instanceof String s ? s : ips.resolve(req.getRemoteAddr(), req.getHeader("X-Forwarded-For"));
        AuthPrincipal who = (AuthPrincipal) req.getAttribute(AuthPrincipal.REQUEST_ATTRIBUTE);
        String path = RequestPaths.dispatchPath(req);
        boolean authRoute = path.startsWith("/api/auth/") && !path.equals("/api/auth/config");
        boolean over;
        if (who != null) {
            over = cache.incrementWindow("rl:u:" + who.userId(), Duration.ofMinutes(1)) > (who.demo() ? demoPerMinute : userPerMinute);
        } else {
            over = cache.incrementWindow("rl:ip:" + ip, Duration.ofMinutes(1)) > anonymousPerMinute;
        }
        if (!over && authRoute) over = cache.incrementWindow("rl:auth:" + ip, Duration.ofMinutes(1)) > authPerMinute;
        boolean heavyRoute = path.startsWith("/api/risk/") || path.startsWith("/api/me/risk/");
        if (!over && heavyRoute) over = cache.incrementWindow("rl:heavy:" + (who != null ? who.userId() : ip), Duration.ofMinutes(1)) > heavyPerMinute;
        if (over) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Limite de requisições por minuto excedido\",\"code\":\"RATE_LIMITED\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
