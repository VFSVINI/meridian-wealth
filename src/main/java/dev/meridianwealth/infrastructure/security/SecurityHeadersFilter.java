package dev.meridianwealth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança em toda resposta. A CSP é restritiva: só recursos do próprio servidor, sem script nem estilo embutido
 * (a interface é servida em arquivos separados), sem frames, sem {@code <base>} e sem objetos. O único relaxamento é
 * {@code style-src-attr 'unsafe-inline'}, porque a interface monta marcação com atributo {@code style}; ele não abre execução de
 * script. A conexão do stream (WebSocket) é liberada só para o próprio host. As páginas da documentação da API (Swagger) usam
 * script embutido e ficam fora da CSP.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.\\-\\[\\]:]{1,255}$");
    /** Endereços do Google Identity Services, liberados na CSP só quando o Login com Google está configurado neste ambiente. */
    static final String GIS_SCRIPT = "https://accounts.google.com/gsi/client";
    static final String GIS_FRAME = "https://accounts.google.com/gsi/";
    static final String GIS_STYLE = "https://accounts.google.com/gsi/style";
    private final ClientIpResolver ips;
    private final boolean google;

    public SecurityHeadersFilter(ClientIpResolver ips, @Value("${meridianwealth.google.client-id:}") String googleClientId) {
        this.ips = ips;
        this.google = googleClientId != null && !googleClientId.isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        String uri = RequestPaths.dispatchPath(req);
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/me")) res.setHeader("Cache-Control", "no-store");
        if (isHttps(req)) res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        if (!uri.startsWith("/swagger-ui") && !uri.startsWith("/v3/api-docs")) res.setHeader("Content-Security-Policy", csp(req));
        chain.doFilter(req, res);
    }

    private boolean isHttps(HttpServletRequest req) { return ips.isHttps(req); }

    String csp(HttpServletRequest req) {
        String host = req.getHeader("Host");
        String ws = host != null && HOST.matcher(host).matches() ? " " + (isHttps(req) ? "wss://" : "ws://") + host : "";
        String script = google ? " " + GIS_SCRIPT : "";
        String style = google ? " " + GIS_STYLE : "";
        String connect = google ? " " + GIS_FRAME : "";
        String frame = google ? "; frame-src " + GIS_FRAME : "";
        return "default-src 'self'; script-src 'self'" + script + "; style-src 'self'" + style + "; style-src-attr 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
                + "connect-src 'self'" + ws + connect + frame + "; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";
    }
}
