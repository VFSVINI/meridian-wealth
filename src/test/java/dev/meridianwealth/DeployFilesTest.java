package dev.meridianwealth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Trava a coerência dos arquivos de deploy: blueprint do Render, memória da JVM, segredos fora do arquivo e runbook com as variáveis. */
class DeployFilesTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    @Test
    void blueprintDeclaresADockerWebServiceOnTheFreePlanWithALivenessCheck() throws IOException {
        String yaml = read("render.yaml");
        assertThat(yaml).contains("type: web").contains("runtime: docker").contains("plan: free")
                .contains("healthCheckPath: /actuator/health/liveness").contains("autoDeploy: false");
    }

    @Test
    void blueprintNeverWritesTheDatabaseCredentialsOnlyAsksForThem() throws IOException {
        String yaml = read("render.yaml");
        for (String key : new String[] {"DB_URL", "DB_USER", "DB_PASSWORD"}) {
            assertThat(yaml).containsPattern("key: " + key + "\\R\\s+sync: false");
        }
        assertThat(yaml).containsPattern("key: MW_JWT_SECRET\\R\\s+generateValue: true");
        assertThat(Pattern.compile("jdbc:postgresql://|postgres(ql)?://\\w+:|npg_[A-Za-z0-9]{8,}").matcher(yaml).find()).isFalse();
    }

    @Test
    void theJvmHeapPlusMetaspaceFitsTheFree512MbWithRoomForTheRest() throws IOException {
        Matcher opts = Pattern.compile("key: JAVA_TOOL_OPTIONS\\R\\s+value: (.+)").matcher(read("render.yaml"));
        assertThat(opts.find()).as("JAVA_TOOL_OPTIONS definido").isTrue();
        String yaml = opts.group(1);
        Matcher heap = Pattern.compile("-Xmx(\\d+)m").matcher(yaml);
        Matcher meta = Pattern.compile("-XX:MaxMetaspaceSize=(\\d+)m").matcher(yaml);
        assertThat(heap.find()).as("-Xmx definido").isTrue();
        assertThat(meta.find()).as("metaspace limitado").isTrue();
        int total = Integer.parseInt(heap.group(1)) + Integer.parseInt(meta.group(1));
        assertThat(total).as("heap + metaspace, deixando mais de 100 MB para pilhas, código e o sistema").isLessThanOrEqualTo(512 - 100);
    }

    @Test
    void theImageHealthcheckFollowsThePortTheHostInjects() throws IOException {
        String dockerfile = read("Dockerfile");
        assertThat(dockerfile).contains("${PORT:-8080}/actuator/health/liveness");
        assertThat(read("src/main/resources/application.yml")).contains("port: ${PORT:8080}").contains("maximum-pool-size: ${DB_POOL_SIZE:10}");
    }

    @Test
    void thereIsNoLeftoverOfTheAbandonedHostingProvider() throws IOException {
        assertThat(Files.exists(Path.of("deploy/hf"))).isFalse();
        assertThat(Files.exists(Path.of(".github/workflows/deploy-hf.yml"))).isFalse();
        assertThat(read(".github/workflows/ci.yml")).doesNotContain("deploy/hf");
    }

    @Test
    void runbookDocumentsEveryVariableTheServiceNeedsAndTheUnverifiedPoints() throws IOException {
        String runbook = read("docs/deploy.md");
        for (String v : new String[] {"DB_URL", "DB_USER", "DB_PASSWORD", "MW_JWT_SECRET", "MW_TRUSTED_PROXIES", "render.yaml", "sslmode=require",
                "Ohio", "Hugging Face"}) {
            assertThat(runbook).as(v).contains(v);
        }
        assertThat(runbook).contains("Pontos a verificar no primeiro deploy").contains("Reverter").contains("exigem plano pago");
    }

    @Test
    void theBlueprintRedisExclusionNamesRealAutoConfigurationClasses() throws Exception {
        String yaml = read("render.yaml");
        for (String cls : new String[] {"org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"}) {
            assertThat(yaml).contains(cls);
            assertThat(Class.forName(cls)).isNotNull();
        }
    }
}
