package dev.meridianwealth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClientIpResolverTest {
    private final ClientIpResolver behindProxy = new ClientIpResolver(List.of("10.0.0.0/8", "172.18.0.5", "::1"));

    @Test void withoutTrustedProxiesTheHeaderIsIgnoredEntirely() {
        ClientIpResolver none = new ClientIpResolver(List.of());
        assertThat(none.resolve("203.0.113.9", "1.2.3.4")).isEqualTo("203.0.113.9");
        assertThat(none.resolve("10.0.0.1", "1.2.3.4")).as("mesmo vindo de um IP privado").isEqualTo("10.0.0.1");
        assertThat(new ClientIpResolver(null).resolve("203.0.113.9", "1.2.3.4")).isEqualTo("203.0.113.9");
    }

    @Test void aSpoofedHeaderFromAnUntrustedPeerIsIgnored() {
        assertThat(behindProxy.resolve("203.0.113.9", "198.51.100.1")).isEqualTo("203.0.113.9");
    }

    @Test void aTrustedProxyReportsTheRealClient() {
        assertThat(behindProxy.resolve("10.1.2.3", "198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(behindProxy.resolve("172.18.0.5", "198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(behindProxy.resolve("0:0:0:0:0:0:0:1", "2001:db8::7")).isEqualTo("2001:db8::7");
    }

    @Test void theChainIsReadFromTheRightSkippingTrustedProxies() {
        // o cliente tentou forjar 9.9.9.9; o proxy confiável 10.0.0.2 acrescentou o endereço que realmente viu (198.51.100.7)
        assertThat(behindProxy.resolve("10.0.0.1", "9.9.9.9, 198.51.100.7, 10.0.0.2")).isEqualTo("198.51.100.7");
        assertThat(behindProxy.resolve("10.0.0.1", "198.51.100.7, 10.0.0.2, 10.0.0.3")).isEqualTo("198.51.100.7");
    }

    @Test void aChainMadeOnlyOfTrustedProxiesFallsBackToThePeer() {
        assertThat(behindProxy.resolve("10.0.0.1", "10.0.0.2, 10.0.0.3")).isEqualTo("10.0.0.1");
    }

    @Test void aMalformedChainIsNotTrustedAtAll() {
        // lixo à esquerda do que o proxy confiável acrescentou é ignorado: o cliente real já foi achado antes de chegar nele
        assertThat(behindProxy.resolve("10.0.0.1", "evil.example, 198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(behindProxy.resolve("10.0.0.1", "198.51.100.7, evil.example")).isEqualTo("10.0.0.1");
        assertThat(behindProxy.resolve("10.0.0.1", "198.51.100.7, 999.1.1.1")).isEqualTo("10.0.0.1");
        assertThat(behindProxy.resolve("10.0.0.1", "198.51.100.7, <script>")).isEqualTo("10.0.0.1");
        assertThat(behindProxy.resolve("10.0.0.1", "")).isEqualTo("10.0.0.1");
        assertThat(behindProxy.resolve("10.0.0.1", null)).isEqualTo("10.0.0.1");
    }

    @Test void cidrBoundariesAreRespectedForV4AndV6() {
        ClientIpResolver r = new ClientIpResolver(List.of("192.168.4.0/22", "2001:db8::/32"));
        assertThat(r.isTrusted("192.168.4.1")).isTrue();
        assertThat(r.isTrusted("192.168.7.255")).isTrue();
        assertThat(r.isTrusted("192.168.8.0")).isFalse();
        assertThat(r.isTrusted("192.168.3.255")).isFalse();
        assertThat(r.isTrusted("2001:db8:ffff::1")).isTrue();
        assertThat(r.isTrusted("2001:db9::1")).isFalse();
        assertThat(r.isTrusted("::ffff:192.168.5.5")).as("v4 mapeado em v6").isTrue();
        assertThat(r.isTrusted("not-an-ip")).isFalse();
        assertThat(r.isTrusted(null)).isFalse();
    }

    @Test void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> new ClientIpResolver(List.of("meu-proxy.example"))).hasMessageContaining("inválido");
        assertThatThrownBy(() -> new ClientIpResolver(List.of("10.0.0.0/33"))).hasMessageContaining("CIDR");
        assertThatThrownBy(() -> new ClientIpResolver(List.of("10.0.0.0/x"))).hasMessageContaining("CIDR");
        assertThatThrownBy(() -> new ClientIpResolver(List.of("300.1.1.1"))).hasMessageContaining("inválido");
        assertThat(new ClientIpResolver(List.of(" ", "")).isTrusted("10.0.0.1")).isFalse();
    }
}
