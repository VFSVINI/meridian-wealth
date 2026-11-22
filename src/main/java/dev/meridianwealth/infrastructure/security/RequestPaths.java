package dev.meridianwealth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * O caminho que o Spring de fato despacha: decodificado e sem parâmetros de caminho ({@code ;x=1}). {@code getRequestURI()} é o texto
 * cru, e {@code /actuator/prometheus;x=1} ou {@code /api/%61uth/login} chegam ao mesmo endpoint sem casar com uma comparação literal.
 * Todo filtro que decide por caminho (autenticação, limites, cabeçalhos, métricas) deve usar este método e nunca o texto cru.
 */
public final class RequestPaths {
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private RequestPaths() {}

    public static String dispatchPath(HttpServletRequest request) { return PATHS.getPathWithinApplication(request); }
}
