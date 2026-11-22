package dev.meridianwealth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import dev.meridianwealth.application.port.GoogleIdentityVerifier.GoogleIdentity;
import dev.meridianwealth.application.port.GoogleIdentityVerifier.InvalidGoogleToken;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Os tokens do Google são fabricados com uma chave RSA de teste, cujo JWKS é entregue ao verificador no lugar do JWKS do Google. */
class GoogleIdTokenVerifierTest {
    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private static RSAKey googleKey;
    private static RSAKey strangerKey;
    private static GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void keys() throws JOSEException {
        googleKey = new RSAKeyGenerator(2048).keyID("google-kid-1").generate();
        strangerKey = new RSAKeyGenerator(2048).keyID("google-kid-1").generate();
        verifier = new GoogleIdTokenVerifier(CLIENT_ID, new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK())));
    }

    private static JWTClaimsSet.Builder good() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(CLIENT_ID).subject("sub-123").claim("email", "bia@exemplo.com")
                .claim("email_verified", true).claim("name", "Bia Souza").claim("picture", "https://lh3.googleusercontent.com/a/x")
                .issueTime(Date.from(NOW.minusSeconds(60))).expirationTime(Date.from(NOW.plusSeconds(3000)));
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static void assertRejected(JWTClaimsSet claims) throws JOSEException {
        String token = sign(claims, googleKey);
        assertThatThrownBy(() -> verifier.verify(token, NOW)).isInstanceOf(InvalidGoogleToken.class);
    }

    @Test void aValidTokenYieldsTheIdentity() throws Exception {
        GoogleIdentity id = verifier.verify(sign(good().build(), googleKey), NOW);
        assertThat(id).isEqualTo(new GoogleIdentity("sub-123", "bia@exemplo.com", "Bia Souza", "https://lh3.googleusercontent.com/a/x"));
        assertThat(verifier.enabled()).isTrue();
    }

    @Test void bothGoogleIssuerFormsAreAccepted() throws Exception {
        assertThat(verifier.verify(sign(good().issuer("accounts.google.com").build(), googleKey), NOW).subject()).isEqualTo("sub-123");
    }

    @Test void aTokenForAnotherAudienceIsRejected() throws Exception {
        assertRejected(good().audience("outro-client.apps.googleusercontent.com").build());
        assertRejected(good().audience(List.of("outro", "mais-um")).build());
    }

    @Test void aTokenFromAnotherIssuerIsRejected() throws Exception {
        assertRejected(good().issuer("https://evil.example").build());
        assertRejected(good().issuer("https://accounts.google.com.evil.example").build());
        assertRejected(good().issuer((String) null).build());
    }

    @Test void expiredTokensAreRejectedBeyondTheSixtySecondSkew() throws Exception {
        assertRejected(good().expirationTime(Date.from(NOW.minusSeconds(61))).build());
        assertThat(verifier.verify(sign(good().expirationTime(Date.from(NOW.minusSeconds(30))).build(), googleKey), NOW).subject())
                .as("dentro da tolerância de relógio").isEqualTo("sub-123");
        assertRejected(good().expirationTime(null).build());
    }

    @Test void tokensIssuedInTheFutureAreRejected() throws Exception {
        assertRejected(good().issueTime(Date.from(NOW.plusSeconds(600))).build());
        assertRejected(good().notBeforeTime(Date.from(NOW.plusSeconds(600))).build());
    }

    @Test void anUnverifiedEmailIsRejected() throws Exception {
        assertRejected(good().claim("email_verified", false).build());
        assertRejected(good().claim("email_verified", "false").build());
        assertRejected(good().claim("email_verified", null).build());
        assertThat(verifier.verify(sign(good().claim("email_verified", "true").build(), googleKey), NOW).email()).as("o Google às vezes manda string").isEqualTo("bia@exemplo.com");
    }

    @Test void aTokenWithoutIdentityIsRejected() throws Exception {
        assertRejected(good().subject(null).build());
        assertRejected(good().claim("email", null).build());
        assertRejected(good().claim("email", "  ").build());
    }

    @Test void multiAudienceTokensNeedTheRightAuthorizedParty() throws Exception {
        assertRejected(good().audience(List.of(CLIENT_ID, "outro")).claim("azp", "outro").build());
        assertThat(verifier.verify(sign(good().audience(List.of(CLIENT_ID, "outro")).claim("azp", CLIENT_ID).build(), googleKey), NOW).subject()).isEqualTo("sub-123");
    }

    @Test void aTokenSignedByAnotherKeyIsRejectedEvenWithTheSameKid() throws Exception {
        String forged = sign(good().build(), strangerKey);
        assertThatThrownBy(() -> verifier.verify(forged, NOW)).isInstanceOf(InvalidGoogleToken.class);
    }

    @Test void unsignedAndSymmetricAlgorithmConfusionTokensAreRejected() throws Exception {
        String none = new PlainJWT(new PlainHeader(), good().build()).serialize();
        assertThatThrownBy(() -> verifier.verify(none, NOW)).isInstanceOf(InvalidGoogleToken.class);
        // ataque clássico: assinar com HS256 usando a chave pública como segredo
        SignedJWT hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("google-kid-1").build(), good().build());
        hs.sign(new MACSigner(googleKey.toPublicJWK().toJSONString().repeat(2).getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> verifier.verify(hs.serialize(), NOW)).isInstanceOf(InvalidGoogleToken.class);
    }

    @Test void garbageIsRejectedWithoutLeakingInternals() {
        for (String bad : new String[] {"", "x", "a.b.c", "....", "eyJhbGciOiJSUzI1NiJ9.e30."}) {
            assertThatThrownBy(() -> verifier.verify(bad, NOW)).isInstanceOf(InvalidGoogleToken.class);
        }
    }

    @Test void withoutAClientIdTheProviderIsOffAndNothingIsAccepted() throws Exception {
        for (String blank : new String[] {null, "", "   "}) {
            GoogleIdTokenVerifier off = new GoogleIdTokenVerifier(blank, new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK())));
            assertThat(off.enabled()).isFalse();
            String token = sign(good().build(), googleKey);
            assertThatThrownBy(() -> off.verify(token, NOW)).isInstanceOf(InvalidGoogleToken.class);
        }
    }
}
