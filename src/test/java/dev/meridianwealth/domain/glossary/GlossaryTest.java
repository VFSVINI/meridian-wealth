package dev.meridianwealth.domain.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.RateSeries;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** O glossário é completo, consistente e ligado a cada métrica do painel (critério de aceite do item #9). */
class GlossaryTest {
    private static String html() throws IOException {
        return dev.meridianwealth.testsupport.StaticPage.whole();
    }

    /** Tabela GID do painel: padrão do rótulo -> id do glossário, lida do próprio index.html. */
    private static List<Object[]> gidTable() throws IOException {
        String h = html();
        int start = h.indexOf("const GID=[");
        int end = h.indexOf("const gid=", start);
        assertThat(start).as("tabela GID no index.html").isPositive();
        Matcher m = Pattern.compile("\\[/(.+?)/([a-z]*),'([a-z0-9-]+)'\\]").matcher(h.substring(start, end));
        List<Object[]> out = new ArrayList<>();
        while (m.find()) {
            int flags = m.group(2).contains("i") ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            out.add(new Object[] {Pattern.compile(m.group(1), flags), m.group(3)});
        }
        return out;
    }

    private static Optional<String> idFor(List<Object[]> table, String label) {
        return table.stream().filter(e -> ((Pattern) e[0]).matcher(label).find()).map(e -> (String) e[1]).findFirst();
    }

    @Test void everyEntryExplainsWhatHowAndWhenInPlainPortuguese() {
        assertThat(Glossary.all()).hasSizeGreaterThanOrEqualTo(55);
        assertThat(Glossary.all().stream().map(Glossary.Entry::id).distinct()).hasSameSizeAs(Glossary.all());
        for (Glossary.Entry e : Glossary.all()) {
            assertThat(e.id()).matches("[a-z0-9-]+");
            assertThat(e.term()).as(e.id()).isNotBlank();
            assertThat(e.what()).as(e.id() + " o que é").hasSizeGreaterThan(20);
            assertThat(e.howToRead()).as(e.id() + " como ler").hasSizeGreaterThan(30);
            assertThat(e.whenToWorry()).as(e.id() + " quando se preocupar").hasSizeGreaterThan(20);
            assertThat(e.related()).as(e.id()).doesNotContain(e.id());
            e.related().forEach(r -> assertThat(Glossary.find(r)).as(e.id() + " -> " + r).isPresent());
        }
        assertThat(Glossary.all().stream().map(Glossary.Entry::group).collect(Collectors.toSet())).containsExactlyInAnyOrder(Glossary.Group.values());
    }

    @Test void theGlossaryNeverPresentsGuidanceAsAdviceOrCertainty() {
        for (Glossary.Entry e : Glossary.all()) {
            String all = (e.what() + " " + e.howToRead() + " " + e.whenToWorry()).toLowerCase();
            assertThat(all).as(e.id()).doesNotContain("compre ").doesNotContain("venda agora").doesNotContain("garantido").doesNotContain("sem risco");
        }
    }

    @Test void sharpeAndExpectationAdmitWhatTheCodeActuallyDoes() {
        assertThat(Glossary.find("sharpe").orElseThrow().what()).contains("não se desconta a taxa livre de risco");
        assertThat(Glossary.find("expected-value").orElseThrow().whenToWorry()).contains("heurística").contains("não uma probabilidade estatística");
        assertThat(Glossary.find("simulation").orElseThrow().howToRead()).contains("não é cotação").contains("não executa ordens");
    }

    @Test void everyIdUsedByThePanelExistsInTheGlossary() throws IOException {
        String h = html();
        List<String> ids = new ArrayList<>();
        Matcher gl = Pattern.compile("GL\\('([a-z0-9-]+)'\\)").matcher(h);
        while (gl.find()) ids.add(gl.group(1));
        gidTable().forEach(e -> ids.add((String) e[1]));
        assertThat(ids).hasSizeGreaterThan(50);
        ids.forEach(id -> assertThat(Glossary.find(id)).as("id do painel: " + id).isPresent());
    }

    @Test void everyMetricLabelOfThePanelLinksToAGlossaryEntry() throws IOException {
        List<Object[]> table = gidTable();
        List<String> labels = new ArrayList<>(Arrays.asList(
                // cartão de timing e métricas quantitativas
                "Regime", "Confiança", "Risco", "Exposição sugerida", "Entrada", "Invalidação", "Alvo 1", "Alvo 2", "Z-Score (50)", "Percentil", "RSI (14)",
                "MACD hist.", "Vol. anual. (√252)", "Vol. anual. (√365)", "Max drawdown", "Sharpe", "VaR 95% (1 barra)", "ATR (14)", "Valor justo (SMA50)", "Upside",
                "Downside", "Risco/retorno", "Expectativa", "Força relativa", "Volume relativo", "Beta vs IBOV (90)",
                // microeconomia
                "Setor", "Benchmark", "Força relativa (60)", "Valor de mercado", "P/L", "Margem líquida", "Cresc. receita",
                // macro
                "VIX", "Índice do dólar", "Yield 10 anos", "Risco geopolítico",
                // backtest
                "Estratégia", "Comprar e segurar", "Acerto",
                // simulador e carteira
                "Maior posição", "HHI", "Posições efetivas", "Volatilidade ponderada", "Cobertura da volatilidade", "Aderência ao perfil"));
        // juros e inflação do Brasil: os rótulos vêm do catálogo real de séries
        Arrays.stream(RateSeries.values()).forEach(s -> labels.add(s.label()));
        for (String label : labels) assertThat(idFor(table, label)).as("rótulo sem glossário: " + label).isPresent();
    }

    @Test void selicEntryExplainsTheForwardFilledDateInsteadOfClaimingTheDecisionDate() {
        // a série 432 repete o valor vigente: "vigente em" é a data de hoje, não a da última decisão do Copom
        String worry = Glossary.find("selic").orElseThrow().whenToWorry();
        assertThat(worry).contains("vigente em").contains("data de hoje").contains("não é a da última decisão").contains("nem mede a idade do dado");
        assertThat(worry).doesNotContain("vale até a próxima reunião");
        assertThat(RateSeries.SELIC_TARGET.dateLabel()).isEqualTo("vigente em");
    }

    @Test void theSimulatorIsRenderedOnlyThroughTheGuardThatWaitsForTheUniverseAndTheTaxonomy() throws IOException {
        String h = html();
        // renderSimulator() só pode ser chamada de dentro da guarda: senão volta a corrida com o /api/universe
        Matcher calls = Pattern.compile("renderSimulator\\(\\)").matcher(h);
        int count = 0;
        while (calls.find()) count++;
        assertThat(count).as("definição + chamada da guarda").isEqualTo(2);
        assertThat(h).contains("function tryRenderSimulator()").contains("SPTYPES.length&&universe.length");
        assertThat(h.split("tryRenderSimulator\\(\\)", -1).length - 1).as("guarda chamada pelo init e pelo suitability + definição").isGreaterThanOrEqualTo(3);
    }
}
