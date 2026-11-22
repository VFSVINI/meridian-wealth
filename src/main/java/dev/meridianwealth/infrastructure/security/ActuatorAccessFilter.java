package dev.meridianwealth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fecha as métricas do actuator ({@code /actuator/metrics} e {@code /actuator/prometheus}): mostram memória, consumo de cada provedor e
 * volume de uso, e não devem ficar abertas à internet. Sem {@code MW_METRICS_TOKEN} elas <b>não existem</b> (404); com o token, só
 * respondem a {@code Authorization: Bearer <token>} (comparação em tempo constante; 401 sem ele). {@code health} e {@code info} seguem
 * públicos, sem detalhes sensíveis.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class ActuatorAccessFilter extends OncePerRequestFilter {
    public static final int MIN_TOKEN_LENGTH = 24;
    private final byte[] token;

    public ActuatorAccessFilter(@Value("${meridianwealth.observability.metrics-token:}") String token) {
        if (!token.isBlank() && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("MW_METRICS_TOKEN precisa ter pelo menos " + MIN_TOKEN_LENGTH + " caracteres");
        }
        this.token = token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    static boolean guarded(String uri) {
        return uri.equals("/actuator/metrics") || uri.startsWith("/actuator/metrics/") || uri.equals("/actuator/prometheus");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !guarded(dispatchPath(request)); }

    /**
     * O caminho que o Spring de fato despacha: decodificado e sem parâmetros de caminho. {@code getRequestURI()} é o texto cru, e
     * {@code /actuator/prometheus;x=1} ou {@code /actuator/%70rometheus} chegam ao mesmo endpoint sem casar com a comparação literal.
     */
    static String dispatchPath(HttpServletRequest request) { return RequestPaths.dispatchPath(request); }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (token == null) {
            res.setStatus(404);
            return;
        }
        String h = req.getHeader("Authorization");
        boolean ok = h != null && h.length() > 7 && h.regionMatches(true, 0, "Bearer ", 0, 7)
                && MessageDigest.isEqual(h.substring(7).trim().getBytes(StandardCharsets.UTF_8), token);
        if (!ok) {
            res.setStatus(401);
            res.setHeader("WWW-Authenticate", "Bearer");
            return;
        }
        chain.doFilter(req, res);
    }
}
