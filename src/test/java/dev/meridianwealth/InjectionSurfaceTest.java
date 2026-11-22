package dev.meridianwealth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Duas famílias de ataque travadas por inspeção do código-fonte: injeção de SQL (toda consulta é parametrizada, e só constantes do próprio
 * adaptador entram no texto do SQL) e SSRF (nenhuma rota recebe uma URL, um host ou um endereço de retorno, então o servidor nunca faz
 * requisição para um destino escolhido por quem chama).
 */
class InjectionSurfaceTest {
    private static List<Path> sources(String dir, String glob) throws IOException {
        try (Stream<Path> s = Files.walk(Path.of(dir))) {
            return s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches(glob)).toList();
        }
    }

    @Test void everySqlStatementIsParameterizedAndOnlyConstantsAreConcatenated() throws IOException {
        // jdbc.query("..." + X ...): X precisa ser uma constante do adaptador (COLUMNS, ALL_LIMIT, ...) em MAIÚSCULAS, nunca uma variável
        Pattern concat = Pattern.compile("jdbc\\.(query|update|queryForObject|queryForList|execute)\\(\\s*\"[^\"]*\"\\s*\\+\\s*([A-Za-z_][A-Za-z0-9_.()]*)");
        List<String> bad = new ArrayList<>();
        int statements = 0;
        for (Path p : sources("src/main/java/dev/meridianwealth/infrastructure/persistence", "Jdbc.*\\.java")) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            Matcher m = concat.matcher(text);
            while (m.find()) {
                statements++;
                if (!m.group(2).matches("[A-Z][A-Z0-9_]*")) bad.add(p.getFileName() + ": concatena " + m.group(2));
            }
            assertThat(text).as(p.getFileName() + " monta SQL com String.format").doesNotContain("String.format(\"SELECT").doesNotContain("String.format(\"INSERT").doesNotContain("String.format(\"UPDATE")
                    .doesNotContain("String.format(\"DELETE");
        }
        assertThat(statements).as("o teste enxerga os adaptadores").isPositive();
        assertThat(bad).as("entrada concatenada em SQL").isEmpty();
    }

    @Test void noRouteReceivesAUrlHostOrCallbackSoTheServerNeverCallsADestinationTheCallerChose() throws IOException {
        Pattern risky = Pattern.compile("(@(RequestParam|PathVariable|RequestHeader)(\\([^)]*\\))?\\s+\\w+\\s+(url|uri|host|callback|redirect|webhook|endpoint|target|link)\\b)"
                + "|(record\\s+\\w*(Request|Body)\\([^)]*\\b(String|URI|URL)\\s+(url|uri|host|callback|redirectUri|webhook|endpoint|targetUrl)\\b)", Pattern.CASE_INSENSITIVE);
        List<String> bad = new ArrayList<>();
        for (Path p : sources("src/main/java/dev/meridianwealth/web", ".*\\.java")) {
            Matcher m = risky.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) bad.add(p.getFileName() + ": " + m.group());
        }
        assertThat(bad).as("rota que aceita destino de rede escolhido pelo cliente (SSRF)").isEmpty();
    }
}
