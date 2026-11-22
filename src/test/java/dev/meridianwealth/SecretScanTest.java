package dev.meridianwealth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Varredura de segredos no que vai para o repositório e para o navegador (código, recursos estáticos, documentação, workflows). Uma chave
 * de API, um token ou uma chave privada colados por engano quebram o {@code verify} antes de chegar a um commit. Os testes ficam de fora
 * de propósito (têm valores falsos); a biblioteca de gráficos, que é minificada, também.
 */
class SecretScanTest {
    private static final List<Pattern> SIGNATURES = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}"),
            Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}"),
            Pattern.compile("AIza[0-9A-Za-z_\\-]{35}"),
            Pattern.compile("sk-[A-Za-z0-9]{32,}"),
            Pattern.compile("(?i)(api[_-]?key|secret|token|passwd|password)\\s*[:=]\\s*['\"][A-Za-z0-9+/_\\-]{20,}['\"]"),
            Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"));

    private static boolean scannable(Path p) {
        String n = p.toString().replace('\\', '/');
        if (n.contains("/static/vendor/") || n.contains("/static/fonts/") || n.endsWith(".woff2") || n.endsWith(".png") || n.endsWith(".jpg")) return false;
        return n.endsWith(".java") || n.endsWith(".js") || n.endsWith(".html") || n.endsWith(".css") || n.endsWith(".yml") || n.endsWith(".yaml") || n.endsWith(".md")
                || n.endsWith(".sql") || n.endsWith(".xml") || n.endsWith(".properties") || n.endsWith(".example") || n.endsWith(".json");
    }

    private static List<String> scan(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        if (!Files.exists(root)) return hits;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(Files::isRegularFile).filter(SecretScanTest::scannable).toList()) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                for (Pattern s : SIGNATURES) {
                    if (s.matcher(text).find()) hits.add(p + " casa com " + s.pattern());
                }
            }
        }
        return hits;
    }

    @Test void noSecretIsCommittedInCodeResourcesDocsOrWorkflows() throws IOException {
        List<String> hits = new ArrayList<>();
        for (String dir : List.of("src/main", "docs", ".github", "scripts", "data")) hits.addAll(scan(Path.of(dir)));
        for (String file : List.of("README.md", "SECURITY.md", "CHANGELOG.md", ".env.example", "docker-compose.yml", "Dockerfile", "pom.xml")) {
            Path p = Path.of(file);
            if (Files.exists(p)) hits.addAll(scan(p));
        }
        assertThat(hits).as("possível segredo versionado").isEmpty();
    }

    @Test void secretFilesAreIgnoredAndTheExampleEnvHasNoValues() throws IOException {
        String ignore = Files.readString(Path.of(".gitignore"));
        assertThat(ignore).contains(".env").contains("!.env.example").contains("*.pem").contains("*.key");
        for (String line : Files.readAllLines(Path.of(".env.example"))) {
            if (!line.matches("^MW_[A-Z_]*(SECRET|TOKEN|KEY|PASSWORD)[A-Z_]*=.*$") && !line.matches("^[A-Z_]*(API_KEY|SECRET|TOKEN)[A-Z_]*=.*$")) continue;
            assertThat(line.substring(line.indexOf('=') + 1).strip()).as("valor em " + line.substring(0, line.indexOf('='))).isEmpty();
        }
    }

    @Test void theScannerActuallyCatchesWhatItShould() {
        assertThat(SIGNATURES.stream().anyMatch(p -> p.matcher("aws=AKIAABCDEFGHIJKLMNOP").find())).isTrue();
        assertThat(SIGNATURES.stream().anyMatch(p -> p.matcher("password = \"abcdefghijklmnopqrstuvwxyz0123\"").find())).isTrue();
        assertThat(SIGNATURES.stream().anyMatch(p -> p.matcher("-----BEGIN RSA PRIVATE KEY-----").find())).isTrue();
        assertThat(SIGNATURES.stream().noneMatch(p -> p.matcher("MW_JWT_SECRET=").find())).isTrue();
    }
}
