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

    private static final Pattern BRAPI = Pattern.compile("brapi\\.dev/api/quote/([A-Z0-9]+)\\?range=2y");
    private static final Pattern FRANKFURTER =
            Pattern.compile("api\\.frankfurter\\.dev/v1/(latest|\\d{4}-\\d{2}-\\d{2}\\.\\.[0-9-]+)\\?from=([A-Z]{3})&to=([A-Z]{3})");

    private static final Pattern BCB_SGS = Pattern.compile("api\\.bcb\\.gov\\.br/dados/serie/bcdata\\.sgs\\.(\\d+)/dados");

    private final List<String> requested = new CopyOnWriteArrayList<>();

    @Override
    public String get(String url, Map<String, String> headers) throws IOException {
        requested.add(url);
        Matcher m = YAHOO_CHART.matcher(url);
        if (m.find()) {
            String body = resource("/fixtures/yahoo/" + m.group(1) + "-" + m.group(2) + ".json");
            if (body != null) return body;
        }
        Matcher b = BRAPI.matcher(url);
        if (b.find()) {
            String body = resource("/fixtures/brapi/" + b.group(1) + "-2y.json");
            if (body != null) return body;
        }
        Matcher f = FRANKFURTER.matcher(url);
        if (f.find()) {
            String kind = "latest".equals(f.group(1)) ? "latest" : "2y";
            String body = resource("/fixtures/frankfurter/" + f.group(2) + "-" + f.group(3) + "-" + kind + ".json");
            if (body != null) return body;
        }
        Matcher sgs = BCB_SGS.matcher(url);
        if (sgs.find()) {
            String body = resource("/fixtures/bcb/sgs-" + sgs.group(1) + ".json");
            if (body != null) return body;
        }
        if (url.contains("olinda.bcb.gov.br/olinda/servico/PTAX") && url.contains("CotacaoDolarPeriodo")) {
            return resource("/fixtures/bcb/ptax-periodo.json");
        }
        if (url.startsWith("https://api.exchange.coinbase.com/products/BTC-USD/candles")) return resource("/fixtures/crypto/coinbase-candles-1h.json");
        if (url.startsWith("https://api.alternative.me/fng/")) return resource("/fixtures/crypto/fear-greed.json");
        if (url.contains("stooq.com/q/d/l/")) return resource("/fixtures/stooq/spy.us-bloqueio.html");
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
