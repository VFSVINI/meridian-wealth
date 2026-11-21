package dev.meridianwealth.infrastructure.bcb;

import static dev.meridianwealth.testsupport.FixtureHttpGateway.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.Rate;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsers do BCB contra respostas reais gravadas em 2026-11-21 e contra os erros reais observados. */
class BcbClientTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-11-21");

    private static List<BcbClient.Point> sgs(int code) throws IOException {
        return BcbClient.parseSeries(resource("/fixtures/bcb/sgs-" + code + ".json"), TODAY);
    }

    @Test void selicTargetFromTheRealPayloadKeepsTheExactDecimalAndTheDayOfTheLastValue() throws IOException {
        List<BcbClient.Point> p = sgs(432);
        assertThat(p).isNotEmpty();
        BcbClient.Point last = p.get(p.size() - 1);
        assertThat(last.date()).isEqualTo(TODAY);
        assertThat(last.value()).isEqualTo(new java.math.BigDecimal("13.75"));
        assertThat(p).extracting(BcbClient.Point::date).isSorted();
    }

    @Test void datesAfterTheCutoffAreDroppedBecauseSeries432RunsIntoTheFuture() throws IOException {
        // No modo "últimos N" a API devolveu 08/12 e 09/12/2026 com a série vigente repetida.
        String json = "[{\"data\":\"20/11/2026\",\"valor\":\"13.75\"},{\"data\":\"08/12/2026\",\"valor\":\"13.75\"},"
                + "{\"data\":\"09/12/2026\",\"valor\":\"13.75\"}]";
        List<BcbClient.Point> p = BcbClient.parseSeries(json, TODAY);
        assertThat(p).extracting(BcbClient.Point::date).containsExactly(LocalDate.parse("2026-11-20"));
    }

    @Test void descendingResponsesAreSortedBecauseTheApiDoesNotGuaranteeOrder() throws IOException {
        // Resposta real da série 4389 em "últimos 2": veio da data mais nova para a mais antiga.
        String json = "[{\"data\":\"19/11/2026\",\"valor\":\"13.65\"},{\"data\":\"18/11/2026\",\"valor\":\"13.65\"}]";
        assertThat(BcbClient.parseSeries(json, TODAY)).extracting(BcbClient.Point::date)
                .containsExactly(LocalDate.parse("2026-11-18"), LocalDate.parse("2026-11-19"));
    }

    @Test void realCdiSeriesAreMutuallyConsistent() throws IOException {
        BcbClient.Point daily = last(sgs(12));
        BcbClient.Point annual = last(sgs(4389));
        assertThat(new Rate(daily.value()).compoundedOver(252).rounded(2).percent()).isEqualByComparingTo(annual.value());
        assertThat(daily.date()).isEqualTo(annual.date());
    }

    @Test void realIpcaSeriesAreMutuallyConsistent() throws IOException {
        List<BcbClient.Point> monthly = sgs(433);
        BcbClient.Point twelve = last(sgs(13522));
        assertThat(monthly).hasSizeGreaterThanOrEqualTo(12);
        java.math.BigDecimal factor = java.math.BigDecimal.ONE;
        for (BcbClient.Point m : monthly.subList(monthly.size() - 12, monthly.size())) {
            factor = factor.multiply(java.math.BigDecimal.ONE.add(new Rate(m.value()).fraction()));
        }
        assertThat(new Rate(factor.subtract(java.math.BigDecimal.ONE).movePointRight(2)).rounded(2).percent())
                .isEqualByComparingTo(twelve.value());
        assertThat(last(monthly).date()).as("a data do IPCA é o primeiro dia do mês de referência").isEqualTo(twelve.date());
        assertThat(last(monthly).date().getDayOfMonth()).isEqualTo(1);
    }

    private static BcbClient.Point last(List<BcbClient.Point> p) { return p.get(p.size() - 1); }

    @Test void realErrorShapesBecomeClearExceptions() {
        assertThatThrownBy(() -> BcbClient.parseSeries(
                "{\"erro\":{\"statusCode\":404,\"detail\":\"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found\"}}", TODAY))
                .isInstanceOf(IOException.class).hasMessageContaining("Value(s) not found");
        assertThatThrownBy(() -> BcbClient.parseSeries(
                "{\"erro\":\"dataInicial invalida. Informe uma data existente no formato dd/mm/aaaa.\"}", TODAY))
                .hasMessageContaining("dataInicial invalida");
        assertThatThrownBy(() -> BcbClient.parseSeries("A serie nao existe!", TODAY)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> BcbClient.parseSeries("[]", TODAY)).hasMessageContaining("sem observações");
        assertThatThrownBy(() -> BcbClient.parseSeries("{}", TODAY)).hasMessageContaining("inesperada");
    }

    @Test void malformedPointsAreSkippedAndTheRestSurvive() throws IOException {
        String json = "[{\"data\":\"2026-11-19\",\"valor\":\"1\"},{\"data\":\"19/11/2026\",\"valor\":\"\"},"
                + "{\"data\":\"19/11/2026\",\"valor\":\"abc\"},{\"data\":\"18/11/2026\",\"valor\":\" 0.05 \"}]";
        List<BcbClient.Point> p = BcbClient.parseSeries(json, TODAY);
        assertThat(p).hasSize(1);
        assertThat(p.get(0).value()).isEqualTo(new java.math.BigDecimal("0.05"));
    }

    @Test void ptaxRealPayloadIsReadAsDecimalWithoutPassingThroughDouble() throws IOException {
        BcbClient.Ptax ptax = BcbClient.parsePtax(resource("/fixtures/bcb/ptax-periodo.json"));
        assertThat(ptax.buy()).isEqualByComparingTo("5.8883");
        assertThat(ptax.sell()).isEqualByComparingTo("5.8889");
        assertThat(ptax.time()).isEqualTo(LocalDateTime.parse("2026-11-19T13:04:18.325373"));
        assertThat(ptax.sell()).isGreaterThan(ptax.buy());
    }

    @Test void ptaxToleratesVariableFractionDigitsAndRejectsIncompleteAnswers() throws IOException {
        String five = "{\"value\":[{\"cotacaoCompra\":5.7845,\"cotacaoVenda\":5.7851,\"dataHoraCotacao\":\"2026-11-17 13:07:26.01516\"}]}";
        assertThat(BcbClient.parsePtax(five).time()).isEqualTo(LocalDateTime.parse("2026-11-17T13:07:26.015160"));
        assertThatThrownBy(() -> BcbClient.parsePtax("{\"value\":[]}")).hasMessageContaining("sem cotação PTAX");
        assertThatThrownBy(() -> BcbClient.parsePtax("{\"value\":[{\"cotacaoVenda\":5.7851,\"dataHoraCotacao\":\"2026-11-17 13:07:26\"}]}"))
                .hasMessageContaining("sem compra ou venda");
        assertThatThrownBy(() -> BcbClient.parsePtax("{\"value\":[{\"cotacaoCompra\":1,\"cotacaoVenda\":2,\"dataHoraCotacao\":\"ontem\"}]}"))
                .hasMessageContaining("ilegível");
    }

    @Test void urlsFollowTheOfficialFormats() {
        assertThat(BcbClient.seriesUrl(432, LocalDate.parse("2026-10-07"), TODAY)).isEqualTo(
                "https://api.bcb.gov.br/dados/serie/bcdata.sgs.432/dados?formato=json&dataInicial=07/10/2026&dataFinal=21/11/2026");
        assertThat(BcbClient.ptaxUrl(LocalDate.parse("2026-11-11"), TODAY)).isEqualTo(
                "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoDolarPeriodo(dataInicial=@dataInicial,"
                        + "dataFinalCotacao=@dataFinalCotacao)?@dataInicial='11-11-2026'&@dataFinalCotacao='11-21-2026'"
                        + "&$orderby=dataHoraCotacao%20desc&$top=1&$format=json");
    }
}
