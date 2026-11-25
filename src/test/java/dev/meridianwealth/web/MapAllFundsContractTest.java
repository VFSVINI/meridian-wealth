package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Mapa para todos os FIIs (item #34): a tela aguenta centenas de fundos sem virar um muro de botões nem de linhas, e nenhuma posição é inventada. */
class MapAllFundsContractTest {

    @Test
    void theFundFilterIsASearchWithADatalistNotOneButtonPerFund() throws IOException {
        String js = StaticPage.file("mapa.js");
        assertThat(js).contains("id=\"mpFundQ\" type=\"search\" list=\"mpFundList\"").contains("<datalist id=\"mpFundList\">").contains("e.target.id!=='mpFundQ'");
        assertThat(js).as("a lista de botões por fundo saiu").doesNotContain("...MP.funds.filter(fd=>fd.assets.length).map(fd=>[fd.symbol,fd.symbol])");
    }

    @Test
    void theListShowsAPageAtATimeTheCompareTableIsCappedAndTheSourcesAreSummarized() throws IOException {
        String js = StaticPage.file("mapa.js");
        assertThat(js).contains("items.slice(0,MP.limit)").contains("id=\"mpMore\"").contains("MP.limit+=60");
        assertThat(js).contains("const MAXC=15").contains("Mostrando os ${MAXC} maiores por ABL listada");
        assertThat(js).contains("bySrc[fd.source.name]").contains("fundo${fds.length===1?'':'s'}");
    }

    @Test
    void aPinWithoutACoordinateStillNeverGetsAMarkerAndTheReasonIsShown() throws IOException {
        String js = StaticPage.file("mapa.js");
        assertThat(js).contains("const located=mpFiltered().filter(x=>x.a.coordinates)").contains("a.noCoordinatesReason||'motivo não informado'");
    }

    @Test
    void jsonResponsesAreCompressedBecauseTheWholeCompositionIsBig() throws IOException {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(yml).contains("compression:").contains("enabled: true").contains("application/json").contains("min-response-size: 2KB");
    }
}
