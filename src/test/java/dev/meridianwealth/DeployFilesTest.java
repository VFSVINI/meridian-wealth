package dev.meridianwealth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Trava a coerência dos arquivos de deploy: porta do Space, SDK, workflow manual sem segredo escrito e runbook com as variáveis. */
class DeployFilesTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    @Test
    void spaceReadmeDeclaresDockerSdkAndPort7860() throws IOException {
        String readme = read("deploy/hf/README.md");
        assertThat(readme).startsWith("---\n");
        assertThat(readme).contains("sdk: docker").contains("app_port: 7860");
    }

    @Test
    void spaceImageListensOnThePortTheReadmeDeclaresAsUid1000() throws IOException {
        String dockerfile = read("deploy/hf/Dockerfile");
        assertThat(dockerfile).contains("ENV PORT=7860").contains("EXPOSE 7860").contains("localhost:7860").contains("-u 1000");
        assertThat(dockerfile).doesNotContain("USER root");
    }

    @Test
    void theServerPortAndPoolComeFromTheEnvironment() throws IOException {
        String yml = read("src/main/resources/application.yml");
        assertThat(yml).contains("port: ${PORT:8080}").contains("maximum-pool-size: ${DB_POOL_SIZE:10}");
    }

    @Test
    void deployWorkflowIsManualAndReadsCredentialsOnlyFromSecrets() throws IOException {
        String wf = read(".github/workflows/deploy-hf.yml");
        assertThat(wf).contains("workflow_dispatch").doesNotContain("push:").doesNotContain("pull_request");
        assertThat(wf).contains("secrets.HF_TOKEN").contains("secrets.HF_SPACE");
        assertThat(Pattern.compile("hf_[A-Za-z0-9]{20,}").matcher(wf).find()).isFalse();
    }

    @Test
    void runbookDocumentsEveryVariableTheSpaceNeedsAndTheUnverifiedPoints() throws IOException {
        String runbook = read("docs/deploy.md");
        for (String v : new String[] {"MW_PERSISTENCE", "MW_EXCLUDE_AUTOCONFIG", "DB_URL", "DB_USER", "DB_PASSWORD", "DB_POOL_SIZE",
                "MW_JWT_SECRET", "MW_TRUSTED_PROXIES", "HF_TOKEN", "HF_SPACE", "7860", "sslmode=require"}) {
            assertThat(runbook).as(v).contains(v);
        }
        assertThat(runbook).contains("Pontos a verificar no primeiro deploy").contains("Reverter");
    }

    @Test
    void theRunbookRedisExclusionNamesRealAutoConfigurationClasses() throws Exception {
        String runbook = read("docs/deploy.md");
        for (String cls : new String[] {"org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"}) {
            assertThat(runbook).contains(cls);
            assertThat(Class.forName(cls)).isNotNull();
        }
    }
}
