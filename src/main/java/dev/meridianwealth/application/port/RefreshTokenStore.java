package dev.meridianwealth.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh tokens com rotação e detecção de reuso. Só o hash do token é guardado. Cada token pertence a uma "família" (a cadeia
 * de rotações de um login): se um token já usado aparece de novo, alguém o copiou, e a família inteira é revogada.
 */
public interface RefreshTokenStore {
    record Entry(String hash, String familyId, UUID userId, Instant expiresAt) {}

    /** Resultado atômico de {@link #rotate}. */
    sealed interface Rotation {
        /** Este token era válido e ainda não usado: foi marcado como usado e o próximo foi gravado. */
        record Rotated(Entry previous) implements Rotation {}

        /** Este token já tinha sido usado (ou a família já estava revogada): a família foi revogada. */
        record Reused(String familyId, UUID userId) implements Rotation {}

        /** Token desconhecido ou expirado. */
        record Invalid() implements Rotation {}
    }

    /** Grava o primeiro token de uma família (login). */
    void save(Entry entry);

    /**
     * Troca atomicamente o token {@code presentedHash} por um novo ({@code nextHash}), na mesma família e para o mesmo usuário.
     * Com dois pedidos simultâneos para o mesmo token, exatamente um recebe {@link Rotation.Rotated}.
     */
    Rotation rotate(String presentedHash, String nextHash, Instant nextExpiresAt, Instant now);

    /** Revoga a família do token apresentado (logout); não faz nada se ele for desconhecido. */
    void revokeFamilyOf(String presentedHash);

    void revokeAllOf(UUID userId);
}
