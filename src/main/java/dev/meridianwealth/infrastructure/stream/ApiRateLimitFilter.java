package dev.meridianwealth.infrastructure.stream;

import dev.meridianwealth.application.port.CacheStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limita chamadas REST por IP por minuto, com contador no Redis (ou memória). O stream fica de fora. */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final CacheStore cache;
    private final int limit;

    public ApiRateLimitFilter(CacheStore cache, @Value("${meridianwealth.rate-limit.api-per-minute:300}") int limit) {
        this.cache = cache;
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !p.startsWith("/api/") || p.startsWith("/api/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long n = cache.incrementWindow("rl:" + req.getRemoteAddr(), Duration.ofMinutes(1));
        if (n > limit) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Limite de requisições por minuto excedido\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
