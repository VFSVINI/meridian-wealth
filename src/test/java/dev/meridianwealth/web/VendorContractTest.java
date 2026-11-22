package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Contrato da biblioteca de gráficos servida pelo projeto (item #15): Apache ECharts 5.5.1, sem CDN (a CSP só aceita o próprio servidor),
 * com a licença Apache 2.0 e o NOTICE ao lado. O SHA-256 trava a versão: trocar o arquivo exige trocar o valor aqui, de propósito.
 */
class VendorContractTest {
    static final String ECHARTS_SHA256 = "e84270bd0cd5bdf60fefc26d00c2a391cb2e81f4d26a7a9ee16185a54773a3cf";

    private static byte[] read(String path) throws IOException {
        try (InputStream in = VendorContractTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso ausente: " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test void echartsIsServedLocallyAtThePinnedVersion() throws Exception {
        byte[] js = read("/static/vendor/echarts.min.js");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(js))).isEqualTo(ECHARTS_SHA256);
        assertThat(new String(js, 0, 400, StandardCharsets.UTF_8)).contains("Apache Software Foundation").contains("Apache License");
    }

    @Test void theApacheLicenseAndNoticeTravelWithTheLibrary() throws IOException {
        assertThat(new String(read("/static/vendor/LICENSE-echarts.txt"), StandardCharsets.UTF_8)).contains("Apache License").contains("Version 2.0");
        assertThat(new String(read("/static/vendor/NOTICE-echarts.txt"), StandardCharsets.UTF_8)).contains("Apache ECharts").contains("Apache Software Foundation");
    }

    @Test void theInterfaceNeverReachesForTheLibraryOnAnExternalHost() throws IOException {
        String all = dev.meridianwealth.testsupport.StaticPage.whole();
        assertThat(all).contains("/vendor/echarts.min.js").doesNotContain("cdn.jsdelivr").doesNotContain("echarts.apache.org");
    }
}
