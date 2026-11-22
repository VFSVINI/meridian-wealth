package dev.meridianwealth.application.auth;

import java.util.UUID;

/**
 * Guarda de isolamento entre usuários: todo recurso que pertence a alguém passa por aqui antes de ser devolvido ou alterado. O
 * dono vem do recurso e o usuário vem do token validado; nunca de um parâmetro da requisição. Quando não bate, a resposta é a
 * mesma de "não existe" (o chamador converte), para não revelar que o recurso de outra pessoa existe.
 */
public final class IsolationGuard {
    private IsolationGuard() {}

    public static void requireOwner(UUID resourceOwner, AuthPrincipal principal) {
        if (principal == null || resourceOwner == null || !resourceOwner.equals(principal.userId())) {
            throw new AuthException(AuthException.Code.FORBIDDEN, "Recurso não encontrado");
        }
    }
}
