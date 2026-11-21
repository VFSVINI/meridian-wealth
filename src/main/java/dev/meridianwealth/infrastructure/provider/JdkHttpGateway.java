package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.infrastructure.resilience.ClientErrorException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdkHttpGateway implements HttpGateway {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 (compatible; MeridianWealth/0.2)");
        headers.forEach(b::header);
        try {
            HttpResponse<String> r = client.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
            int s = r.statusCode();
            if (s >= 500 || s == 429) throw new IOException("HTTP " + s);
            if (s >= 400) throw new ClientErrorException("HTTP " + s);
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("requisição interrompida", e);
        }
    }
}
