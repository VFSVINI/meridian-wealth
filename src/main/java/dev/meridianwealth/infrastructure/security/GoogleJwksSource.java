package dev.meridianwealth.infrastructure.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Chaves públicas do Google (JWKS) buscadas pelo {@link HttpGateway}, com cache de 1 hora. Quando chega um token com um {@code kid}
 * que não está no cache (o Google rotaciona as chaves), busca de novo, no máximo uma vez por minuto, para que um atacante não
 * consiga forçar uma requisição ao Google por token falso.
 */
public class GoogleJwksSource implements JWKSource<SecurityContext> {
    public static final String CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration MIN_REFETCH = Duration.ofMinutes(1);

    private final HttpGateway http;
    private JWKSet cached;
    private Instant fetchedAt = Instant.EPOCH;

    public GoogleJwksSource(HttpGateway http) { this.http = http; }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        JWKSet set = current(Instant.now(), false);
        List<JWK> hit = selector.select(set);
        if (!hit.isEmpty()) return hit;
        set = current(Instant.now(), true);
        return selector.select(set);
    }

    private synchronized JWKSet current(Instant now, boolean forceIfAllowed) throws KeySourceException {
        boolean stale = cached == null || fetchedAt.plus(TTL).isBefore(now);
        boolean canForce = forceIfAllowed && fetchedAt.plus(MIN_REFETCH).isBefore(now);
        if (stale || canForce) {
            try {
                cached = JWKSet.parse(http.get(CERTS_URL, Map.of("Accept", "application/json")));
                fetchedAt = now;
            } catch (Exception e) {
                if (cached == null) throw new KeySourceException("Não foi possível obter as chaves públicas do Google", e);
                // mantém as chaves antigas: melhor um token válido com a chave em cache do que derrubar o login
                fetchedAt = now.minus(TTL).plus(MIN_REFETCH);
            }
        }
        return cached;
    }
}
