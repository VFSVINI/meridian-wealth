package dev.meridianwealth.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import dev.meridianwealth.application.port.GoogleIdentityVerifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Valida o ID token do Google. Aceita só RS256 (a assinatura é conferida contra as chaves públicas do Google) e então confere, com
 * o relógio recebido, o emissor, a audiência (o {@code MW_GOOGLE_CLIENT_ID}), a validade, o {@code sub}, o e-mail e o
 * {@code email_verified}. Sem client ID configurado o provedor fica desligado e nenhum token é aceito.
 *
 * <p>Limite assumido: o token não carrega {@code nonce} do cliente, então um ID token válido e ainda não expirado pode ser
 * reapresentado; a janela é a validade do token (cerca de uma hora) e o risco é o de quem já o roubou.
 */
public class GoogleIdTokenVerifier implements GoogleIdentityVerifier {
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration SKEW = Duration.ofSeconds(60);

    private final String clientId;
    private final DefaultJWTProcessor<SecurityContext> processor;

    public GoogleIdTokenVerifier(String clientId, JWKSource<SecurityContext> keys) {
        this.clientId = clientId == null || clientId.isBlank() ? null : clientId.trim();
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        // o relógio do Nimbus não é injetável: a verificação de validade é feita à mão, abaixo, com o "now" recebido
        this.processor.setJWTClaimsSetVerifier((claims, context) -> {});
    }

    @Override
    public boolean enabled() { return clientId != null; }

    @Override
    public GoogleIdentity verify(String idToken, Instant now) {
        if (!enabled()) throw new InvalidGoogleToken("Provedor não configurado");
        JWTClaimsSet c;
        try {
            c = processor.process(idToken, null);
        } catch (Exception e) {
            throw new InvalidGoogleToken("Assinatura ou formato inválido", e);
        }
        try {
            if (c.getIssuer() == null || !ISSUERS.contains(c.getIssuer())) throw new InvalidGoogleToken("Emissor inválido");
            List<String> aud = c.getAudience();
            if (aud == null || !aud.contains(clientId)) throw new InvalidGoogleToken("Audiência inválida");
            String azp = c.getStringClaim("azp");
            if (aud.size() > 1 && !clientId.equals(azp)) throw new InvalidGoogleToken("Parte autorizada inválida");
            Date exp = c.getExpirationTime();
            if (exp == null || !now.minus(SKEW).isBefore(exp.toInstant())) throw new InvalidGoogleToken("Token expirado");
            Date iat = c.getIssueTime();
            if (iat != null && iat.toInstant().isAfter(now.plus(SKEW))) throw new InvalidGoogleToken("Token emitido no futuro");
            Date nbf = c.getNotBeforeTime();
            if (nbf != null && nbf.toInstant().isAfter(now.plus(SKEW))) throw new InvalidGoogleToken("Token ainda não é válido");
            String sub = c.getSubject();
            String email = c.getStringClaim("email");
            if (sub == null || sub.isBlank() || email == null || email.isBlank()) throw new InvalidGoogleToken("Token sem identidade");
            Object verified = c.getClaim("email_verified");
            boolean ok = Boolean.TRUE.equals(verified) || (verified instanceof String s && s.equalsIgnoreCase("true"));
            if (!ok) throw new InvalidGoogleToken("E-mail não verificado pelo Google");
            return new GoogleIdentity(sub, email, c.getStringClaim("name"), c.getStringClaim("picture"));
        } catch (java.text.ParseException e) {
            throw new InvalidGoogleToken("Claims inválidos", e);
        }
    }
}
