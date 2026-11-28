package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Perfil no visual Cosmos (item #52, parte 3): uma pergunta por tela, respostas só na memória da página e o cálculo continua no servidor. */
class ProfilePageContractTest {

    @Test
    void thePageAsksTheQuestionAndShowsOneQuestionPerScreenWithProgressAndBackNext() throws IOException {
        String html = StaticPage.file("index.html");
        assertThat(html).contains("Que tipo de investidor eu sou?").contains("Sete perguntas curtas, uma por tela").contains("id=\"sp\"");
        String js = StaticPage.file("app.js");
        assertThat(js).contains("function spWiz(").contains("Pergunta ${SPW.i+1} de ${qs.length}").contains("Nenhuma resposta é certa ou errada").contains("id=\"pfBack\"").contains("id=\"pfNext\"").contains("Ver meu perfil");
        assertThat(js).as("o formulário único de nove perguntas saiu").doesNotContain("id=\"spForm\"").doesNotContain("9 perguntas");
    }

    @Test
    void theNextButtonNeedsAnAnswerAndTheProfileIsStillComputedByTheServerWithoutStoringAnswers() throws IOException {
        String js = StaticPage.file("app.js");
        assertThat(js).contains("id=\"pfNext\"${cur?'':' disabled'}").contains("if(!SPW.a[SPQ.questions[SPW.i].id])return;");
        assertThat(js).contains("fetch('/api/suitability/evaluate'").contains("Responda todas as perguntas.");
        assertThat(js).as("as respostas ficam só na memória").doesNotContain("localStorage.setItem('mw-answers").doesNotContain("sessionStorage.setItem('mw-answers");
    }

    @Test
    void theWizardIsStyledAndKeyboardReachable() throws IOException {
        String css = StaticPage.file("cosmos.css");
        assertThat(css).contains(".pf-o:has(input:focus-visible)").contains(".pf-prog i.on");
        assertThat(StaticPage.file("app.js")).contains("t.focus()");
    }
}
