package dev.meridianwealth.web;

import static dev.meridianwealth.testsupport.Http.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.infrastructure.security.GoogleJwksSource;
import dev.meridianwealth.infrastructure.security.JwtTokenService;
import dev.meridianwealth.testsupport.FixtureHttpGateway;
import dev.meridianwealth.testsupport.Http;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Sobe a aplicação inteira e exercita a segurança pela borda HTTP: cadastro, login, refresh com rotação, rotas protegidas,
 * isolamento entre usuários, Login com Google (com chave RSA de teste no lugar do JWKS do Google), modo demo, CSP, cabeçalhos e CORS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "meridianwealth.crypto.stream.enabled=false", "meridianwealth.security.bcrypt-strength=4",
        "meridianwealth.security.jwt-secret=um-segredo-de-teste-com-mais-de-trinta-e-dois-caracteres",
        "meridianwealth.google.client-id=" + AuthApiIntegrationTest.CLIENT_ID, "meridianwealth.rate-limit.auth-per-minute=100000",
        "meridianwealth.rate-limit.api-per-minute=100000", "meridianwealth.security.cors-allowed-origins=https://app.exemplo.com"})
class AuthApiIntegrationTest {
    static final String CLIENT_ID = "teste-123.apps.googleusercontent.com";
    static final String SECRET = "um-segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";
    private static final String PASSWORD = "uma frase longa e boa";
    private static RSAKey googleKey;

    @BeforeAll
    static void key() throws Exception { googleKey = new RSAKeyGenerator(2048).keyID("kid-teste").generate(); }

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        HttpGateway gateway() {
            FixtureHttpGateway fixtures = new FixtureHttpGateway();
            return (url, headers) -> GoogleJwksSource.CERTS_URL.equals(url) ? new JWKSet(googleKey.toPublicJWK()).toString() : fixtures.get(url, headers);
        }
    }

    @Value("${local.server.port}")
    private int port;
    private Http http;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @BeforeEach
    void client() { http = new Http(port); }

    private static String email() { return "usuario" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@exemplo.com"; }

    private Http.Reply register(String email) { return http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Teste")); }

    private static String googleToken(JWTClaimsSet.Builder claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-teste").build(), claims.build());
        jwt.sign(new RSASSASigner(googleKey));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder googleClaims(String email) {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(CLIENT_ID).subject("sub-" + UUID.randomUUID()).claim("email", email)
                .claim("email_verified", true).claim("name", "Pessoa Google").claim("picture", "https://lh3.googleusercontent.com/a/foto")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(3000)));
    }

    @Test void configReportsWhatTheEnvironmentOffers() {
        Http.Reply r = http.get("/api/auth/config");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.get("googleLoginEnabled")).isEqualTo(true);
        assertThat(r.get("googleClientId")).as("o ID do cliente é público; o segredo nunca sai do servidor").isEqualTo(CLIENT_ID);
        assertThat(r.body()).doesNotContain("secret");
        assertThat(r.get("demoEnabled")).isEqualTo(true);
        assertThat(r.get("registrationEnabled")).isEqualTo(true);
        assertThat(r.get("passwordMinLength")).isEqualTo(12);
        assertThat(r.header("Cache-Control")).contains("no-store");
    }

    @Test void passwordFlowRegisterLoginRefreshReuseAndLogout() {
        String email = email();
        Http.Reply reg = register(email);
        assertThat(reg.status()).isEqualTo(201);
        assertThat(reg.get("tokenType")).isEqualTo("Bearer");
        assertThat(reg.get("needsSuitability")).isEqualTo(true);
        assertThat(reg.body()).as("a senha nunca volta na resposta").doesNotContain(PASSWORD).doesNotContain("passwordHash");

        Http.Reply me = http.get("/api/me", bearer((String) reg.get("accessToken")));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.get("email")).isEqualTo(email);
        assertThat(me.body()).doesNotContain("passwordHash").doesNotContain("$2");

        Http.Reply login = http.post("/api/auth/login", Map.of("email", email.toUpperCase(), "password", PASSWORD));
        assertThat(login.status()).isEqualTo(200);

        String refresh1 = (String) login.get("refreshToken");
        Http.Reply r2 = http.post("/api/auth/refresh", Map.of("refreshToken", refresh1));
        assertThat(r2.status()).isEqualTo(200);
        String refresh2 = (String) r2.get("refreshToken");
        assertThat(refresh2).isNotEqualTo(refresh1);

        Http.Reply reuse = http.post("/api/auth/refresh", Map.of("refreshToken", refresh1));
        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.get("code")).isEqualTo("REFRESH_TOKEN_REUSED");
        assertThat(http.post("/api/auth/refresh", Map.of("refreshToken", refresh2)).status()).as("a família inteira caiu").isEqualTo(401);

        String refresh3 = (String) http.post("/api/auth/login", Map.of("email", email, "password", PASSWORD)).get("refreshToken");
        assertThat(http.post("/api/auth/logout", Map.of("refreshToken", refresh3)).status()).isEqualTo(204);
        assertThat(http.post("/api/auth/refresh", Map.of("refreshToken", refresh3)).status()).isEqualTo(401);
    }

    @Test void registrationAndLoginErrorsHaveStableStatusAndCodes() {
        String email = email();
        assertThat(http.post("/api/auth/register", Map.of("email", "ruim", "password", PASSWORD)).status()).isEqualTo(400);
        Http.Reply weak = http.post("/api/auth/register", Map.of("email", email, "password", "curta"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.get("code")).isEqualTo("INVALID_INPUT");
        assertThat(register(email).status()).isEqualTo(201);
        Http.Reply dup = register(email);
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.get("code")).isEqualTo("EMAIL_REGISTERED_WITH_PASSWORD");
        Http.Reply bad = http.post("/api/auth/login", Map.of("email", email, "password", "senha errada errada"));
        assertThat(bad.status()).isEqualTo(401);
        assertThat(bad.get("error")).isEqualTo("E-mail ou senha incorretos");
        Http.Reply ghost = http.post("/api/auth/login", Map.of("email", "fantasma@exemplo.com", "password", "senha errada errada"));
        assertThat(ghost.body()).as("mesma resposta para e-mail inexistente").isEqualTo(bad.body());
        assertThat(http.post("/api/auth/login", "{nao e json").status()).isEqualTo(400);
    }

    @Test void oversizedBodiesAreRefusedBeforeBeingReadOnPublicRoutes() {
        String huge = "{\"email\":\"a@exemplo.com\",\"password\":\"" + "a".repeat(1_000_000) + "\"}";
        Http.Reply r = http.post("/api/auth/login", huge);
        assertThat(r.status()).isEqualTo(413);
        assertThat(r.get("code")).isEqualTo("BODY_TOO_LARGE");
        assertThat(http.post("/api/auth/register", huge).status()).isEqualTo(413);
        assertThat(http.post("/api/impact/pre-order", huge).status()).isEqualTo(413);
        assertThat(http.put("/api/me/profile", null, huge).status()).as("o filtro vem antes da autenticação").isEqualTo(413);
        String okSize = "{\"email\":\"a@exemplo.com\",\"password\":\"" + "a".repeat(20_000) + "\"}";
        assertThat(http.post("/api/auth/login", okSize).status()).as("20 KB ainda passa pelo filtro (e a senha longa é recusada)").isEqualTo(401);
        // sem Content-Length (chunked), o fluxo é limitado byte a byte
        Http.Reply chunked = http.postChunked("/api/auth/login", huge);
        assertThat(chunked.status()).isIn(400, 413);
        assertThat(http.get("/api/universe").status()).as("GET não é afetado").isEqualTo(200);
    }

    @Test void repeatedWrongPasswordsReturn429WithRetryAfter() {
        String email = email();
        register(email);
        for (int i = 0; i < 5; i++) assertThat(http.post("/api/auth/login", Map.of("email", email, "password", "senha errada errada")).status()).isEqualTo(401);
        Http.Reply locked = http.post("/api/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.get("code")).isEqualTo("TOO_MANY_ATTEMPTS");
        assertThat(locked.header("Retry-After")).isEqualTo("900");
    }

    @Test void protectedRoutesRejectMissingGarbageExpiredForgedAndUnsignedTokens() throws Exception {
        Http.Reply none = http.get("/api/me");
        assertThat(none.status()).isEqualTo(401);
        assertThat(none.header("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(none.get("code")).isEqualTo("UNAUTHENTICATED");
        assertThat(http.get("/api/me", bearer("lixo")).status()).isEqualTo(401);
        assertThat(http.get("/api/me", Map.of("Authorization", "Basic dXNlcjpwYXNz")).status()).isEqualTo(401);
        assertThat(http.get("/api/me/profile").status()).isEqualTo(401);
        assertThat(http.put("/api/me/profile", null, Map.of("profile", "BOLD")).status()).isEqualTo(401);

        UserAccount u = new UserAccount(UUID.randomUUID(), "x@exemplo.com", "X", null, AuthProvider.PASSWORD, "h", null, Instant.now(), null);
        JwtTokenService mine = new JwtTokenService(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = mine.issueAccess(u, Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(15)).token();
        assertThat(http.get("/api/me", bearer(expired)).status()).as("expirado").isEqualTo(401);
        String forged = new JwtTokenService(JwtTokenService.randomSecret()).issueAccess(u, Instant.now(), Duration.ofMinutes(15)).token();
        assertThat(http.get("/api/me", bearer(forged)).status()).as("segredo errado").isEqualTo(401);
        String unsigned = new PlainJWT(new PlainHeader(), new JWTClaimsSet.Builder().issuer("meridianwealth").audience("meridianwealth-api")
                .subject(u.id().toString()).expirationTime(Date.from(Instant.now().plusSeconds(600))).claim("prov", "PASSWORD").build()).serialize();
        assertThat(http.get("/api/me", bearer(unsigned)).status()).as("alg none").isEqualTo(401);
        String valid = mine.issueAccess(u, Instant.now(), Duration.ofMinutes(15)).token();
        assertThat(http.get("/api/me", bearer(valid)).status()).as("token válido de usuário que não existe mais").isEqualTo(401);
    }

    @Test void publicMarketDataStaysOpenEvenWithABadToken() {
        assertThat(http.get("/api/universe").status()).isEqualTo(200);
        assertThat(http.get("/api/universe", bearer("lixo")).status()).isEqualTo(200);
        assertThat(http.get("/api/glossary").status()).isEqualTo(200);
    }

    @Test void usersAreIsolatedFromEachOther() {
        Http.Reply a = register(email());
        Http.Reply b = register(email());
        String ta = (String) a.get("accessToken");
        String tb = (String) b.get("accessToken");
        assertThat(http.put("/api/me/profile", bearer(ta), Map.of("profile", "AGGRESSIVE")).status()).isEqualTo(200);
        assertThat(http.get("/api/me/profile", bearer(tb)).get("needsSuitability")).as("B não herda o perfil de A").isEqualTo(true);
        assertThat(http.put("/api/me/profile", bearer(tb), Map.of("profile", "CONSERVATIVE")).status()).isEqualTo(200);
        assertThat(http.get("/api/me/profile", bearer(ta)).get("profile")).isEqualTo("AGGRESSIVE");
        assertThat(http.get("/api/me/profile", bearer(tb)).get("profile")).isEqualTo("CONSERVATIVE");
        String bId = (String) ((Map<?, ?>) b.get("user")).get("id");
        Http.Reply spoof = http.get("/api/me/profile?userId=" + bId, bearer(ta));
        assertThat(spoof.get("profile")).as("o dono vem do token, nunca do parâmetro").isEqualTo("AGGRESSIVE");
        assertThat(http.get("/api/me/" + bId, bearer(ta)).status()).isEqualTo(404);
        assertThat(http.put("/api/me/profile", bearer(ta), Map.of("profile", "NAO_EXISTE")).status()).isEqualTo(400);
    }

    @Test void googleLoginProvisionsAnAccountAndIssuesTheInternalToken() throws Exception {
        String email = email();
        Http.Reply r = http.post("/api/auth/google", Map.of("idToken", googleToken(googleClaims(email))));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.get("newUser")).isEqualTo(true);
        assertThat(r.get("needsSuitability")).isEqualTo(true);
        assertThat(r.obj("user").get("provider")).isEqualTo("GOOGLE");
        assertThat(r.obj("user").get("avatarUrl")).isEqualTo("https://lh3.googleusercontent.com/a/foto");
        assertThat(r.get("refreshToken")).isNotNull();
        assertThat(http.get("/api/me", bearer((String) r.get("accessToken"))).get("email")).isEqualTo(email);
        assertThat(http.post("/api/auth/refresh", Map.of("refreshToken", r.get("refreshToken"))).status()).as("mesmo refresh token da conta com senha").isEqualTo(200);
    }

    @Test void googleRejectsWrongAudienceUnverifiedEmailExpiredAndForgedTokens() throws Exception {
        String email = email();
        assertThat(http.post("/api/auth/google", Map.of("idToken", googleToken(googleClaims(email).audience("outro-client")))).status()).isEqualTo(401);
        assertThat(http.post("/api/auth/google", Map.of("idToken", googleToken(googleClaims(email).claim("email_verified", false)))).status()).isEqualTo(401);
        assertThat(http.post("/api/auth/google", Map.of("idToken", googleToken(googleClaims(email).expirationTime(Date.from(Instant.now().minusSeconds(600)))))).status()).isEqualTo(401);
        RSAKey stranger = new RSAKeyGenerator(2048).keyID("kid-teste").generate();
        SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-teste").build(), googleClaims(email).build());
        forged.sign(new RSASSASigner(stranger));
        Http.Reply f = http.post("/api/auth/google", Map.of("idToken", forged.serialize()));
        assertThat(f.status()).isEqualTo(401);
        assertThat(f.get("code")).isEqualTo("INVALID_GOOGLE_TOKEN");
        assertThat(f.body()).as("o motivo exato não vaza").doesNotContain("assinatura").doesNotContain("audiência");
        assertThat(http.post("/api/auth/google", Map.of("idToken", "")).status()).isEqualTo(401);
        assertThat(http.get("/api/me", bearer("x")).status()).isEqualTo(401);
    }

    @Test void googleNeverLinksToAnExistingPasswordAccountSilently() throws Exception {
        String email = email();
        assertThat(register(email).status()).isEqualTo(201);
        Http.Reply r = http.post("/api/auth/google", Map.of("idToken", googleToken(googleClaims(email))));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.get("code")).isEqualTo("EMAIL_REGISTERED_WITH_PASSWORD");
        assertThat(r.body()).doesNotContain("accessToken");
        assertThat(http.post("/api/auth/login", Map.of("email", email, "password", PASSWORD)).status()).as("a conta com senha segue intacta").isEqualTo(200);
    }

    @Test void demoSessionWorksWithoutRegistrationAndHasNoRefresh() {
        Http.Reply d = http.post("/api/auth/demo", null);
        assertThat(d.status()).isEqualTo(200);
        assertThat(d.get("demo")).isEqualTo(true);
        assertThat(d.get("refreshToken")).isNull();
        String t = (String) d.get("accessToken");
        Http.Reply me = http.get("/api/me", bearer(t));
        assertThat(me.get("demo")).isEqualTo(true);
        assertThat(String.valueOf(me.get("email"))).endsWith("@demo.invalid");
        assertThat(http.put("/api/me/profile", bearer(t), Map.of("profile", "MODERATE")).status()).isEqualTo(200);
    }

    @Test void everyResponseCarriesTheSecurityHeadersAndAStrictCsp() {
        Http.Reply page = http.get("/");
        assertThat(page.status()).isEqualTo(200);
        String csp = page.header("Content-Security-Policy");
        assertThat(csp).contains("default-src 'self'").contains("script-src 'self'").contains("frame-ancestors 'none'").contains("object-src 'none'")
                .contains("base-uri 'none'").contains("connect-src 'self' ws://localhost:" + port).doesNotContain("unsafe-eval");
        assertThat(csp).as("script embutido continua proibido").doesNotContain("script-src 'self' 'unsafe-inline'");
        assertThat(csp).as("com o Google configurado, só o script e o frame do Identity Services são liberados").contains("script-src 'self' https://accounts.google.com/gsi/client;")
                .contains("frame-src https://accounts.google.com/gsi/").doesNotContain("https://accounts.google.com ").doesNotContain("*");
        assertThat(page.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(page.header("X-Frame-Options")).isEqualTo("DENY");
        assertThat(page.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(page.header("Permissions-Policy")).contains("geolocation=()");
        assertThat(page.header("Strict-Transport-Security")).as("só em HTTPS").isNull();
        assertThat(http.get("/api/universe").header("Content-Security-Policy")).isNotNull();
        assertThat(http.get("/", Map.of("X-Forwarded-Proto", "https")).header("Strict-Transport-Security")).as("sem proxy confiável, o cabeçalho é ignorado").isNull();
        assertThat(http.get("/v3/api-docs").header("Content-Security-Policy")).as("a documentação da API usa script embutido").isNull();
    }

    @Test void corsIsLimitedToTheConfiguredOrigins() {
        Http.Reply ok = http.send("OPTIONS", "/api/auth/login", Map.of("Origin", "https://app.exemplo.com", "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "content-type"), null);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.header("Access-Control-Allow-Origin")).isEqualTo("https://app.exemplo.com");
        assertThat(ok.header("Access-Control-Allow-Credentials")).isNull();
        Http.Reply evil = http.send("OPTIONS", "/api/auth/login", Map.of("Origin", "https://evil.example", "Access-Control-Request-Method", "POST"), null);
        assertThat(evil.status()).isEqualTo(403);
        assertThat(evil.header("Access-Control-Allow-Origin")).isNull();
        Http.Reply evilGet = http.get("/api/auth/config", Map.of("Origin", "https://evil.example"));
        assertThat(evilGet.header("Access-Control-Allow-Origin")).isNull();
        Http.Reply wildcard = http.send("OPTIONS", "/api/auth/login", Map.of("Origin", "null", "Access-Control-Request-Method", "POST"), null);
        assertThat(wildcard.header("Access-Control-Allow-Origin")).isNull();
    }

    @Test void theStreamWebSocketRefusesForeignOrigins() throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        WebSocket.Listener quiet = new WebSocket.Listener() {};
        try {
            c.newWebSocketBuilder().header("Origin", "https://evil.example").buildAsync(URI.create("ws://localhost:" + port + "/ws/stream"), quiet).join();
            org.junit.jupiter.api.Assertions.fail("o handshake de outra origem deveria falhar");
        } catch (CompletionException expected) {
            assertThat(expected.getCause()).isInstanceOf(java.net.http.WebSocketHandshakeException.class);
            assertThat(((java.net.http.WebSocketHandshakeException) expected.getCause()).getResponse().statusCode()).isEqualTo(403);
        }
        WebSocket ok = c.newWebSocketBuilder().header("Origin", "http://localhost:" + port).buildAsync(URI.create("ws://localhost:" + port + "/ws/stream"), quiet).join();
        ok.sendClose(WebSocket.NORMAL_CLOSURE, "fim").join();
    }
}
