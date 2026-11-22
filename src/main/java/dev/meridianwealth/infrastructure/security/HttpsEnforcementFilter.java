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
 * Força o HTTPS quando {@code MW_FORCE_HTTPS=true}: requisição em HTTP puro recebe um redirecionamento permanente (308, que preserva o
 * método) para o mesmo endereço em HTTPS. Atrás de um proxy que termina o TLS, o proxy precisa estar em {@code MW_TRUSTED_PROXIES}: é só
 * assim que o {@code X-Forwarded-Proto} vale, e sem isso toda requisição pareceria HTTP e entraria em laço. O host do redirecionamento
 * é validado (nada de redirecionar para um valor de {@code Host} forjado) e a verificação de saúde fica de fora, para o orquestrador
 * conseguir sondar por dentro da rede.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpsEnforcementFilter extends OncePerRequestFilter {
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.\\-]{1,253}(:\\d{1,5})?$");
    private final boolean force;
    private final ClientIpResolver ips;

    public HttpsEnforcementFilter(@Value("${meridianwealth.security.force-https:false}") boolean force, ClientIpResolver ips) {
        this.force = force;
        this.ips = ips;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !force || ips.isHttps(request) || RequestPaths.dispatchPath(request).startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String host = req.getHeader("Host");
        if (host == null || !HOST.matcher(host).matches()) {
            res.setStatus(400);
            return;
        }
        String query = req.getQueryString();
        res.setStatus(308);
        // a porta do HTTP não vale para o HTTPS: o redirecionamento usa a porta padrão (443), que é o que um proxy de TLS expõe
        res.setHeader("Location", "https://" + host.replaceFirst(":\\d+$", "") + req.getRequestURI() + (query == null ? "" : "?" + query));
    }
}
