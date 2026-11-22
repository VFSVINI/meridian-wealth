package dev.meridianwealth.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter(true);

    private MockHttpServletResponse run(String header, List<String> seenInChain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/universe");
        if (header != null) req.addHeader(CorrelationIdFilter.HEADER, header);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                seenInChain.add(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });
        return res;
    }

    @Test void aValidClientIdIsKeptReturnedAndVisibleToTheLogsDuringTheRequest() throws Exception {
        List<String> seen = new ArrayList<>();
        MockHttpServletResponse res = run("pedido-2026-11-21.abc_123", seen);
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("pedido-2026-11-21.abc_123");
        assertThat(seen).containsExactly("pedido-2026-11-21.abc_123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).as("o MDC é limpo no fim").isNull();
    }

    @Test void withoutAClientIdAFreshUuidIsGeneratedForEachRequest() throws Exception {
        List<String> seen = new ArrayList<>();
        String a = run(null, seen).getHeader(CorrelationIdFilter.HEADER);
        String b = run(null, seen).getHeader(CorrelationIdFilter.HEADER);
        assertThat(UUID.fromString(a)).isNotNull();
        assertThat(a).isNotEqualTo(b);
        assertThat(seen).containsExactly(a, b);
    }

    @Test void anInvalidClientIdIsDiscardedSoItCannotInjectTextIntoTheLogs() throws Exception {
        for (String bad : new String[] {"curto", "x".repeat(65), "tem espaço no meio", "linha1\r\nINFO falso", "a\nb-12345678", "<script>alert(1)</script>", "id-com-%0d%0a-1234", "çãõ-12345678", ""}) {
            List<String> seen = new ArrayList<>();
            String used = run(bad, seen).getHeader(CorrelationIdFilter.HEADER);
            assertThat(used).as("entrada: " + bad.replace("\r", "\r").replace("\n", "\n")).isNotEqualTo(bad).matches("[0-9a-f-]{36}");
            assertThat(seen.get(0)).isEqualTo(used).doesNotContain("\n").doesNotContain("\r");
        }
        assertThat(CorrelationIdFilter.resolve("12345678")).isEqualTo("12345678");
        assertThat(CorrelationIdFilter.resolve("x".repeat(64))).hasSize(64);
    }

    @Test void theMdcIsClearedEvenWhenTheRequestFails() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
        assertThatThrownBy(() -> filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                throw new IllegalStateException("falha no meio");
            }
        })).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
