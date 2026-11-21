package dev.meridianwealth.infrastructure.bcb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.provider.HttpGateway;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Banco Central do Brasil: séries do SGS e PTAX do serviço Olinda. Contratos confirmados em
 * 2026-11-20 contra a API real (ver {@code docs/mercados.md}):
 * <ul>
 *   <li>SGS com janela de datas ({@code dataInicial} e {@code dataFinal} em dd/MM/aaaa; a consulta sem filtro
 *       é limitada e a janela máxima é de 10 anos). O modo "últimos N" traz datas futuras na série 432
 *       e não garante a ordem (a série 4389 veio decrescente), por isso não é usado.</li>
 *   <li>Os valores do SGS chegam como <b>texto</b> ({@code "13.75"}); o PTAX chega como número decimal e
 *       é lido sem passar por {@code double}. Tudo vira {@link BigDecimal} (ADR 002).</li>
 *   <li>Janela sem dados responde HTTP 404 com {@code {"erro":{...}}}, data fora do formato responde 400:
 *       em ambos os casos o cliente falha, e quem chama mantém o último valor bom.</li>
 * </ul>
 */
@Component
public class BcbClient implements ProviderStatusSource {
    /** Uma observação de série: data de referência e valor. */
    public record Point(LocalDate date, BigDecimal value) {}

    /** PTAX do dia: compra, venda e o horário da cotação (hora de Brasília). */
    public record Ptax(BigDecimal buy, BigDecimal sell, LocalDateTime time) {}

    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final DateTimeFormatter SGS_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter OLINDA_DATE = DateTimeFormatter.ofPattern("MM-dd-uuuu");
    private static final DateTimeFormatter OLINDA_TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter();

    private final HttpGateway http;
    private final ResilientExecutor executor;

    /** Balde de 8 fichas: cobre uma rodada inteira de atualização (6 consultas) sem esperar. */
    @Autowired
    public BcbClient(HttpGateway http) { this(http, ResilientExecutor.withDefaults("BCB", 8, 1.0)); }

    BcbClient(HttpGateway http, ResilientExecutor executor) {
        this.http = http;
        this.executor = executor;
    }

    static String seriesUrl(int code, LocalDate from, LocalDate to) {
        return "https://api.bcb.gov.br/dados/serie/bcdata.sgs." + code + "/dados?formato=json&dataInicial="
                + SGS_DATE.format(from) + "&dataFinal=" + SGS_DATE.format(to);
    }

    static String ptaxUrl(LocalDate from, LocalDate to) {
        return "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/"
                + "CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
                + "?@dataInicial='" + OLINDA_DATE.format(from) + "'&@dataFinalCotacao='" + OLINDA_DATE.format(to)
                + "'&$orderby=dataHoraCotacao%20desc&$top=1&$format=json";
    }

    /** Observações de uma série SGS entre {@code from} e {@code to}, em ordem crescente de data. */
    public List<Point> series(int code, LocalDate from, LocalDate to) {
        String url = seriesUrl(code, from, to);
        return executor.callPatiently(() -> parseSeries(http.get(url, Map.of()), to), ResilientExecutor.BACKGROUND_WAIT);
    }

    /** PTAX mais recente do período {@code [today - lookbackDays, today]} (um dia útil por cotação). */
    public Ptax latestPtax(LocalDate today, int lookbackDays) {
        String url = ptaxUrl(today.minusDays(lookbackDays), today);
        return executor.callPatiently(() -> parsePtax(http.get(url, Map.of())), ResilientExecutor.BACKGROUND_WAIT);
    }

    static List<Point> parseSeries(String json, LocalDate until) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (root == null || !root.isArray()) throw new IOException("BCB: " + errorText(root));
        List<Point> out = new ArrayList<>();
        for (JsonNode n : root) {
            try {
                LocalDate date = LocalDate.parse(n.path("data").asText(), SGS_DATE);
                BigDecimal value = new BigDecimal(n.path("valor").asText().strip());
                if (!date.isAfter(until)) out.add(new Point(date, value));
            } catch (DateTimeParseException | NumberFormatException e) {
                // Observação malformada é descartada; as demais continuam valendo.
            }
        }
        out.sort(Comparator.comparing(Point::date));
        if (out.isEmpty()) throw new IOException("BCB: série sem observações até " + until);
        return out;
    }

    static Ptax parsePtax(String json) throws IOException {
        JsonNode value = JSON.readTree(json).path("value");
        if (!value.isArray() || value.isEmpty()) throw new IOException("BCB: sem cotação PTAX no período");
        JsonNode q = value.get(0);
        if (!q.path("cotacaoCompra").isNumber() || !q.path("cotacaoVenda").isNumber()) {
            throw new IOException("BCB: cotação PTAX sem compra ou venda");
        }
        try {
            LocalDateTime time = LocalDateTime.parse(q.path("dataHoraCotacao").asText(), OLINDA_TIME);
            return new Ptax(q.path("cotacaoCompra").decimalValue(), q.path("cotacaoVenda").decimalValue(), time);
        } catch (DateTimeParseException e) {
            throw new IOException("BCB: horário da cotação PTAX ilegível", e);
        }
    }

    private static String errorText(JsonNode root) {
        if (root == null) return "resposta vazia";
        JsonNode erro = root.path("erro");
        if (erro.isTextual()) return erro.asText();
        if (erro.isObject()) return erro.path("detail").asText(erro.toString());
        return "resposta inesperada";
    }

    @Override
    public ProviderStatus status() { return executor.stats(true); }
}
