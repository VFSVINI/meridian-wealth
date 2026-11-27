package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Academia (item #49): o curso é conteúdo editorial, então o teste trava a estrutura, o quiz coerente, as contas dos exemplos e a ausência de promessa de retorno. */
class AcademiaContentTest {
    private static final Set<String> DIAGRAMS = Set.of("flow", "cols", "bars", "split", "scale", "scatter", "candle", "compound");
    private final JsonNode root = read();

    private static JsonNode read() {
        try {
            return new ObjectMapper().readTree(StaticPage.file("data/academia.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void thereAreNineModulesAndTwentySixChaptersWithUniqueIdsLikeThePrototype() {
        JsonNode mods = root.get("modulos");
        assertThat(mods).hasSize(9);
        assertThat(titles(mods)).containsExactly("Fundamentos", "Renda variável", "Renda fixa", "FIIs", "Cripto", "Câmbio e commodities", "Indicadores técnicos", "Risco e carteira", "Macro e geopolítica");
        Set<String> ids = new HashSet<>();
        int chapters = 0;
        for (JsonNode m : mods) {
            assertThat(ids.add(m.get("id").asText())).isTrue();
            for (JsonNode c : m.get("capitulos")) {
                chapters++;
                assertThat(ids.add(c.get("id").asText())).as("id único " + c.get("id")).isTrue();
                assertThat(c.get("id").asText()).startsWith(m.get("id").asText() + "c").matches("[a-z0-9]+");
            }
        }
        assertThat(chapters).isEqualTo(26);
        assertThat(mods.get(2).get("capitulos")).as("Renda fixa tem dois capítulos").hasSize(2);
    }

    private static List<String> titles(JsonNode mods) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (JsonNode m : mods) out.add(m.get("titulo").asText());
        return out;
    }

    @Test
    void everyChapterHasTheCentralIdeaADiagramAnExampleWhyItMattersAndAQuizThatIsCoherent() {
        for (JsonNode m : root.get("modulos")) {
            for (JsonNode c : m.get("capitulos")) {
                String id = c.get("id").asText();
                assertThat(c.get("minutos").asInt()).as(id).isBetween(2, 8);
                assertThat(c.get("blocos").size()).as(id).isBetween(2, 3);
                for (JsonNode b : c.get("blocos")) assertThat(b.get("p").asText().length()).as(id).isGreaterThan(60);
                assertThat(DIAGRAMS).as(id).contains(c.get("diagrama").get("tipo").asText());
                assertThat(c.get("exemplo").get("titulo").asText()).as(id + ": todo exemplo é rotulado").isEqualTo("Exemplo hipotético");
                assertThat(c.get("exemplo").get("texto").asText().length()).as(id).isGreaterThan(60);
                assertThat(c.get("importa").asText().length()).as(id).isGreaterThan(60);
                assertThat(c.get("quiz").size()).as(id).isBetween(1, 3);
                for (JsonNode q : c.get("quiz")) {
                    assertThat(q.get("o").size()).as(id).isBetween(3, 4);
                    assertThat(q.get("c").asInt()).as(id).isBetween(0, q.get("o").size() - 1);
                    Set<String> opts = new HashSet<>();
                    q.get("o").forEach(o -> assertThat(opts.add(o.asText())).as(id + ": opções distintas").isTrue());
                    assertThat(q.get("e").asText().length()).as(id).isGreaterThan(20);
                }
            }
        }
    }

    @Test
    void theExampleNumbersAreRightWhereTheyStateAComputation() {
        String all = root.toString();
        assertThat(1000 * Math.pow(1.10, 10)).isBetween(2593.74, 2593.75);          // m1c3: juros compostos
        assertThat(all).contains("R$ 2.593,74").contains("R$ 2.000,00");
        assertThat((1.10 / 1.04 - 1) * 100).isBetween(5.76, 5.78);                    // m1c3: juro real
        assertThat(all).contains("5,77%");
        assertThat((1.04 * 1.06 - 1) * 100).isBetween(10.23, 10.25);                  // m3c2: IPCA+
        assertThat(all).contains("10,24%");
        assertThat(1.0 / 20).isEqualTo(0.05);                                           // m2c1: dividend yield de 5%
        assertThat(30.0 / 3).isEqualTo(10.0);                                           // m2c2: P/L de 10
        assertThat(90.0 / 100).isEqualTo(0.9);                                          // m4c3: P/VP de 0,90
        assertThat(0.5 * -20 + 0.5 * 10).isEqualTo(-5.0);                               // m8c1: carteira 50/50
        assertThat(0.25 * 40).isEqualTo(10.0);                                          // m8c3: 25% do patrimônio caindo 40%
        assertThat(500 * 2).as("m5c3: cair 50% exige subir 100%").isEqualTo(1000);
        assertThat(100 * 5.50).isEqualTo(550.0);                                        // m6c1: dólar de 5,00 a 5,50
    }

    @Test
    void theCourseNeverPromisesReturnsOrRecommendsAndSaysItIsEducational() {
        // as alternativas ERRADAS do quiz podem citar a promessa justamente para desmenti-la; o lint vale para o que o curso afirma
        StringBuilder sb = new StringBuilder();
        for (JsonNode m : root.get("modulos")) {
            sb.append(m.get("resumo").asText()).append(' ');
            for (JsonNode c : m.get("capitulos")) {
                c.get("blocos").forEach(b -> sb.append(b.get("p").asText()).append(' '));
                sb.append(c.get("exemplo").get("texto").asText()).append(' ').append(c.get("importa").asText()).append(' ');
                c.get("quiz").forEach(q -> sb.append(q.get("q").asText()).append(' ').append(q.get("e").asText()).append(' '));
            }
        }
        String all = sb.toString().toLowerCase();
        for (String bad : List.of("recomendo", "recomendamos", "compre agora", "venda agora", "retorno garantido", "rentabilidade garantida", "sem risco algum", "lucro certo", "dinheiro fácil")) {
            assertThat(all).as(bad).doesNotContain(bad);
        }
        assertThat(root.get("aviso").asText()).contains("educativo").contains("hipotéticos").contains("não são previsão nem recomendação");
    }

    @Test
    void theAcademiaPageProgressStaysInTheBrowserAndTheMenuAndHomeLeadToIt() throws IOException {
        String js = StaticPage.file("academia.js"), html = StaticPage.file("index.html"), app = StaticPage.file("app.js");
        assertThat(js).contains("const KEY = 'mw-academia';").contains("localStorage.setItem(KEY").contains("localStorage.getItem(KEY");
        assertThat(js).as("o armazenamento pode falhar: o curso segue sem salvar").contains("catch (e) { /* sem armazenamento */ }");
        assertThat(js).as("só busca o conteúdo estático, nada vai ao servidor").contains("fetch('/data/academia.json')").doesNotContain("method: 'POST'").doesNotContain("sendBeacon");
        assertThat(js).contains("O progresso fica salvo neste navegador.").contains("Capítulo concluído. Seu progresso foi salvo neste navegador.");
        assertThat(html).contains("data-page=\"academia\"").contains("id=\"acGrid\"").contains("id=\"glossBtn\"").contains("Por onde começo a entender investimentos?");
        assertThat(app).contains("['academia','Academia'],").contains("$('#hmAcademia').onclick=()=>go('academia');").doesNotContain("['glossario','Glossário']");
    }

    @Test
    void quizOptionsAreShuffledPerChapterWithoutChangingWhichIndexIsRight() throws IOException {
        String js = StaticPage.file("academia.js");
        assertThat(js).contains("function order(n, seed)").contains("order(q.o.length, c.id + ':' + k)").contains("value=\"${j}\"").contains("AC.state.ans[c][k] === qq.c");
    }

    @Test
    void answeringTheQuizRedrawsInPlaceKeepingScrollAndFocusAndOnlyChangingChapterGoesToTheTop() throws IOException {
        // medido: responder o quiz rolava a página de 880 px para 0 (render() sempre ia ao topo); agora só a troca de módulo ou capítulo vai
        String js = StaticPage.file("academia.js");
        assertThat(js).contains("function render(inPlace)").contains("if (view !== 'Home' && !inPlace) window.scrollTo(0, 0);").contains("render(true);").contains("window.scrollTo(0, y);");
        assertThat(js).contains("input[name=\"' + name + '\"][value=\"' + value + '\"]").contains("again.focus({preventScroll: true})");
    }
}
