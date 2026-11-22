package dev.meridianwealth.application.port;

import java.time.Instant;

/**
 * Valida o ID token do Google. Só devolve a identidade se a assinatura bater com as chaves públicas do Google, o emissor for o
 * Google, a audiência for o client ID configurado, o token não tiver expirado e o e-mail estiver verificado.
 */
public interface GoogleIdentityVerifier {
    record GoogleIdentity(String subject, String email, String name, String pictureUrl) {}

    /** O token é inválido por qualquer motivo (a mensagem não detalha qual, para não ajudar quem ataca). */
    class InvalidGoogleToken extends RuntimeException {
        public InvalidGoogleToken(String message) { super(message); }

        public InvalidGoogleToken(String message, Throwable cause) { super(message, cause); }
    }

    /** O provedor está configurado neste ambiente (há {@code MW_GOOGLE_CLIENT_ID})? */
    boolean enabled();

    GoogleIdentity verify(String idToken, Instant now);
}
