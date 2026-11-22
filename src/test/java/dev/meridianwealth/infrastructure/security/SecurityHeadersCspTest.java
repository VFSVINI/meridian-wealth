package dev.meridianwealth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** A CSP só abre espaço para o Google quando o Login com Google está configurado, e nunca além do necessário. */
class SecurityHeadersCspTest {
    private static String csp(String clientId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Host", "localhost:8080");
        return new SecurityHeadersFilter(new ClientIpResolver(List.of()), clientId).csp(req);
    }

    @Test void withoutGoogleNothingExternalIsAllowed() {
        for (String empty : new String[] {"", "  ", null}) {
            String c = csp(empty);
            assertThat(c).doesNotContain("google").doesNotContain("frame-src").doesNotContain("*");
            assertThat(c).contains("script-src 'self';").contains("connect-src 'self' ws://localhost:8080;");
        }
    }

    @Test void withGoogleOnlyTheIdentityServicesEndpointsAreAdded() {
        String c = csp("abc.apps.googleusercontent.com");
        assertThat(c).contains("script-src 'self' https://accounts.google.com/gsi/client;").contains("style-src 'self' https://accounts.google.com/gsi/style;")
                .contains("connect-src 'self' ws://localhost:8080 https://accounts.google.com/gsi/;").contains("frame-src https://accounts.google.com/gsi/;")
                .contains("img-src 'self' data:;").contains("frame-ancestors 'none'").doesNotContain("*").doesNotContain("unsafe-eval");
    }
}
