package dev.meridianwealth.config;

import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.application.auth.LoginAttemptLimiter;
import dev.meridianwealth.application.port.GoogleIdentityVerifier;
import dev.meridianwealth.application.port.PasswordHasher;
import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.TokenService;
import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.infrastructure.security.BcryptPasswordHasher;
import dev.meridianwealth.infrastructure.security.ClientIpResolver;
import dev.meridianwealth.infrastructure.security.GoogleIdTokenVerifier;
import dev.meridianwealth.infrastructure.security.GoogleJwksSource;
import dev.meridianwealth.infrastructure.security.InMemoryRefreshTokenStore;
import dev.meridianwealth.infrastructure.security.InMemoryUserStore;
import dev.meridianwealth.infrastructure.security.JwtTokenService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Composição da segurança (ADR 013): autenticação própria com JWT, Login com Google opcional, CORS restritivo e proxies
 * confiáveis. Nada aqui tem valor padrão secreto: sem {@code MW_JWT_SECRET} o segredo é aleatório a cada partida, e sem
 * {@code MW_GOOGLE_CLIENT_ID} o Login com Google fica desligado e a aplicação sobe normalmente.
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {
    private static final Logger LOG = Logger.getLogger(SecurityConfig.class.getName());

    private final List<String> corsOrigins;

    public SecurityConfig(@Value("${meridianwealth.security.cors-allowed-origins:}") String origins) {
        this.corsOrigins = split(origins);
        for (String o : corsOrigins) {
            if (o.contains("*") || !(o.startsWith("https://") || o.startsWith("http://"))) {
                throw new IllegalStateException("MW_CORS_ORIGINS aceita só origens exatas http(s) (sem curinga): " + o);
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsOrigins.isEmpty()) return; // sem lista: só a própria origem (nenhum cabeçalho CORS é emitido)
        registry.addMapping("/api/**").allowedOrigins(corsOrigins.toArray(String[]::new)).allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type").allowCredentials(false).maxAge(600);
    }

    @Bean
    Clock utcClock() { return Clock.systemUTC(); }

    @Bean
    ClientIpResolver clientIpResolver(@Value("${meridianwealth.security.trusted-proxies:}") String trusted) {
        return new ClientIpResolver(split(trusted));
    }

    @Bean
    TokenService tokenService(@Value("${meridianwealth.security.jwt-secret:}") String secret) {
        if (secret.isBlank()) {
            LOG.log(Level.WARNING, "MW_JWT_SECRET não definido: usando segredo aleatório desta partida (os tokens não sobrevivem a um reinício)");
            return new JwtTokenService(JwtTokenService.randomSecret());
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < JwtTokenService.MIN_SECRET_BYTES) {
            throw new IllegalStateException("MW_JWT_SECRET precisa ter pelo menos " + JwtTokenService.MIN_SECRET_BYTES + " caracteres");
        }
        return new JwtTokenService(bytes);
    }

    @Bean
    PasswordHasher passwordHasher(@Value("${meridianwealth.security.bcrypt-strength:12}") int strength) { return new BcryptPasswordHasher(strength); }

    @Bean
    UserStore userStore() { return new InMemoryUserStore(); }

    @Bean
    RefreshTokenStore refreshTokenStore() { return new InMemoryRefreshTokenStore(); }

    @Bean
    GoogleIdentityVerifier googleIdentityVerifier(@Value("${meridianwealth.google.client-id:}") String clientId, HttpGateway http) {
        if (clientId.isBlank()) LOG.log(Level.INFO, "MW_GOOGLE_CLIENT_ID não definido: Login com Google desligado");
        return new GoogleIdTokenVerifier(clientId, new GoogleJwksSource(http));
    }

    @Bean
    AuthService authService(UserStore users, RefreshTokenStore refresh, PasswordHasher hasher, TokenService tokens, GoogleIdentityVerifier google,
                            Clock utcClock, @Value("${meridianwealth.auth.access-ttl-minutes:15}") long accessMinutes,
                            @Value("${meridianwealth.auth.refresh-ttl-days:7}") long refreshDays,
                            @Value("${meridianwealth.auth.demo-ttl-minutes:60}") long demoMinutes,
                            @Value("${meridianwealth.auth.max-users:10000}") int maxUsers, @Value("${meridianwealth.auth.max-demo-users:500}") int maxDemo,
                            @Value("${meridianwealth.auth.demo-enabled:true}") boolean demoEnabled,
                            @Value("${meridianwealth.auth.registration-enabled:true}") boolean registrationEnabled,
                            @Value("${meridianwealth.auth.login-max-failures:5}") int maxFailures,
                            @Value("${meridianwealth.auth.login-max-failures-per-ip:50}") int maxFailuresPerIp,
                            @Value("${meridianwealth.auth.login-window-minutes:15}") long windowMinutes) {
        AuthService.Settings settings = new AuthService.Settings(Duration.ofMinutes(accessMinutes), Duration.ofDays(refreshDays), Duration.ofMinutes(demoMinutes),
                maxUsers, maxDemo, demoEnabled, registrationEnabled);
        return new AuthService(users, refresh, hasher, tokens, google, new LoginAttemptLimiter(maxFailures, Duration.ofMinutes(windowMinutes), utcClock),
                new LoginAttemptLimiter(maxFailuresPerIp, Duration.ofMinutes(windowMinutes), utcClock), settings, utcClock);
    }

    private static List<String> split(String csv) { return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(); }
}
