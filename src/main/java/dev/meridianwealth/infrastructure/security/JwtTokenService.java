package dev.meridianwealth.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.port.TokenService;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Token de acesso JWT assinado com HS256 (segredo simétrico de pelo menos 256 bits) e refresh token opaco. A validação aceita
 * <b>só</b> HS256 (nunca "none" nem RS256 com a chave tratada como segredo), confere emissor, audiência, validade e o formato do
 * {@code sub}, e devolve vazio para qualquer falha, sem dizer qual.
 */
public class JwtTokenService implements TokenService {
    public static final String ISSUER = "meridianwealth";
    public static final String AUDIENCE = "meridianwealth-api";
    public static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 2048;
    private static final String PROVIDER_CLAIM = "prov";

    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public JwtTokenService(byte[] secret) {
        if (secret == null || secret.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("O segredo do JWT precisa ter pelo menos " + MIN_SECRET_BYTES + " bytes");
        }
        this.secret = secret.clone();
    }

    /** Segredo aleatório de 48 bytes para quando {@code MW_JWT_SECRET} não está definido (os tokens não sobrevivem a um reinício). */
    public static byte[] randomSecret() {
        byte[] s = new byte[48];
        new SecureRandom().nextBytes(s);
        return s;
    }

    @Override
    public AccessToken issueAccess(UserAccount user, Instant now, Duration ttl) {
        Instant exp = now.plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject(user.id().toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(exp)).jwtID(UUID.randomUUID().toString())
                .claim(PROVIDER_CLAIM, user.provider().name()).build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return new AccessToken(jwt.serialize(), exp);
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o token", e);
        }
    }

    @Override
    public Optional<AuthPrincipal> verifyAccess(String token, Instant now) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) return Optional.empty();
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) return Optional.empty();
            if (!jwt.verify(new MACVerifier(secret))) return Optional.empty();
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            List<String> aud = c.getAudience();
            if (!ISSUER.equals(c.getIssuer()) || aud == null || !aud.contains(AUDIENCE)) return Optional.empty();
            Date exp = c.getExpirationTime();
            Date nbf = c.getNotBeforeTime();
            if (exp == null || !now.isBefore(exp.toInstant())) return Optional.empty();
            if (nbf != null && now.isBefore(nbf.toInstant())) return Optional.empty();
            return Optional.of(new AuthPrincipal(UUID.fromString(c.getSubject()), AuthProvider.valueOf(c.getStringClaim(PROVIDER_CLAIM))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String newRefreshToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    @Override
    public String hashRefreshToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String newFamilyId() { return UUID.randomUUID().toString(); }
}
