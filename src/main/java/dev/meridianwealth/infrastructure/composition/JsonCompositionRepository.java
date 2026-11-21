package dev.meridianwealth.infrastructure.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import dev.meridianwealth.application.port.CompositionPort;
import dev.meridianwealth.domain.composition.Allocation;
import dev.meridianwealth.domain.composition.Coordinates;
import dev.meridianwealth.domain.composition.FundComposition;
import dev.meridianwealth.domain.composition.FundMetrics;
import dev.meridianwealth.domain.composition.GeoPrecision;
import dev.meridianwealth.domain.composition.PropertyKind;
import dev.meridianwealth.domain.composition.RealAsset;
import dev.meridianwealth.domain.composition.SourceKind;
import dev.meridianwealth.domain.composition.SourceRef;
import dev.meridianwealth.domain.composition.TenantSector;
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
 * Carrega a composição curada de {@code data/composicao/*.json} (gerada por {@code scripts/curadoria}). Falha na
 * partida se algum arquivo violar uma invariante do domínio (coordenada fora do Brasil, área não positiva, imóvel
 * sem coordenada e sem motivo, fonte sem https...), em vez de servir um dado quebrado.
 */
@Component
public class JsonCompositionRepository implements CompositionPort {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);

    private final Map<String, FundComposition> byFund;

    public JsonCompositionRepository() {
        this(loadFromClasspath());
    }

    JsonCompositionRepository(List<FundComposition> funds) {
        Map<String, FundComposition> m = new LinkedHashMap<>();
        funds.stream().sorted(Comparator.comparing(FundComposition::fundSymbol)).forEach(f -> {
            if (m.put(f.fundSymbol(), f) != null) throw new IllegalStateException("Fundo repetido: " + f.fundSymbol());
        });
        this.byFund = Map.copyOf(m);
    }

    @Override
    public List<FundComposition> all() { return byFund.values().stream().sorted(Comparator.comparing(FundComposition::fundSymbol)).toList(); }

    @Override
    public Optional<FundComposition> find(String fundSymbol) { return Optional.ofNullable(byFund.get(fundSymbol)); }

    private static List<FundComposition> loadFromClasspath() {
        try {
            List<FundComposition> out = new ArrayList<>();
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:data/composicao/*.json")) {
                try (InputStream in = r.getInputStream()) {
                    out.add(parse(JSON.readTree(in), String.valueOf(r.getFilename())));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler os dados de composição", e);
        }
    }

    static FundComposition parse(JsonNode n, String origin) {
        try {
            if (n.path("versaoDoEsquema").asInt() != 1) throw new IllegalStateException("Esquema não suportado");
            String symbol = text(n, "fundo");
            JsonNode f = n.path("fonte");
            SourceRef source = new SourceRef(text(f, "nome"), text(f, "url"), sourceKind(text(f, "tipo")),
                    optText(f, "referenciaDosDados"), LocalDate.parse(text(f, "consultadoEm")), optText(f, "sha256DoTextoExtraido"));
            List<RealAsset> assets = new ArrayList<>();
            for (JsonNode a : n.path("ativos")) assets.add(asset(symbol, a));
            List<Allocation> allocations = new ArrayList<>();
            for (JsonNode a : n.path("alocacoes")) {
                allocations.add(new Allocation(text(a, "codigo"), text(a, "rotulo"), dec(a, "valorReais"),
                        dec(a, "percentualDoAtivoCalculado")));
            }
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            n.path("totais").fields().forEachRemaining(e -> totals.put(e.getKey(), new BigDecimal(e.getValue().asText())));
            List<String> notices = new ArrayList<>();
            n.path("avisos").forEach(x -> notices.add(x.asText()));
            return new FundComposition(symbol, text(n, "nomeDoFundo"), optText(n, "cnpj"), source, optText(n, "rotuloDaArea"),
                    n.hasNonNull("quantidadeDeAtivosDeclarada") ? n.get("quantidadeDeAtivosDeclarada").asInt() : null,
                    metrics(n.path("metricasDoFundo")), optText(n, "classificacaoNoInforme"), assets, allocations, totals, notices);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Composição inválida em " + origin + ": " + e.getMessage(), e);
        }
    }

    private static RealAsset asset(String fund, JsonNode a) {
        List<TenantSector> sectors = new ArrayList<>();
        a.path("setoresDosInquilinos").forEach(s -> sectors.add(new TenantSector(text(s, "setor"), dec(s, "percentualDaReceitaDoImovel"))));
        String kind = optText(a, "tipo");
        return new RealAsset(text(a, "id"), fund, text(a, "nome"), optText(a, "enderecoPublicado"), optText(a, "cidade"),
                optText(a, "uf"), kind == null ? null : PropertyKind.valueOf(kind), dec(a, "areaM2"), text(a, "areaPublicada"),
                optDec(a, "vacanciaPercentual"), optDec(a, "percentualDaReceitaDoFundo"), sectors,
                a.hasNonNull("coordenadas") ? coordinates(a.get("coordenadas")) : null, optText(a, "coordenadasMotivoAusencia"));
    }

    private static SourceKind sourceKind(String value) {
        return switch (value) {
            case "OFICIAL" -> SourceKind.OFFICIAL;
            case "TERCEIRO" -> SourceKind.THIRD_PARTY;
            default -> throw new IllegalStateException("Tipo de fonte desconhecido: " + value);
        };
    }

    private static Coordinates coordinates(JsonNode c) {
        GeoPrecision precision = switch (text(c, "precisao")) {
            case "ENDERECO" -> GeoPrecision.ADDRESS;
            case "VIA" -> GeoPrecision.STREET;
            default -> throw new IllegalStateException("Precisão desconhecida: " + text(c, "precisao"));
        };
        return new Coordinates(dec(c, "lat"), dec(c, "lng"), precision, text(c, "enderecoEncontrado"), text(c, "metodo"),
                optText(c, "osm"), LocalDate.parse(text(c, "geocodificadoEm")));
    }

    private static FundMetrics metrics(JsonNode m) {
        if (m.isMissingNode() || m.isNull()) return null;
        return new FundMetrics(optDec(m, "ablTotalM2"), optDec(m, "vacanciaFisicaPercentual"),
                optDec(m, "vacanciaFinanceiraPercentual"), optText(m, "referenciaDasMetricas"));
    }

    private static String text(JsonNode n, String field) {
        if (!n.hasNonNull(field) || n.get(field).asText().isBlank()) throw new IllegalStateException("Campo obrigatório ausente: " + field);
        return n.get(field).asText();
    }

    private static String optText(JsonNode n, String field) { return n.hasNonNull(field) ? n.get(field).asText() : null; }

    private static BigDecimal dec(JsonNode n, String field) { return new BigDecimal(text(n, field)); }

    private static BigDecimal optDec(JsonNode n, String field) { return n.hasNonNull(field) ? new BigDecimal(n.get(field).asText()) : null; }
}
