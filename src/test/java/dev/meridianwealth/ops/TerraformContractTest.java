package dev.meridianwealth.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Item #63: o Terraform descreve o mesmo ambiente do render.yaml, não carrega segredo e nunca é aplicado pela CI. */
class TerraformContractTest {
    private static final Path TF = Path.of("infra", "terraform");

    private static String read(Path p) throws IOException { return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"); }

    @Test
    void theTerraformEnvironmentMirrorsTheRenderBlueprintEnvVarsRegionPlanAndNoAutoDeploy() throws IOException {
        String main = read(TF.resolve("main.tf")), render = read(Path.of("render.yaml"));
        Set<String> yamlKeys = new LinkedHashSet<>();
        Matcher m = Pattern.compile("- key: ([A-Z_]+)").matcher(render);
        while (m.find()) yamlKeys.add(m.group(1));
        assertThat(yamlKeys).as("o render.yaml declara variáveis").contains("DB_URL", "DB_USER", "DB_PASSWORD", "MW_JWT_SECRET", "JAVA_TOOL_OPTIONS", "MW_PERSISTENCE");
        for (String k : yamlKeys) assertThat(main).as("variável do render.yaml ausente no Terraform: " + k).containsPattern("\\b" + k + "\\s+= \\{");
        assertThat(main).contains("region            = \"ohio\"").contains("auto_deploy = false").contains("health_check_path = \"/actuator/health/liveness\"").contains("region_id      = \"aws-us-east-2\"")
                .contains("pg_version     = 16");
        assertThat(render).contains("region: ohio").contains("autoDeploy: false").contains("healthCheckPath: /actuator/health/liveness");
        String jvm = Pattern.compile("JAVA_TOOL_OPTIONS\\s+= \\{ value = \"([^\"]+)\"").matcher(main).results().map(r -> r.group(1)).findFirst().orElseThrow();
        assertThat(render).as("as mesmas opções de JVM nos dois lugares").contains(jvm);
    }

    @Test
    void noSecretIsWrittenInTheFilesAndEverySecretVariableIsSensitiveWithoutADefault() throws IOException {
        String vars = read(TF.resolve("variables.tf")), all = vars + read(TF.resolve("main.tf")) + read(TF.resolve("outputs.tf")) + read(TF.resolve("versions.tf")) + read(TF.resolve("terraform.tfvars.example"));
        for (String secret : new String[] {"render_api_key", "neon_api_key"}) {
            String block = vars.substring(vars.indexOf("variable \"" + secret + "\""));
            block = block.substring(0, block.indexOf("\n}\n"));
            assertThat(block).as(secret).contains("sensitive   = true").doesNotContain("default");
        }
        assertThat(all).doesNotContainPattern("rnd_[A-Za-z0-9]{10,}").doesNotContainPattern("napi_[A-Za-z0-9]{10,}").doesNotContainPattern("postgres(ql)?://[^\\s\"$]+:[^\\s\"@$]+@");
        assertThat(read(TF.resolve("main.tf"))).as("senha e segredo vêm de recursos, nunca de texto fixo").contains("neon_project.db.database_password").contains("random_password.jwt_secret.result");
        String ignore = read(Path.of(".gitignore"));
        assertThat(ignore).contains("*.tfstate").contains(".terraform/").contains("terraform.tfvars");
    }

    @Test
    void theCiOnlyChecksFormatAndValidityAndNeverAppliesAnything() throws IOException {
        String ci = read(Path.of(".github", "workflows", "ci.yml"));
        assertThat(ci).contains("hashicorp/terraform:1.9.8").contains("fmt -check").contains("init -backend=false").contains("validate");
        assertThat(ci).doesNotContain("terraform apply").doesNotContain(" apply ").doesNotContain("tf apply");
        for (Path wf : Files.list(Path.of(".github", "workflows")).toList()) assertThat(read(wf)).as(wf.toString()).doesNotContain("terraform apply");
        assertThat(read(TF.resolve("README.md"))).contains("Nada aqui é aplicado").contains("terraform import").contains("contém a senha do banco");
    }

    @Test
    void theProvidersArePinnedToAMajorLineAndTheAdrComparesTheCloudsHonestly() throws IOException {
        String versions = read(TF.resolve("versions.tf"));
        assertThat(versions).contains("render-oss/render").contains("kislerdm/neon").contains("hashicorp/random").contains("version = \"~> 1.9\"").contains("version = \"~> 0.18\"");
        String adr = read(Path.of("docs", "adr", "045-infraestrutura-como-codigo-e-comparacao-de-nuvens.md"));
        assertThat(adr).contains("Cloud Run").contains("cartão").contains("não foram verificados").contains("Nada migra para o GCP sem decisão");
    }
}
