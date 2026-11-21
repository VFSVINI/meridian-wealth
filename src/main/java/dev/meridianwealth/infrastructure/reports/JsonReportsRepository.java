package dev.meridianwealth.infrastructure.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.port.ReportsPort;
import dev.meridianwealth.domain.composition.SourceKind;
import dev.meridianwealth.domain.reports.FiiReportFactory;
import dev.meridianwealth.domain.reports.IssuerKind;
import dev.meridianwealth.domain.reports.IssuerReports;
import dev.meridianwealth.domain.reports.Report;
import dev.meridianwealth.domain.reports.ReportMetric;
import dev.meridianwealth.domain.reports.ReportPeriodicity;
import dev.meridianwealth.domain.reports.ReportSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Carrega os relatórios curados de {@code data/relatorios/*.json} (gerados por {@code scripts/curadoria/gerar_relatorios.py}).
 * Falha na partida se um arquivo violar uma invariante, em vez de servir dado quebrado. O veredito não vem do arquivo: é
 * calculado pelo domínio.
 */
@Component
public class JsonReportsRepository implements ReportsPort {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, IssuerReports> bySymbol;

    public JsonReportsRepository() { this(loadFromClasspath()); }

    JsonReportsRepository(List<IssuerReports> issuers) {
        Map<String, IssuerReports> m = new LinkedHashMap<>();
        issuers.stream().sorted(Comparator.comparing(IssuerReports::symbol)).forEach(i -> {
            if (m.put(i.symbol(), i) != null) throw new IllegalStateException("Ativo repetido: " + i.symbol());
        });
        this.bySymbol = Map.copyOf(m);
    }

    @Override
    public List<IssuerReports> all() { return bySymbol.values().stream().sorted(Comparator.comparing(IssuerReports::symbol)).toList(); }

    @Override
    public Optional<IssuerReports> find(String symbol) { return Optional.ofNullable(bySymbol.get(symbol)); }

    private static List<IssuerReports> loadFromClasspath() {
        try {
            List<IssuerReports> out = new ArrayList<>();
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:data/relatorios/*.json")) {
                try (InputStream in = r.getInputStream()) {
                    out.add(parse(JSON.readTree(in), String.valueOf(r.getFilename())));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler os dados de relatórios", e);
        }
    }

    static IssuerReports parse(JsonNode n, String origin) {
        try {
            if (n.path("versaoDoEsquema").asInt() != 1) throw new IllegalStateException("Esquema não suportado");
            String symbol = text(n, "ativo");
            IssuerKind kind = switch (text(n, "tipo")) {
                case "EMPRESA" -> IssuerKind.COMPANY;
                case "FII" -> IssuerKind.FII;
                default -> throw new IllegalStateException("Tipo desconhecido: " + text(n, "tipo"));
            };
            List<ReportSource> sources = new ArrayList<>();
            for (JsonNode f : n.path("fontes")) sources.add(source(f));
            if (sources.isEmpty()) throw new IllegalStateException("Sem fonte");
            List<Report> reports = new ArrayList<>();
            String fiscalYearEnd = null;
            if (kind == IssuerKind.COMPANY) {
                for (JsonNode r : n.path("relatorios")) reports.add(companyReport(r));
            } else {
                List<FiiReportFactory.Month> months = new ArrayList<>();
                for (JsonNode m : n.path("meses")) {
                    months.add(new FiiReportFactory.Month(text(m, "id"), LocalDate.parse(text(m, "fimDoPeriodo")),
                            LocalDate.parse(text(m, "primeiraEntregaEm")), LocalDate.parse(text(m, "versaoEntregueEm")),
                            m.path("versao").asInt(), optDec(m, "dividendYieldPercentual"), optDec(m, "valorPatrimonialPorCota")));
                    if (fiscalYearEnd == null) fiscalYearEnd = optText(m, "encerramentoDoExercicio");
                }
                reports.addAll(FiiReportFactory.build(months));
            }
            List<String> notices = new ArrayList<>();
            n.path("avisos").forEach(x -> notices.add(x.asText()));
            return new IssuerReports(symbol, optText(n, "nome"), optText(n, "cnpj"), kind, sources, reports, fiscalYearEnd, notices);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Relatórios inválidos em " + origin + ": " + e.getMessage(), e);
        }
    }

    private static ReportSource source(JsonNode f) {
        Map<String, String> files = new LinkedHashMap<>();
        f.path("arquivos").fields().forEachRemaining(e -> files.put(e.getKey(), e.getValue().asText()));
        SourceKind kind = switch (text(f, "tipo")) {
            case "OFICIAL" -> SourceKind.OFFICIAL;
            case "TERCEIRO" -> SourceKind.THIRD_PARTY;
            default -> throw new IllegalStateException("Tipo de fonte desconhecido: " + text(f, "tipo"));
        };
        return new ReportSource(text(f, "nome"), text(f, "url"), kind, LocalDate.parse(text(f, "consultadoEm")), files);
    }

    private static Report companyReport(JsonNode r) {
        ReportPeriodicity periodicity = switch (text(r, "periodicidade")) {
            case "TRIMESTRAL" -> ReportPeriodicity.QUARTERLY;
            case "ANUAL" -> ReportPeriodicity.ANNUAL;
            default -> throw new IllegalStateException("Periodicidade desconhecida: " + text(r, "periodicidade"));
        };
        List<ReportMetric> metrics = new ArrayList<>();
        for (JsonNode m : r.path("metricas")) {
            String base = optText(m, "periodoBase");
            metrics.add(new ReportMetric(text(m, "codigo"), text(m, "rotulo"), text(m, "rotuloNaFonte"), text(m, "unidade"),
                    new BigDecimal(text(m, "atual")), text(m, "periodoAtual"), optDec(m, "base"), base,
                    periodicity == ReportPeriodicity.ANNUAL ? "exercício anterior" : "mesmo trimestre do ano anterior"));
        }
        return new Report(text(r, "id"), periodicity, text(r, "rotulo"), LocalDate.parse(text(r, "fimDoPeriodo")),
                LocalDate.parse(text(r, "primeiraEntregaEm")), LocalDate.parse(text(r, "versaoEntregueEm")), r.path("versao").asInt(),
                optText(r, "link"), metrics);
    }

    private static String text(JsonNode n, String field) {
        if (!n.hasNonNull(field) || n.get(field).asText().isBlank()) throw new IllegalStateException("Campo obrigatório ausente: " + field);
        return n.get(field).asText();
    }

    private static String optText(JsonNode n, String field) { return n.hasNonNull(field) ? n.get(field).asText() : null; }

    private static BigDecimal optDec(JsonNode n, String field) { return n.hasNonNull(field) ? new BigDecimal(n.get(field).asText()) : null; }
}
