package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.domain.Exchange;
import dev.meridianwealth.domain.Instrument;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo dos ativos negociados (item #33, ADR 034): ações, FIIs, ETFs e BDRs da B3 e ações e ETFs dos EUA, montado a partir de listas
 * públicas de símbolos ({@code scripts/build-catalog.py}). Guarda só cadastro (símbolo, nome, classe, bolsa, setor e tipo): <b>nenhum preço</b>.
 * Preço, histórico e sinal vêm sempre do provedor real, sob demanda, quando o ativo é aberto.
 *
 * <p>O universo curado ({@link Universe}) tem prioridade: se um símbolo existe nos dois, vale o curado (por exemplo BHP, que no universo é a
 * ação australiana).
 */
public final class Catalog {
    private Catalog() {}

    /** Linha do catálogo, já traduzida para o cadastro do projeto. {@code kind} é o rótulo para a tela (Ação, BDR, Unit, FII, Fiagro, ETF...). */
    public record Entry(Instrument instrument, Symbols symbols, String kind) {}

    private record Row(String symbol, String name, String assetClass, Exchange exchange, String sector, String kind, String folded) {}

    private static final String RESOURCE = "/catalog/assets.tsv";
    private static final List<Row> ROWS = load();
    private static final Map<String, Row> BY_SYMBOL = index();

    private static List<Row> load() {
        try (InputStream in = Catalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Catálogo ausente: " + RESOURCE);
            List<Row> rows = new ArrayList<>(15_000);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line = r.readLine(); line != null; line = r.readLine()) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] f = line.split("\t", -1);
                    if (f.length != 6) throw new IllegalStateException("Linha inválida no catálogo: " + line);
                    rows.add(new Row(f[0], f[1], f[2], Exchange.valueOf(f[3]), f[4], f[5], fold(f[0] + " " + f[1])));
                }
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Row> index() {
        Map<String, Row> m = new HashMap<>(ROWS.size() * 2);
        for (Row r : ROWS) m.put(r.symbol(), r);
        return Map.copyOf(m);
    }

    /** Minúsculas e sem acento, para a busca achar "petrobras" e "ITAÚ" do mesmo jeito. */
    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static int size() { return ROWS.size(); }

    /** Procura pelo símbolo exato; o universo curado não entra aqui. */
    public static Optional<Entry> find(String symbol) {
        if (symbol == null) return Optional.empty();
        Row r = BY_SYMBOL.get(symbol.toUpperCase(Locale.ROOT));
        return r == null ? Optional.empty() : Optional.of(entry(r));
    }

    /**
     * Busca por símbolo ou nome. Ordem: símbolo igual, símbolo que começa com o texto, nome que começa com o texto, nome que contém o texto.
     * Dentro de cada grupo a B3 vem antes (é o mercado do dono do projeto) e depois a ordem alfabética.
     */
    public static List<Entry> search(String query, int limit) {
        String q = fold(query == null ? "" : query.trim());
        if (q.isEmpty() || limit < 1) return List.of();
        List<Row> exact = new ArrayList<>(), symPrefix = new ArrayList<>(), namePrefix = new ArrayList<>(), contains = new ArrayList<>();
        for (Row r : ROWS) {
            String sym = r.symbol().toLowerCase(Locale.ROOT);
            if (sym.equals(q)) exact.add(r);
            else if (sym.startsWith(q)) symPrefix.add(r);
            else if (r.folded().substring(r.symbol().length() + 1).startsWith(q)) namePrefix.add(r);
            else if (r.folded().contains(q)) contains.add(r);
        }
        List<Entry> out = new ArrayList<>(limit);
        for (List<Row> group : List.of(exact, symPrefix, namePrefix, contains)) {
            group.sort((a, b) -> a.exchange() == b.exchange() ? a.symbol().compareTo(b.symbol()) : a.exchange() == Exchange.B3 ? -1 : 1);
            for (Row r : group) {
                if (out.size() >= limit) return out;
                out.add(entry(r));
            }
        }
        return out;
    }

    private static Entry entry(Row r) {
        boolean b3 = r.exchange() == Exchange.B3;
        /* benchmark da força relativa: ações usam o índice do próprio mercado; fundos e ETFs ficam sem benchmark (como os FIIs do universo curado) */
        String bench = !"Ação".equals(r.assetClass()) ? null : b3 ? "IBOV" : "SPY";
        Instrument i = Instrument.of(r.symbol(), r.name(), r.exchange(), r.assetClass(), r.sector(), b3 ? "BRL" : "USD", bench);
        Symbols s = b3 ? Symbols.b3(r.symbol()) : new Symbols(r.symbol(), r.symbol().toLowerCase(Locale.ROOT) + ".us", r.symbol().replace('-', '.'),
                null, null, null, null);
        return new Entry(i, s, r.kind());
    }
}
