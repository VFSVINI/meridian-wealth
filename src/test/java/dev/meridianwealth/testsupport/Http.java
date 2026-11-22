package dev.meridianwealth.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de teste sobre o {@code java.net.http}. O {@code TestRestTemplate} usa {@code HttpURLConnection}, que lança exceção
 * em respostas 401 com {@code WWW-Authenticate} a um POST, justamente o caso que os testes de segurança precisam exercitar.
 */
public final class Http {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String base;

    public Http(int port) { this.base = "http://localhost:" + port; }

    public record Reply(int status, Map<String, List<String>> headers, String body) {
        @SuppressWarnings("unchecked")
        public Map<String, Object> json() {
            try {
                return body == null || body.isBlank() ? Map.of() : JSON.readValue(body, Map.class);
            } catch (IOException e) {
                throw new IllegalStateException("Resposta não é JSON: " + body, e);
            }
        }

        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(e -> String.join(", ", e.getValue())).findFirst().orElse(null);
        }

        public Object get(String key) { return json().get(key); }

        @SuppressWarnings("unchecked")
        public Map<String, Object> obj(String key) { return (Map<String, Object>) json().get(key); }
    }

    public Reply send(String method, String path, Map<String, String> headers, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
            Map<String, String> h = new LinkedHashMap<>(headers == null ? Map.of() : headers);
            String payload = body == null ? null : body instanceof String s ? s : JSON.writeValueAsString(body);
            if (payload != null) h.putIfAbsent("Content-Type", "application/json");
            h.forEach(b::header);
            b.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.headers().map(), r.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** POST com corpo em fluxo, o que faz o cliente enviar {@code Transfer-Encoding: chunked} (sem Content-Length). */
    public Reply postChunked(String path, String body) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).build();
            HttpResponse<String> resp = client.send(r, HttpResponse.BodyHandlers.ofString());
            return new Reply(resp.statusCode(), resp.headers().map(), resp.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public Reply get(String path) { return send("GET", path, null, null); }

    public Reply get(String path, Map<String, String> headers) { return send("GET", path, headers, null); }

    public Reply post(String path, Object body) { return send("POST", path, null, body); }

    public Reply post(String path, Map<String, String> headers, Object body) { return send("POST", path, headers, body); }

    public Reply put(String path, Map<String, String> headers, Object body) { return send("PUT", path, headers, body); }

    public static Map<String, String> bearer(String token) { return Map.of("Authorization", "Bearer " + token); }
}
