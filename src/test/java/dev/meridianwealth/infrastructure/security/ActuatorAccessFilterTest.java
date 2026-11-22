package dev.meridianwealth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ActuatorAccessFilterTest {
    private static final String TOKEN = "t".repeat(32);

    private static MockHttpServletResponse run(ActuatorAccessFilter f, String uri, String auth) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        if (auth != null) req.addHeader("Authorization", auth);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test void withoutAConfiguredTokenTheMetricsDoNotExist() throws Exception {
        ActuatorAccessFilter f = new ActuatorAccessFilter("");
        assertThat(run(f, "/actuator/prometheus", null).getStatus()).isEqualTo(404);
        assertThat(run(f, "/actuator/metrics", "Bearer " + TOKEN).getStatus()).isEqualTo(404);
        assertThat(run(f, "/actuator/metrics/jvm.memory.used", null).getStatus()).isEqualTo(404);
    }

    @Test void withATokenOnlyTheRightBearerPasses() throws Exception {
        ActuatorAccessFilter f = new ActuatorAccessFilter(TOKEN);
        assertThat(run(f, "/actuator/prometheus", null).getStatus()).isEqualTo(401);
        assertThat(run(f, "/actuator/prometheus", "Bearer errado").getStatus()).isEqualTo(401);
        assertThat(run(f, "/actuator/prometheus", "Basic " + TOKEN).getStatus()).isEqualTo(401);
        assertThat(run(f, "/actuator/prometheus", "Bearer " + TOKEN).getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(run(f, "/actuator/metrics/jvm.memory.used", "bearer " + TOKEN).getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test void healthAndOtherPathsStayOutsideTheGuard() throws Exception {
        ActuatorAccessFilter f = new ActuatorAccessFilter(TOKEN);
        assertThat(run(f, "/actuator/health", null).getStatus()).isEqualTo(200);
        assertThat(run(f, "/actuator/health/readiness", null).getStatus()).isEqualTo(200);
        assertThat(run(f, "/api/overview", null).getStatus()).isEqualTo(200);
        assertThat(run(f, "/actuator/metricsX", null).getStatus()).as("prefixo parecido não é o mesmo caminho").isEqualTo(200);
    }

    @Test void aShortTokenIsRejectedAtStartup() {
        assertThatThrownBy(() -> new ActuatorAccessFilter("curto")).isInstanceOf(IllegalStateException.class).hasMessageContaining("24");
    }
}
