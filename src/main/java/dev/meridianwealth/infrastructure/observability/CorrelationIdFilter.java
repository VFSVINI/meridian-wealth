package dev.meridianwealth.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dá a cada requisição um identificador de correlação que liga a resposta aos logs. Aceita o {@code X-Correlation-Id} do cliente <b>só se
 * for válido</b> (de 8 a 64 caracteres entre letras, números, ponto, sublinhado e hífen); qualquer outra coisa, inclusive quebra de linha,
 * é descartada e um UUID novo é gerado: o valor vem de fora e vai parar em todo log da requisição, então não pode carregar texto livre
 * (injeção de linhas falsas no log). O identificador volta no cabeçalho da resposta e fica no MDC ({@code correlationId}), que o padrão de
 * log imprime; o MDC é limpo no fim, mesmo com erro.
 *
 * <p>Também registra uma linha por requisição de {@code /api/**} (método, caminho, status e duração), sem query, corpo nem credenciais.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    private static final Logger LOG = Logger.getLogger("meridianwealth.http");

    private final boolean accessLog;

    public CorrelationIdFilter(@Value("${meridianwealth.observability.access-log:true}") boolean accessLog) { this.accessLog = accessLog; }

    /** Aceita o identificador do cliente se for válido; senão gera um. */
    static String resolve(String fromClient) {
        return fromClient != null && VALID.matcher(fromClient).matches() ? fromClient : UUID.randomUUID().toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String id = resolve(req.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        res.setHeader(HEADER, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            String uri = req.getRequestURI();
            if (accessLog && uri.startsWith("/api/") && !uri.startsWith("/api/stream")) {
                LOG.log(Level.INFO, "http {0} {1} -> {2} ({3} ms)", new Object[] {req.getMethod(), uri, res.getStatus(), (System.nanoTime() - start) / 1_000_000});
            }
            MDC.remove(MDC_KEY);
        }
    }
}
