package dev.meridianwealth.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Item #62: o fluxo de deploy só roda sob comando, só publica com a CI verde, não carrega segredo no texto e confere o commit no ar. */
class DeployWorkflowContractTest {
    private static String read(String first, String... more) throws IOException { return Files.readString(Path.of(first, more), StandardCharsets.UTF_8).replace("\r\n", "\n"); }

    @Test
    void itRunsOnlyWhenTheOwnerAsksOrAReleaseIsPublishedNeverOnPush() throws IOException {
        String wf = read(".github", "workflows", "deploy.yml");
        assertThat(wf).contains("workflow_dispatch:").contains("types: [published]");
        assertThat(wf).as("nada de deploy a cada push: o Render segue com autoDeploy desligado").doesNotContain("\n  push:").doesNotContain("pull_request");
        assertThat(read("render.yaml")).contains("autoDeploy: false");
        assertThat(wf).contains("group: deploy").contains("cancel-in-progress: false");
    }

    @Test
    void itPublishesOnlyAfterTheCiPassedOnThatCommitWithTheLeastPermissions() throws IOException {
        String wf = read(".github", "workflows", "deploy.yml");
        assertThat(wf).contains("Build, testes, cobertura e estilo").contains("\"$result\" != \"success\"").contains("exit 1");
        assertThat(wf.indexOf("Confirma que a CI passou")).as("a conferência vem antes do build e do push").isLessThan(wf.indexOf("docker/build-push-action"));
        assertThat(wf).contains("permissions:\n  contents: read\n").contains("packages: write").contains("checks: read");
        assertThat(wf).as("o publish de pacote só existe no job que publica").containsOnlyOnce("packages: write");
        assertThat(read(".github", "workflows", "ci.yml")).as("o nome da verificação usada como portão existe na CI").contains("name: Build, testes, cobertura e estilo");
    }

    @Test
    void secretsComeFromTheSecretStoreAreMaskedAndNothingSensitiveIsWrittenInTheFile() throws IOException {
        String wf = read(".github", "workflows", "deploy.yml");
        assertThat(wf).contains("secrets.RENDER_DEPLOY_HOOK_URL").contains("vars.RENDER_SERVICE_URL").contains("::add-mask::$HOOK").contains("secrets.GITHUB_TOKEN");
        assertThat(wf).doesNotContainPattern("api\\.render\\.com/deploy/srv-[A-Za-z0-9]+").doesNotContainPattern("key=[A-Za-z0-9_-]{8,}");
        assertThat(wf).as("o job de deploy usa o ambiente production (aprovação opcional)").contains("environment: production");
        assertThat(wf).as("sem configuração o fluxo para com uma mensagem clara").contains("Faltam o segredo RENDER_DEPLOY_HOOK_URL");
    }

    @Test
    void itWaitsForTheServiceToAnswerWithTheExactCommitBeforeCallingItDone() throws IOException {
        String wf = read(".github", "workflows", "deploy.yml");
        assertThat(wf).contains("/actuator/info").contains("grep -q \"$SHA\"").contains("/actuator/health/liveness").contains("seq 1 60");
        assertThat(wf).contains("GIT_COMMIT=${{ github.sha }}").contains("org.opencontainers.image.revision");
        String yml = read("src", "main", "resources", "application.yml");
        assertThat(yml).contains("commit: ${RENDER_GIT_COMMIT:${GIT_COMMIT:desconhecido}}").contains("env:\n      enabled: true");
        assertThat(read("Dockerfile")).contains("ARG GIT_COMMIT=desconhecido").contains("ENV GIT_COMMIT=$GIT_COMMIT");
    }

    @Test
    void everyActionIsPinnedToAMajorVersionAndTheCiLintsTheWorkflows() throws IOException {
        String wf = read(".github", "workflows", "deploy.yml");
        wf.lines().filter(l -> l.trim().startsWith("- uses:")).forEach(l -> assertThat(l).as(l).containsPattern("@v\\d+"));
        assertThat(read(".github", "workflows", "ci.yml")).contains("rhysd/actionlint:1.7.4");
    }
}
