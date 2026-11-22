package dev.meridianwealth.application.auth;

import dev.meridianwealth.domain.auth.AuthProvider;
import java.util.UUID;

/** Quem está autenticado numa requisição: o dono dos dados que ela pode tocar. Vem sempre de um token validado. */
public record AuthPrincipal(UUID userId, AuthProvider provider) {
    /** Atributo da requisição onde o filtro de autenticação deixa o principal já validado. */
    public static final String REQUEST_ATTRIBUTE = "meridianwealth.principal";

    /** Atributo da requisição com o IP real do cliente (já resolvido com proxies confiáveis). */
    public static final String CLIENT_IP_ATTRIBUTE = "meridianwealth.clientIp";

    public boolean demo() { return provider == AuthProvider.DEMO; }
}
