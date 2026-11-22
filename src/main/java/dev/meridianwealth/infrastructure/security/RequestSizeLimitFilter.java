package dev.meridianwealth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita o tamanho do corpo das requisições de escrita em {@code /api/**} (64 KB por padrão: o maior payload legítimo é uma carteira
 * de 50 posições, na casa de 5 KB). Com {@code Content-Length} acima do limite, a resposta é 413 antes de qualquer leitura; sem
 * {@code Content-Length} (chunked), o fluxo é limitado byte a byte e estourar o limite interrompe a leitura. Sem isso, as rotas
 * públicas de autenticação alocariam dezenas de megabytes por requisição de quem não fez login.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH");
    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${meridianwealth.security.max-body-bytes:65536}") long maxBytes) {
        if (maxBytes < 1024) throw new IllegalArgumentException("max-body-bytes precisa ser pelo menos 1024");
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RequestPaths.dispatchPath(request).startsWith("/api/") || !WRITES.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long declared = req.getContentLengthLong();
        if (declared > maxBytes) {
            res.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Corpo da requisição grande demais (máximo " + maxBytes / 1024 + " KB)\",\"code\":\"BODY_TOO_LARGE\"}");
            return;
        }
        chain.doFilter(declared >= 0 ? req : new Limited(req, maxBytes), res);
    }

    /** Requisição sem tamanho declarado: o fluxo de entrada passa a ter um teto. */
    private static final class Limited extends HttpServletRequestWrapper {
        private final long max;
        private ServletInputStream stream;

        Limited(HttpServletRequest request, long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new Capped(super.getInputStream(), max);
            return stream;
        }
    }

    private static final class Capped extends ServletInputStream {
        private final ServletInputStream in;
        private final long max;
        private long count;

        Capped(ServletInputStream in, long max) {
            this.in = in;
            this.max = max;
        }

        private void check(long add) throws IOException {
            count += add;
            if (count > max) throw new IOException("Corpo da requisição grande demais");
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) check(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = in.read(buf, off, len);
            if (n > 0) check(n);
            return n;
        }

        @Override
        public boolean isFinished() { return in.isFinished(); }

        @Override
        public boolean isReady() { return in.isReady(); }

        @Override
        public void setReadListener(ReadListener listener) { in.setReadListener(listener); }
    }
}
