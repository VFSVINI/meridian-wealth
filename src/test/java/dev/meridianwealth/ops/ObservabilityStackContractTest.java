package dev.meridianwealth.ops;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Item #61: a pilha local de observabilidade (Prometheus, Grafana) e os logs em JSON, conferidos sem subir contêiner nenhum. */
class ObservabilityStackContractTest {
    private static final Path OPS = Path.of("ops", "observability");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String read(String rel) throws IOException { return Files.readString(OPS.resolve(rel), StandardCharsets.UTF_8); }

    @Test
    void jsonLogsCarryTheCorrelationIdAndTheServiceNameOnOneLine() throws IOException {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogstashEncoder enc = new LogstashEncoder();
        enc.setContext(ctx);
        enc.setCustomFields("{\"service\":\"meridianwealth\"}");
        enc.start();
        LoggingEvent ev = new LoggingEvent("x", ctx.getLogger("meridianwealth.http"), Level.INFO, "http GET /api/x -> 200", null, null);
        ev.setMDCPropertyMap(Map.of("correlationId", "abc12345"));
        String line = new String(enc.encode(ev), StandardCharsets.UTF_8);
        assertThat(line.strip()).as("uma linha por evento").doesNotContain("\n");
        JsonNode n = JSON.readTree(line);
        assertThat(n.get("service").asText()).isEqualTo("meridianwealth");
        assertThat(n.get("correlationId").asText()).isEqualTo("abc12345");
        assertThat(n.get("level").asText()).isEqualTo("INFO");
        assertThat(n.get("logger_name").asText()).isEqualTo("meridianwealth.http");
        assertThat(n.get("message").asText()).isEqualTo("http GET /api/x -> 200");
        assertThat(n.has("@timestamp")).isTrue();
    }

    @Test
    void theLogbackConfigurationKeepsTheTextFormatByDefaultAndSwitchesToJsonByProfile() throws IOException {
        String xml = Files.readString(Path.of("src", "main", "resources", "logback-spring.xml"), StandardCharsets.UTF_8);
        assertThat(xml).contains("<springProfile name=\"json-logs\">").contains("LogstashEncoder").contains("<springProfile name=\"!json-logs\">").contains("console-appender.xml");
    }

    @Test
    void thePrometheusConfigScrapesTheProtectedEndpointWithTheTokenFromAFileNeverInline() throws IOException {
        String prom = read("prometheus.yml");
        assertThat(prom).contains("metrics_path: /actuator/prometheus").contains("credentials_file: /etc/prometheus/metrics-token").contains("type: Bearer").contains("/etc/prometheus/alerts.yml");
        assertThat(prom).as("nenhum token escrito no arquivo").doesNotContainPattern("(?i)credentials:\\s*\\S");
        assertThat(Files.readString(Path.of(".gitignore"))).contains("ops/observability/secrets/");
        assertThat(read("docker-compose.yml")).contains("127.0.0.1:9090:9090").contains("127.0.0.1:3000:3000").contains("./secrets/metrics-token:/etc/prometheus/metrics-token:ro").contains("prom/prometheus:v")
                .contains("grafana/grafana:");
    }

    private static final Pattern METRIC = Pattern.compile("\\b(meridianwealth_[a-z_]+|http_server_requests_seconds_(?:count|bucket|sum)|jvm_memory_(?:used|max)_bytes|process_cpu_usage|hikaricp_connections_[a-z]+|up)\\b");
    private static final Set<String> KNOWN = Set.of("meridianwealth_provider_calls_total", "meridianwealth_provider_circuit", "meridianwealth_provider_enabled", "meridianwealth_provider_latency_seconds_sum",
            "meridianwealth_provider_latency_seconds_count", "meridianwealth_cache_requests_total", "meridianwealth_cache_puts_total", "meridianwealth_portfolio_events_total",
            "http_server_requests_seconds_count", "http_server_requests_seconds_bucket", "http_server_requests_seconds_sum", "jvm_memory_used_bytes", "jvm_memory_max_bytes", "process_cpu_usage",
            "hikaricp_connections_active", "hikaricp_connections_pending", "hikaricp_connections_max", "up");

    @Test
    void theDashboardIsValidJsonAndEveryMetricItQueriesIsOneTheApplicationExposes() throws IOException {
        JsonNode dash = JSON.readTree(read("grafana/dashboards/meridianwealth.json"));
        assertThat(dash.get("uid").asText()).isEqualTo("meridianwealth-visao-geral");
        assertThat(dash.get("panels").size()).isGreaterThanOrEqualTo(10);
        Set<String> seen = new HashSet<>();
        for (JsonNode p : dash.get("panels")) {
            assertThat(p.get("title").asText()).isNotBlank();
            assertThat(p.get("datasource").get("uid").asText()).isEqualTo("prometheus");
            for (JsonNode t : p.get("targets")) {
                Matcher m = METRIC.matcher(t.get("expr").asText());
                boolean any = false;
                while (m.find()) {
                    seen.add(m.group(1));
                    any = true;
                }
                assertThat(any).as("cada painel consulta alguma métrica conhecida: " + p.get("title").asText()).isTrue();
            }
        }
        assertThat(seen).isSubsetOf(KNOWN);
        assertThat(seen).contains("meridianwealth_provider_circuit", "http_server_requests_seconds_bucket", "meridianwealth_cache_requests_total");
        assertThat(read("grafana/provisioning/datasources/prometheus.yml")).contains("uid: prometheus").contains("http://prometheus:9090");
        assertThat(read("grafana/provisioning/dashboards/dashboards.yml")).contains("/var/lib/grafana/dashboards");
    }

    @Test
    void everyAlertQueriesKnownMetricsAndHasASeverityAndAnExplanation() throws IOException {
        String alerts = read("alerts.yml");
        Set<String> seen = new HashSet<>();
        Matcher m = METRIC.matcher(alerts);
        while (m.find()) seen.add(m.group(1));
        assertThat(seen).isSubsetOf(KNOWN);
        assertThat(alerts).contains("MeridianWealthForaDoAr").contains("ProvedorComCircuitoAberto").contains("ProvedorFalhandoMuito").contains("ErrosDeServidorNaApi").contains("HeapQuaseCheio");
        long rules = alerts.lines().filter(l -> l.contains("- alert:")).count();
        assertThat(alerts.lines().filter(l -> l.contains("severity:")).count()).isEqualTo(rules);
        assertThat(alerts.lines().filter(l -> l.contains("summary:")).count()).isEqualTo(rules);
    }

    @Test
    void theApplicationEnablesTheLatencyHistogramTheDashboardNeeds() throws IOException {
        assertThat(Files.readString(Path.of("src", "main", "resources", "application.yml"))).contains("percentiles-histogram:").contains("http.server.requests: true");
    }
}
