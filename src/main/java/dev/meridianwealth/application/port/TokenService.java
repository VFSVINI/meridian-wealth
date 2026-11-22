package dev.meridianwealth.application.port;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.domain.auth.UserAccount;
import java.time.Instant;
import java.util.Optional;

/** Emite e valida o token de acesso (JWT assinado) e cria/resume os refresh tokens opacos. */
public interface TokenService {
    record AccessToken(String token, Instant expiresAt) {}

    AccessToken issueAccess(UserAccount user, Instant now, java.time.Duration ttl);

    /** Valida assinatura, emissor, audiência e validade; vazio para qualquer token inválido (sem dizer por quê). */
    Optional<AuthPrincipal> verifyAccess(String token, Instant now);

    /** Token opaco de 256 bits, aleatório e seguro para uso criptográfico. */
    String newRefreshToken();

    /** Hash determinístico do refresh token, que é o que se guarda. */
    String hashRefreshToken(String rawToken);

    String newFamilyId();
}
