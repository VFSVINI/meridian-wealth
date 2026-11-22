package dev.meridianwealth.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A interface é servida em arquivos separados (index.html, app.css, app.js, theme.js) por causa da CSP, que proíbe script e estilo
 * embutidos. Os testes de contrato leem a página como um todo, então este auxiliar junta os quatro.
 */
public final class StaticPage {
    private StaticPage() {}

    public static String file(String name) throws IOException {
        try (InputStream in = StaticPage.class.getResourceAsStream("/static/" + name)) {
            if (in == null) throw new IOException("Recurso estático ausente: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** index.html seguido do CSS e dos scripts, como se fossem um só documento. */
    public static String whole() throws IOException {
        return file("index.html") + "\n" + file("app.css") + "\n" + file("theme.js") + "\n" + file("app.js") + "\n" + file("auth.js") + "\n" + file("carteira.js") + "\n" + file("mapa.js");
    }
}
