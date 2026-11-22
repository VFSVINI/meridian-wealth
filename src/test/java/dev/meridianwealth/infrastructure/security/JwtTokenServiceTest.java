package dev.meridianwealth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private final byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private final JwtTokenService tokens = new JwtTokenService(secret);
    private final UserAccount user = new UserAccount(UUID.randomUUID(), "ana@exemplo.com", "Ana", null, AuthProvider.PASSWORD, "hash", null, NOW, null);

    private String sign(JWTClaimsSet claims, byte[] key, JWSAlgorithm alg) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(alg), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder good() {
        return new JWTClaimsSet.Builder().issuer(JwtTokenService.ISSUER).audience(JwtTokenService.AUDIENCE).subject(user.id().toString())
                .expirationTime(Date.from(NOW.plusSeconds(600))).claim("prov", "PASSWORD");
    }

    @Test void roundTripCarriesTheUserAndTheProvider() {
        var t = tokens.issueAccess(user, NOW, Duration.ofMinutes(15));
        assertThat(t.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(tokens.verifyAccess(t.token(), NOW.plusSeconds(60))).contains(new AuthPrincipal(user.id(), AuthProvider.PASSWORD));
    }

    @Test void expiredAndNotYetValidTokensAreRejected() {
        var t = tokens.issueAccess(user, NOW, Duration.ofMinutes(15));
        assertThat(tokens.verifyAccess(t.token(), NOW.plus(Duration.ofMinutes(15)))).as("no instante exato da expiração").isEmpty();
        assertThat(tokens.verifyAccess(t.token(), NOW.plus(Duration.ofHours(2)))).isEmpty();
        assertThat(tokens.verifyAccess(t.token(), NOW.minusSeconds(120))).as("antes do nbf").isEmpty();
    }

    @Test void aTamperedPayloadOrAnotherSecretIsRejected() throws Exception {
        String valid = tokens.issueAccess(user, NOW, Duration.ofMinutes(15)).token();
        String[] p = valid.split("\\.");
        String evil = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8).replace(user.id().toString(), UUID.randomUUID().toString());
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(evil.getBytes(StandardCharsets.UTF_8)) + "." + p[2];
        assertThat(tokens.verifyAccess(forged, NOW)).isEmpty();
        String other = sign(good().build(), "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8), JWSAlgorithm.HS256);
        assertThat(tokens.verifyAccess(other, NOW)).isEmpty();
        assertThat(tokens.verifyAccess(sign(good().build(), secret, JWSAlgorithm.HS256), NOW)).as("controle: assinado com o segredo certo").isPresent();
    }

    @Test void onlyHs256IsAcceptedNeverNoneOrOtherAlgorithms() throws Exception {
        PlainJWT none = new PlainJWT(new PlainHeader(), good().build());
        assertThat(tokens.verifyAccess(none.serialize(), NOW)).as("alg none").isEmpty();
        assertThat(tokens.verifyAccess(sign(good().build(), (new String(secret, StandardCharsets.UTF_8) + new String(secret, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8), JWSAlgorithm.HS512), NOW)).as("HS512").isEmpty();
        String stripped = sign(good().build(), secret, JWSAlgorithm.HS256).split("\\.")[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(good().build().toString().getBytes(StandardCharsets.UTF_8)) + ".";
        assertThat(tokens.verifyAccess(stripped, NOW)).as("assinatura removida").isEmpty();
    }

    @Test void issuerAudienceSubjectAndProviderMustBeRight() throws Exception {
        assertThat(tokens.verifyAccess(sign(good().issuer("outro").build(), secret, JWSAlgorithm.HS256), NOW)).isEmpty();
        assertThat(tokens.verifyAccess(sign(good().audience("outra-api").build(), secret, JWSAlgorithm.HS256), NOW)).isEmpty();
        assertThat(tokens.verifyAccess(sign(good().subject("nao-e-uuid").build(), secret, JWSAlgorithm.HS256), NOW)).isEmpty();
        assertThat(tokens.verifyAccess(sign(good().claim("prov", "ADMIN").build(), secret, JWSAlgorithm.HS256), NOW)).isEmpty();
        assertThat(tokens.verifyAccess(sign(good().expirationTime(null).build(), secret, JWSAlgorithm.HS256), NOW)).as("sem exp").isEmpty();
    }

    @Test void garbageNullAndOversizedInputsAreRejectedWithoutErrors() {
        for (String bad : new String[] {null, "", "a", "a.b.c", "....", "Bearer x", "x".repeat(5000)}) {
            assertThat(tokens.verifyAccess(bad, NOW)).as("entrada: " + (bad == null ? "null" : bad.substring(0, Math.min(10, bad.length())))).isEmpty();
        }
    }

    @Test void refreshTokensAreRandomUrlSafeAndHashedDeterministically() {
        String a = tokens.newRefreshToken();
        String b = tokens.newRefreshToken();
        assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_-]{43}");
        assertThat(tokens.hashRefreshToken(a)).isEqualTo(tokens.hashRefreshToken(a)).hasSize(64).isNotEqualTo(tokens.hashRefreshToken(b)).doesNotContain(a);
        assertThat(tokens.newFamilyId()).isNotEqualTo(tokens.newFamilyId());
    }

    @Test void theSecretMustHaveAtLeast256Bits() {
        assertThatThrownBy(() -> new JwtTokenService("curto".getBytes(StandardCharsets.UTF_8))).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtTokenService(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(JwtTokenService.randomSecret()).hasSize(48).isNotEqualTo(JwtTokenService.randomSecret());
    }
}
