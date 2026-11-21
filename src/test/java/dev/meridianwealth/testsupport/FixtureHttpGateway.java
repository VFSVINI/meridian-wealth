package dev.meridianwealth.testsupport;

import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.infrastructure.resilience.ClientErrorException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gateway HTTP de teste que responde com respostas reais gravadas dos provedores
 * ({@code src/test/resources/fixtures}). Qualquer URL sem fixture recebe HTTP 404, o que exercita
 * o caminho honesto de "sem dado em tempo real".
 */
public class FixtureHttpGateway implements HttpGateway {
    private static final Pattern YAHOO_CHART =
            Pattern.compile("query1\\.finance\\.yahoo\\.com/v8/finance/chart/([^?]+)\\?range=([0-9a-z]+)&");

    private final List<String> requested = new CopyOnWriteArrayList<>();

    @Override
    public String get(String url, Map<String, String> headers) throws IOException {
        requested.add(url);
        Matcher m = YAHOO_CHART.matcher(url);
        if (m.find()) {
            String body = resource("/fixtures/yahoo/" + m.group(1) + "-" + m.group(2) + ".json");
            if (body != null) return body;
        }
        throw new ClientErrorException("HTTP 404 (sem fixture para " + url + ")");
    }

    public List<String> requested() { return List.copyOf(requested); }

    public static String resource(String path) {
        try (InputStream in = FixtureHttpGateway.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
