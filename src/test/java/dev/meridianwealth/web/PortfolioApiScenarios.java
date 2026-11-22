package dev.meridianwealth.web;

import static dev.meridianwealth.testsupport.Http.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.Http;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Cenários da carteira e do livro de movimentações pela borda HTTP, com cotações e PTAX reais gravados (fixtures). Rodam duas vezes: com
 * a persistência em memória (sem Docker) e com o PostgreSQL de verdade, para provar que o comportamento é o mesmo.
 */
abstract class PortfolioApiScenarios {
    static final String PASSWORD = "uma frase longa e boa";

    @Value("${local.server.port}")
    int port;

    Http http;

    @Autowired
    dev.meridianwealth.infrastructure.bcb.LiveBrazilRates brazilRates;

    @Autowired
    MeterRegistry metrics;

    @BeforeEach
    void client() { http = new Http(port); }

    String newUserToken() {
        String email = "carteira-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
        Http.Reply r = http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Carteira"));
        assertThat(r.status()).isEqualTo(201);
        return (String) r.get("accessToken");
    }

    Http.Reply open(String token, Map<String, Object> body) { return http.post("/api/me/portfolio/positions", bearer(token), body); }

    Http.Reply open(String token, Map<String, Object> body, String key) {
        return http.post("/api/me/portfolio/positions", merge(bearer(token), Map.of("Idempotency-Key", key)), body);
    }

    static Map<String, String> merge(Map<String, String> a, Map<String, String> b) {
        Map<String, String> m = new java.util.HashMap<>(a);
        m.putAll(b);
        return m;
    }

    Map<String, Object> typed(String label, String qty, String price) { return Map.of("type", "LCI", "label", label, "quantity", qty, "averagePrice", price); }

    Map<String, Object> typedOn(String type, String label, String qty, String price, String date) {
        return Map.of("type", type, "label", label, "quantity", qty, "averagePrice", price, "tradeDate", date);
    }

    @Test void exportBringsEverythingTheUserHasAndNothingSecretAndEraseWipesItAllOnlyWithTheConfirmedEmail() {
        String email = "lgpd-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
        Http.Reply reg = http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Titular"));
        String token = (String) reg.get("accessToken");
        String refresh = (String) reg.get("refreshToken");
        Http.Reply op = open(token, typed("LCI do titular", "1", "1000"), "k-lgpd");
        assertThat(op.status()).isEqualTo(201);
        String other = newUserToken();
        open(other, typed("LCI de outra pessoa", "1", "5000"));

        assertThat(http.get("/api/me/export").status()).as("sem token").isEqualTo(401);
        Http.Reply export = http.get("/api/me/export", bearer(token));
        assertThat(export.status()).isEqualTo(200);
        assertThat(export.header("Content-Disposition")).contains("attachment").contains(".json");
        assertThat(export.obj("account").get("email")).isEqualTo(email);
        assertThat((List<?>) export.json().get("positions")).hasSize(1);
        assertThat((List<?>) export.json().get("ledger")).hasSize(1);
        assertThat(export.body()).as("nada de outra pessoa").doesNotContain("de outra pessoa");
        assertThat(export.body()).as("nem segredo").doesNotContain(PASSWORD).doesNotContain("passwordHash").doesNotContain("$2").doesNotContain(refresh);

        assertThat(http.post("/api/me/erase", bearer(token), Map.of("confirmEmail", "outro@exemplo.com")).status()).as("e-mail errado").isEqualTo(400);
        assertThat(http.post("/api/me/erase", bearer(token), Map.of()).status()).as("sem confirmação").isEqualTo(400);
        assertThat(http.get("/api/me", bearer(token)).status()).as("nada foi apagado ainda").isEqualTo(200);

        Http.Reply erase = http.post("/api/me/erase", bearer(token), Map.of("confirmEmail", email.toUpperCase()));
        assertThat(erase.status()).isEqualTo(200);
        assertThat(erase.get("positions")).isEqualTo(1);
        assertThat(erase.get("ledgerEntries")).isEqualTo(1);
        assertThat(http.get("/api/me", bearer(token)).status()).as("a conta não existe mais").isEqualTo(401);
        assertThat(http.post("/api/auth/refresh", Map.of("refreshToken", refresh)).status()).as("as sessões caíram").isEqualTo(401);
        assertThat(http.post("/api/auth/login", Map.of("email", email, "password", PASSWORD)).status()).isEqualTo(401);
        assertThat(http.get("/api/me/portfolio", bearer(other)).status()).isEqualTo(200);
        assertThat(http.get("/api/me/portfolio", bearer(other)).json().get("positions").toString()).as("o outro titular segue intacto").contains("de outra pessoa");
        assertThat(http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "De novo")).status()).as("o e-mail volta a ficar livre").isEqualTo(201);
    }

    @Test void monteCarloOverThePublicAssumptionsAndOverTheSavedPortfolioWithRealHistory() {
        Http.Reply open = http.post("/api/risk/monte-carlo", Map.of("initial", "100000", "monthlyContribution", "1000", "horizonYears", 10, "expectedReturnPercent", "10", "volatilityPercent", "15", "goal", "300000", "seed", 7));
        assertThat(open.status()).as("as premissas manuais não exigem login").isEqualTo(200);
        assertThat(open.get("kind")).isEqualTo("PROJECTION");
        assertThat((String) open.get("label")).startsWith("PROJEÇÃO");
        assertThat(open.get("mode")).isEqualTo("PARAMETRIC");
        assertThat((List<?>) open.json().get("points")).hasSize(121);
        assertThat(open.get("disclaimer").toString()).contains("não é promessa de retorno");
        Http.Reply again = http.post("/api/risk/monte-carlo", Map.of("initial", "100000", "monthlyContribution", "1000", "horizonYears", 10, "expectedReturnPercent", "10", "volatilityPercent", "15", "goal", "300000", "seed", 7));
        assertThat(again.body()).as("mesma semente, mesma projeção").isEqualTo(open.body());
        assertThat(http.post("/api/risk/monte-carlo", Map.of("initial", "100000", "horizonYears", 99, "expectedReturnPercent", "10", "volatilityPercent", "15")).status()).isEqualTo(400);
        assertThat(http.post("/api/risk/monte-carlo", Map.of("initial", "1E5000000", "horizonYears", 5, "expectedReturnPercent", "10", "volatilityPercent", "15")).status()).as("notação científica absurda").isEqualTo(400);
        assertThat(http.post("/api/risk/monte-carlo", Map.of("horizonYears", 5, "expectedReturnPercent", "10", "volatilityPercent", "15")).status()).as("sem inicial nem aporte").isEqualTo(400);

        assertThat(http.post("/api/me/risk/monte-carlo", Map.of("horizonYears", 5)).status()).as("a carteira exige login").isEqualTo(401);
        String token = newUserToken();
        Http.Reply empty = http.post("/api/me/risk/monte-carlo", bearer(token), Map.of("horizonYears", 5));
        assertThat(empty.status()).as("carteira vazia: recusa, não inventa").isEqualTo(422);
        assertThat(empty.get("code")).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(open(token, Map.of("symbol", "PETR4", "quantity", "100", "averagePrice", "30", "tradeDate", "2026-11-10")).status()).isEqualTo(201);
        assertThat(open(token, typed("LCI do titular", "1", "10000")).status()).isEqualTo(201);
        Http.Reply mc = http.post("/api/me/risk/monte-carlo", bearer(token), Map.of("monthlyContribution", "500", "horizonYears", 5, "goal", "60000"));
        assertThat(mc.status()).isEqualTo(200);
        assertThat(mc.get("mode")).isEqualTo("PORTFOLIO_BOOTSTRAP");
        List<Map<String, Object>> coverage = (List<Map<String, Object>>) mc.json().get("coverage");
        assertThat(coverage).extracting(c -> c.get("treatment")).containsExactlyInAnyOrder("HISTORICO", "CONSTANTE");
        assertThat(coverage).filteredOn(c -> "CONSTANTE".equals(c.get("treatment"))).extracting(c -> c.get("reason").toString()).allMatch(r -> r.contains("não é negociado em mercado"));
        assertThat(mc.obj("statistics").get("commonDays")).isNotNull();
        assertThat(((Number) mc.get("coveredPercent")).doubleValue()).isBetween(1.0, 99.0);
        assertThat((List<?>) mc.json().get("points")).hasSize(61);
        assertThat(mc.body()).as("nada de outro usuário nem de segredo").doesNotContain("passwordHash").doesNotContain(PASSWORD);
        assertThat(http.post("/api/me/risk/monte-carlo", bearer(newUserToken()), Map.of("horizonYears", 5)).status()).as("o outro usuário tem a carteira dele, vazia").isEqualTo(422);
    }

    String idOf(Http.Reply operation) { return (String) operation.obj("position").get("id"); }

    String path(String id, String op) { return "/api/me/portfolio/positions/" + id + "/" + op; }

    Http.Reply buy(String t, String id, Map<String, Object> body) { return http.post(path(id, "purchases"), bearer(t), body); }

    Http.Reply buy(String t, String id, Map<String, Object> body, String key) {
        return http.post(path(id, "purchases"), merge(bearer(t), Map.of("Idempotency-Key", key)), body);
    }

    Http.Reply sell(String t, String id, Map<String, Object> body) { return http.post(path(id, "sales"), bearer(t), body); }

    /** O serviço conta as datas em Brasília; a JVM da CI roda em UTC, então "hoje" nos testes também é o de Brasília. */
    static LocalDate brasilia() { return LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")); }

    static BigDecimal num(Object o) { return new BigDecimal(String.valueOf(o)); }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> positions(String token) { return (List<Map<String, Object>>) http.get("/api/me/portfolio", bearer(token)).json().get("positions"); }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ledger(String token, String query) {
        return (List<Map<String, Object>>) http.get("/api/me/portfolio/transactions" + query, bearer(token)).json().get("entries");
    }

    double counter(String type) { return metrics.counter("meridianwealth.portfolio.events", "type", type).count(); }

    @Test void theWeightedAverageFollowsPurchasesWithFeesAndSalesKeepTheAverageAndRealizeTheResult() {
        String t = newUserToken();
        Http.Reply opened = open(t, typed("LCI Banco Teste 2028", "10", "1000"));
        assertThat(opened.status()).isEqualTo(201);
        assertThat(opened.obj("position").get("assetKey")).isEqualTo("T:LCI:lci-banco-teste-2028");
        assertThat(opened.obj("position").get("currency")).isEqualTo("BRL");
        assertThat(opened.obj("entry").get("entryType")).isEqualTo("OPENING");
        assertThat(((Number) opened.obj("position").get("version")).longValue()).isZero();
        String id = idOf(opened);

        // 10 a 1.000 + 10 a 1.100 com R$ 200 de corretagem: (10.000 + 11.000 + 200) / 20 = 1.060
        Http.Reply bought = buy(t, id, Map.of("quantity", "10", "unitPrice", "1100", "fees", "200"));
        assertThat(bought.status()).isEqualTo(200);
        assertThat(bought.obj("entry").get("entryType")).isEqualTo("BUY");
        assertThat(num(bought.obj("position").get("averagePrice"))).isEqualByComparingTo("1060");
        assertThat(num(bought.obj("position").get("quantity"))).isEqualByComparingTo("20");
        assertThat(((Number) bought.obj("position").get("version")).longValue()).isEqualTo(1);
        assertThat(num(bought.obj("position").get("costBasis"))).isEqualByComparingTo("21200.00");

        // vende 5 a 1.200 com R$ 50 de taxas: líquido 6.000 - 50 = 5.950; custo 5 x 1.060 = 5.300; resultado 650
        Http.Reply sold = sell(t, id, Map.of("quantity", "5", "unitPrice", "1200", "fees", "50"));
        assertThat(sold.get("closed")).isEqualTo(false);
        assertThat(num(sold.obj("entry").get("realizedPnl"))).isEqualByComparingTo("650.00");
        assertThat(num(sold.obj("entry").get("realizedPnlBrl"))).isEqualByComparingTo("650.00");
        assertThat(num(sold.obj("position").get("quantity"))).isEqualByComparingTo("15");
        assertThat(num(sold.obj("position").get("averagePrice"))).as("a venda mantém o preço médio").isEqualByComparingTo("1060");

        assertThat(sell(t, id, Map.of("quantity", "15.5", "unitPrice", "1")).status()).as("vender mais do que tem").isEqualTo(400);
        assertThat(sell(t, id, Map.of("quantity", "1")).status()).as("a venda exige o preço").isEqualTo(400);
        Http.Reply closed = sell(t, id, Map.of("quantity", "15", "unitPrice", "900"));
        assertThat(closed.get("closed")).isEqualTo(true);
        assertThat(closed.get("position")).isNull();
        assertThat(num(closed.obj("entry").get("realizedPnl"))).as("15 x 900 - 15 x 1.060").isEqualByComparingTo("-2400.00");
        assertThat(positions(t)).isEmpty();

        List<Map<String, Object>> entries = ledger(t, "");
        assertThat(entries).extracting(e -> e.get("entryType")).containsExactly("OPENING", "BUY", "SELL", "SELL");
        assertThat(num(entries.get(3).get("quantityAfter"))).isEqualByComparingTo("0");
        assertThat(entries.get(3).get("averagePriceAfter")).isNull();
    }

    @Test void positionsAreValuedWithRealQuotesTheOfficialPtaxAndTheBrlCostAtThePurchaseRate() {
        brazilRates.refresh();
        String t = newUserToken();
        assertThat(open(t, Map.of("symbol", "petr4", "quantity", "100", "averagePrice", "30")).status()).isEqualTo(201);
        Http.Reply spyOpened = open(t, Map.of("symbol", "SPY", "quantity", "10", "averagePrice", "500"));
        assertThat(spyOpened.status()).isEqualTo(201);
        Map<String, Object> spyEntry = spyOpened.obj("entry");
        assertThat(spyEntry.get("fxSource")).as("sem câmbio informado, o PTAX do Banco Central da data").isEqualTo("PTAX");
        BigDecimal purchaseRate = num(spyEntry.get("fxBrlPerUsd"));
        assertThat(purchaseRate.signum()).isPositive();
        assertThat(spyEntry.get("fxDate")).isNotNull();
        Http.Reply btcOpened = open(t, Map.of("type", "CRIPTO_ATIVO", "label", "Bitcoin guardado", "currency", "USD", "quantity", "0.5", "averagePrice", "60000"));
        assertThat(btcOpened.status()).isEqualTo(201);

        Map<String, Object> r = http.get("/api/me/portfolio", bearer(t)).json();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) r.get("positions");
        assertThat(lines).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> fx = (Map<String, Object>) r.get("fx");
        assertThat(fx).as("PTAX real do Banco Central, com data e fonte").isNotNull();
        assertThat((String) fx.get("source")).contains("PTAX").contains("Banco Central");
        assertThat(fx.get("date")).isNotNull();
        BigDecimal rate = num(fx.get("brlPerUsd"));

        Map<String, Object> petr = lines.stream().filter(l -> "PETR4".equals(((Map<?, ?>) l.get("position")).get("assetKey"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> petrPrice = (Map<String, Object>) petr.get("price");
        assertThat(petr.get("basis")).isEqualTo("MARKET");
        assertThat(petrPrice.get("source")).isNotNull();
        assertThat(petrPrice.get("freshness")).isNotNull();
        assertThat(petrPrice.get("quoteTime")).isNotNull();
        assertThat(petrPrice.get("currency")).isEqualTo("BRL");
        BigDecimal expectedMarket = num("100").multiply(num(petrPrice.get("price"))).setScale(2, RoundingMode.HALF_EVEN);
        assertThat(num(petr.get("marketValue"))).isEqualByComparingTo(expectedMarket);
        assertThat(num(petr.get("unrealizedPnl"))).isEqualByComparingTo(expectedMarket.subtract(new BigDecimal("3000.00")));
        assertThat(num(petr.get("currentValueBrl"))).isEqualByComparingTo(expectedMarket);
        assertThat(num(petr.get("unrealizedPnlBrl"))).isEqualByComparingTo(expectedMarket.subtract(new BigDecimal("3000.00")));

        Map<String, Object> spy = lines.stream().filter(l -> "SPY".equals(((Map<?, ?>) l.get("position")).get("assetKey"))).findFirst().orElseThrow();
        assertThat(spy.get("basis")).isEqualTo("MARKET");
        BigDecimal spyMarket = num(spy.get("marketValue"));
        BigDecimal spyCostBrl = new BigDecimal("5000").multiply(purchaseRate).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal spyCurrentBrl = spyMarket.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
        assertThat(num(spy.get("costBasisBrl"))).as("custo em reais ao câmbio da compra").isEqualByComparingTo(spyCostBrl);
        assertThat(num(spy.get("currentValueBrl"))).as("valor em dólar x PTAX de hoje").isEqualByComparingTo(spyCurrentBrl);
        assertThat(num(spy.get("unrealizedPnlBrl")).subtract(spyCurrentBrl.subtract(spyCostBrl)).abs()).as("valor de hoje menos custo ao câmbio da compra (inclui a variação cambial)")
                .isLessThanOrEqualTo(new BigDecimal("0.01"));

        Map<String, Object> btc = lines.stream().filter(l -> ((String) ((Map<?, ?>) l.get("position")).get("assetKey")).startsWith("T:CRIPTO")).findFirst().orElseThrow();
        assertThat(btc.get("basis")).as("sem cotação: avaliada pelo custo").isEqualTo("COST");
        assertThat(btc.get("price")).isNull();
        assertThat(btc.get("marketValue")).isNull();
        assertThat(btc.get("unrealizedPnl")).as("não informado, nunca estimado").isNull();
        assertThat(btc.get("unrealizedPnlBrl")).isNull();
        BigDecimal btcRate = num(btcOpened.obj("entry").get("fxBrlPerUsd"));
        assertThat(num(btc.get("costBasisBrl"))).isEqualByComparingTo(new BigDecimal("30000").multiply(btcRate).setScale(2, RoundingMode.HALF_EVEN));
        assertThat(num(btc.get("currentValueBrl"))).isEqualByComparingTo(new BigDecimal("30000").multiply(rate).setScale(2, RoundingMode.HALF_EVEN));

        BigDecimal total = num(petr.get("currentValueBrl")).add(num(spy.get("currentValueBrl"))).add(num(btc.get("currentValueBrl")));
        assertThat(num(r.get("totalCurrentValueBrl"))).isEqualByComparingTo(total);
        assertThat(((Number) r.get("positionsWithoutBrlValue")).intValue()).isZero();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byClass = (List<Map<String, Object>>) r.get("byClass");
        assertThat(byClass.stream().map(c -> num(c.get("percent"))).reduce(BigDecimal.ZERO, BigDecimal::add).subtract(new BigDecimal("100")).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
        assertThat((String) r.get("disclaimer")).contains("não executa ordens").contains("não é recomendação");
        assertThat(((List<?>) r.get("notes")).toString()).contains("custo").contains("data de cada compra");
    }

    @Test void aUserSuppliedExchangeRateIsRecordedAsSuchAndDrivesTheBrlResult() {
        String t = newUserToken();
        Http.Reply opened = open(t, Map.of("type", "CRIPTO_ATIVO", "label", "Cripto na corretora", "currency", "USD", "quantity", "2", "averagePrice", "1000", "fxRate", "5.00"));
        assertThat(opened.status()).isEqualTo(201);
        assertThat(opened.obj("entry").get("fxSource")).isEqualTo("USER");
        assertThat(num(opened.obj("position").get("averageCostBrl"))).isEqualByComparingTo("5000");
        String id = idOf(opened);
        // vende 1 a US$ 1.200 com câmbio da nota 6,00 e US$ 10 de taxas: líquido US$ 1.190; custo US$ 1.000; resultado US$ 190; em reais 7.140 - 5.000 = 2.140
        Http.Reply sold = sell(t, id, Map.of("quantity", "1", "unitPrice", "1200", "fees", "10", "fxRate", "6.00"));
        assertThat(num(sold.obj("entry").get("realizedPnl"))).isEqualByComparingTo("190.00");
        assertThat(num(sold.obj("entry").get("realizedPnlBrl"))).isEqualByComparingTo("2140.00");
        assertThat(sold.obj("entry").get("fxSource")).isEqualTo("USER");
        assertThat(num(sold.obj("position").get("averageCostBrl"))).as("o custo em reais da sobra não muda").isEqualByComparingTo("5000");
        assertThat(sell(t, id, Map.of("quantity", "1", "unitPrice", "1200", "fxRate", "0")).status()).isEqualTo(400);
        assertThat(sell(t, id, Map.of("quantity", "1", "unitPrice", "1200", "fxRate", "1E999999999")).status()).isEqualTo(400);
    }

    @Test void theSameIdempotencyKeyAppliesOnceAndReplaysTheSameResult() {
        String t = newUserToken();
        String id = idOf(open(t, typed("LCI Idempotente", "10", "100"), "abertura-1"));
        double before = counter("purchase_recorded");
        Map<String, String> headers = merge(bearer(t), Map.of("Idempotency-Key", "compra-2026-11-21-a"));
        Map<String, Object> body = Map.of("quantity", "10", "unitPrice", "110");
        Http.Reply first = http.post(path(id, "purchases"), headers, body);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.get("replayed")).isEqualTo(false);
        assertThat(first.header("Idempotent-Replay")).isNull();
        assertThat(counter("purchase_recorded")).as("a primeira aplicação publica o evento").isEqualTo(before + 1);

        Http.Reply second = http.post(path(id, "purchases"), headers, body);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.get("replayed")).isEqualTo(true);
        assertThat(second.header("Idempotent-Replay")).isEqualTo("true");
        assertThat(second.obj("entry").get("id")).as("a mesma entrada").isEqualTo(first.obj("entry").get("id"));
        assertThat(counter("purchase_recorded")).as("a repetição não publica de novo").isEqualTo(before + 1);
        assertThat(num(((Map<?, ?>) positions(t).get(0).get("position")).get("quantity"))).as("aplicada uma vez só").isEqualByComparingTo("20");
        assertThat(ledger(t, "")).hasSize(2);

        Http.Reply reused = http.post(path(id, "purchases"), headers, Map.of("quantity", "10", "unitPrice", "111"));
        assertThat(reused.status()).isEqualTo(422);
        assertThat(reused.get("code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(ledger(t, "")).as("o conteúdo diferente não foi aplicado").hasSize(2);
        assertThat(http.post(path(id, "purchases"), merge(bearer(t), Map.of("Idempotency-Key", "tem espaço")), body).status()).isEqualTo(400);
        assertThat(http.post(path(id, "purchases"), merge(bearer(t), Map.of("Idempotency-Key", "x".repeat(65))), body).status()).isEqualTo(400);
        assertThat(open(t, typed("LCI Idempotente", "10", "100"), "abertura-1").status()).as("repetir a abertura devolve a mesma operação").isEqualTo(200);
        assertThat(open(t, typed("LCI Idempotente", "99", "100"), "abertura-1").status()).as("a abertura com outro conteúdo é recusada").isEqualTo(422);
    }

    @Test void theSameKeyIsScopedToTheUser() {
        String a = newUserToken();
        String b = newUserToken();
        assertThat(open(a, typed("LCI A", "1", "100"), "chave-compartilhada").status()).isEqualTo(201);
        assertThat(open(b, typed("LCI A", "1", "100"), "chave-compartilhada").status()).as("a chave de A não é vista por B").isEqualTo(201);
        assertThat(positions(b)).hasSize(1);
    }

    @Test void parallelRequestsWithTheSameKeyApplyOnlyOnce() throws Exception {
        String t = newUserToken();
        String id = idOf(open(t, typed("LCI Corrida de chave", "1", "100")));
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        Set<Object> entryIds = java.util.Collections.synchronizedSet(new HashSet<>());
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    Http.Reply r = new Http(port).post(path(id, "purchases"), merge(bearer(t), Map.of("Idempotency-Key", "corrida-unica")), Map.of("quantity", "1", "unitPrice", "100"));
                    if (r.status() == 200) {
                        entryIds.add(r.obj("entry").get("id"));
                        if (Boolean.TRUE.equals(r.get("replayed"))) replayed.incrementAndGet();
                        else applied.incrementAndGet();
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
        }
        assertThat(applied.get()).as("uma só aplicação").isEqualTo(1);
        assertThat(entryIds).as("todas as respostas falam da mesma entrada").hasSize(1);
        assertThat(replayed.get()).isLessThanOrEqualTo(threads - 1);
        assertThat(ledger(t, "?assetKey=T:LCI:lci-corrida-de-chave")).hasSize(2);
        assertThat(num(((Map<?, ?>) positions(t).get(0).get("position")).get("quantity"))).isEqualByComparingTo("2");
    }

    @Test void theLedgerIsChronologicalPerAssetAndRefusesTheFuture() {
        String t = newUserToken();
        String today = brasilia().toString();
        String tomorrow = brasilia().plusDays(1).toString();
        Http.Reply opened = open(t, typedOn("LCI", "LCI Datas", "10", "100", "2026-03-10"));
        assertThat(opened.status()).isEqualTo(201);
        String id = idOf(opened);
        assertThat(opened.obj("entry").get("tradeDate")).isEqualTo("2026-03-10");
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", "2026-03-09")).status()).as("anterior à última operação do ativo").isEqualTo(422);
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", "2026-03-09")).get("code")).isEqualTo("OUT_OF_ORDER");
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", tomorrow)).status()).as("no futuro").isEqualTo(400);
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", "1999-12-31")).status()).isEqualTo(400);
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", "2026-03-10")).status()).as("no mesmo dia vale").isEqualTo(200);
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", today)).status()).isEqualTo(200);
        assertThat(ledger(t, "?from=2026-03-10&to=2026-03-10")).hasSize(2);
        assertThat(ledger(t, "?limit=2&offset=1")).hasSize(2);
        assertThat(http.get("/api/me/portfolio/transactions?limit=0", bearer(t)).status()).isEqualTo(400);
        assertThat(http.get("/api/me/portfolio/transactions?limit=201", bearer(t)).status()).isEqualTo(400);
        assertThat(http.get("/api/me/portfolio/transactions?from=2026-05-01&to=2026-04-01", bearer(t)).status()).isEqualTo(400);
        assertThat(http.get("/api/me/portfolio/transactions?from=lixo", bearer(t)).status()).isEqualTo(400);
    }

    @Test void adjustmentsAndWriteOffsAreRecordedAndTheHistorySurvivesTheClosing() {
        String t = newUserToken();
        String id = idOf(open(t, typed("LCI Ajuste", "10", "100")));
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "8", "averagePrice", "105", "expectedVersion", 0)).obj("entry").get("entryType")).isEqualTo("ADJUSTMENT");
        Http.Reply off = http.send("DELETE", "/api/me/portfolio/positions/" + id + "?expectedVersion=1", bearer(t), null);
        assertThat(off.status()).isEqualTo(200);
        assertThat(off.get("closed")).isEqualTo(true);
        assertThat(off.obj("entry").get("entryType")).isEqualTo("WRITE_OFF");
        assertThat(off.obj("entry").get("realizedPnl")).as("baixa não realiza resultado").isNull();
        assertThat(positions(t)).isEmpty();
        assertThat(ledger(t, "")).extracting(e -> e.get("entryType")).containsExactly("OPENING", "ADJUSTMENT", "WRITE_OFF");
        // reabrir o mesmo ativo depois da baixa é uma nova posição, e o livro continua dele
        assertThat(open(t, typed("LCI Ajuste", "3", "90")).status()).isEqualTo(201);
        assertThat(ledger(t, "?assetKey=T:LCI:lci-ajuste")).hasSize(4);
    }

    @Test void staleVersionsAreRejectedWith409AndReplacementNeedsTheVersion() {
        String t = newUserToken();
        String id = idOf(open(t, typed("LCI A", "10", "100")));
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "expectedVersion", 0)).status()).isEqualTo(200);
        Http.Reply stale = buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "expectedVersion", 0));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.get("code")).isEqualTo("VERSION_CONFLICT");
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "5", "averagePrice", "90")).status()).as("sem expectedVersion").isEqualTo(400);
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "5", "averagePrice", "90", "expectedVersion", 0)).status()).isEqualTo(409);
        Http.Reply replaced = http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "5", "averagePrice", "90", "expectedVersion", 1));
        assertThat(replaced.status()).isEqualTo(200);
        assertThat(num(replaced.obj("position").get("quantity"))).isEqualByComparingTo("5");
        assertThat(((Number) replaced.obj("position").get("version")).longValue()).isEqualTo(2);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id + "?expectedVersion=1", bearer(t), null).status()).isEqualTo(409);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id + "?expectedVersion=2", bearer(t), null).status()).isEqualTo(200);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id, bearer(t), null).status()).as("já não existe").isEqualTo(404);
        assertThat(ledger(t, "")).as("as tentativas recusadas não deixaram entrada").hasSize(4);
    }

    @Test void parallelPurchasesNeverLoseAnUpdate() throws Exception {
        String t = newUserToken();
        String id = idOf(open(t, typed("LCI Corrida", "1", "100")));
        int threads = 10;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    Http.Reply r = new Http(port).post(path(id, "purchases"), bearer(t), Map.of("quantity", "1", "unitPrice", "100"));
                    if (r.status() == 200) ok.incrementAndGet();
                    else if (r.status() == 409 && "VERSION_CONFLICT".equals(r.get("code"))) conflicts.incrementAndGet();
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
        }
        assertThat(ok.get()).isGreaterThanOrEqualTo(1);
        assertThat(ok.get() + conflicts.get()).as("toda requisição ou venceu ou recebeu conflito").isEqualTo(threads);
        @SuppressWarnings("unchecked")
        Map<String, Object> finalPosition = (Map<String, Object>) positions(t).get(0).get("position");
        assertThat(num(finalPosition.get("quantity"))).as("nenhuma compra aceita se perdeu").isEqualByComparingTo(String.valueOf(1 + ok.get()));
        assertThat(((Number) finalPosition.get("version")).longValue()).isEqualTo(ok.get());
        assertThat(num(finalPosition.get("averagePrice"))).isEqualByComparingTo("100");
        assertThat(ledger(t, "")).as("uma entrada por compra aceita, nenhuma por conflito").hasSize(1 + ok.get());
    }

    @Test void usersCannotSeeOrTouchEachOthersPositionsLedgerOrTaxReport() {
        String a = newUserToken();
        String b = newUserToken();
        String id = idOf(open(a, typedOn("ACAO", "Ação A", "10", "30", "2026-01-05")));
        assertThat(sell(a, id, Map.of("quantity", "5", "unitPrice", "40", "tradeDate", "2026-02-05")).status()).isEqualTo(200);
        assertThat(positions(b)).isEmpty();
        assertThat(ledger(b, "")).isEmpty();
        assertThat(ledger(b, "?assetKey=T:ACAO:acao-a")).isEmpty();
        Map<String, Object> bReport = http.get("/api/me/tax/report?year=2026", bearer(b)).json();
        assertThat((List<?>) bReport.get("holdings")).isEmpty();
        assertThat((List<?>) bReport.get("months")).isEmpty();
        assertThat(buy(b, id, Map.of("quantity", "1", "unitPrice", "1")).status()).isEqualTo(404);
        assertThat(sell(b, id, Map.of("quantity", "1", "unitPrice", "1")).status()).isEqualTo(404);
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(b), Map.of("quantity", "1", "averagePrice", "1", "expectedVersion", 0)).status()).isEqualTo(404);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id, bearer(b), null).status()).isEqualTo(404);
        assertThat(open(b, typedOn("ACAO", "Ação A", "1", "1", "2026-01-05")).status()).as("B pode ter o mesmo ativo").isEqualTo(201);
        assertThat(ledger(a, "")).hasSize(2);
    }

    @Test void theTaxReportListsHoldingsAtYearEndAndRealizedResultsWithTheLegalBasisAndNoInventedTax() {
        String t = newUserToken();
        String acao = idOf(open(t, typedOn("ACAO", "Ação Teste", "1000", "10", "2026-01-02")));
        // fevereiro: 1.200 em vendas (abaixo de R$ 20.000), resultado 200; março: 27.000 em vendas (acima), resultado 18.000
        assertThat(sell(t, acao, Map.of("quantity", "100", "unitPrice", "12", "tradeDate", "2026-02-10")).status()).isEqualTo(200);
        assertThat(sell(t, acao, Map.of("quantity", "900", "unitPrice", "30", "tradeDate", "2026-03-10")).get("closed")).isEqualTo(true);
        String lci = idOf(open(t, typedOn("LCI", "LCI Teste", "1", "10000", "2026-04-01")));
        // day trade em potencial: compra e venda do mesmo ativo no mesmo dia
        String fii = idOf(open(t, typedOn("FII", "FII Teste", "10", "100", "2026-05-02")));
        assertThat(buy(t, fii, Map.of("quantity", "10", "unitPrice", "110", "tradeDate", "2026-06-01")).status()).isEqualTo(200);
        assertThat(sell(t, fii, Map.of("quantity", "5", "unitPrice", "120", "tradeDate", "2026-06-01")).status()).isEqualTo(200);
        assertThat(lci).isNotBlank();

        Http.Reply rep = http.get("/api/me/tax/report?year=2026", bearer(t));
        assertThat(rep.status()).isEqualTo(200);
        Map<String, Object> r = rep.json();
        assertThat(r.get("year")).isEqualTo(2026);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> holdings = (List<Map<String, Object>>) r.get("holdings");
        assertThat(holdings).extracting(h -> h.get("label")).containsExactlyInAnyOrder("LCI Teste", "FII Teste");
        Map<String, Object> lciHolding = holdings.stream().filter(h -> "LCI Teste".equals(h.get("label"))).findFirst().orElseThrow();
        assertThat(num(lciHolding.get("totalCostBrl"))).isEqualByComparingTo("10000.00");
        Map<String, Object> fiiHolding = holdings.stream().filter(h -> "FII Teste".equals(h.get("label"))).findFirst().orElseThrow();
        assertThat(num(fiiHolding.get("quantity"))).isEqualByComparingTo("15");
        assertThat(num(fiiHolding.get("averageCostBrl"))).as("(1.000 + 1.100) / 20 mantido na venda").isEqualByComparingTo("105");
        assertThat(num(fiiHolding.get("totalCostBrl"))).isEqualByComparingTo("1575.00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> months = (List<Map<String, Object>>) r.get("months");
        Map<String, Object> feb = months.stream().filter(m -> ((Number) m.get("month")).intValue() == 2 && "ACOES".equals(m.get("group"))).findFirst().orElseThrow();
        assertThat(num(feb.get("salesGrossBrl"))).isEqualByComparingTo("1200.00");
        assertThat(num(feb.get("realizedResultBrl"))).isEqualByComparingTo("200.00");
        assertThat(feb.get("withinStockExemptionLimit")).isEqualTo(true);
        Map<String, Object> mar = months.stream().filter(m -> ((Number) m.get("month")).intValue() == 3 && "ACOES".equals(m.get("group"))).findFirst().orElseThrow();
        assertThat(num(mar.get("salesGrossBrl"))).isEqualByComparingTo("27000.00");
        assertThat(num(mar.get("realizedResultBrl"))).isEqualByComparingTo("18000.00");
        assertThat(mar.get("withinStockExemptionLimit")).as("27.000 passa de 20.000").isEqualTo(false);
        Map<String, Object> jun = months.stream().filter(m -> ((Number) m.get("month")).intValue() == 6 && "FII".equals(m.get("group"))).findFirst().orElseThrow();
        assertThat(((Number) jun.get("possibleDayTrades")).intValue()).isEqualTo(1);
        assertThat(jun.get("withinStockExemptionLimit")).as("o limite só vale para ações").isNull();
        assertThat(num(r.get("stockMonthlySalesLimitBrl"))).isEqualByComparingTo("20000.00");

        assertThat(rep.body()).as("nenhum imposto devido é calculado").doesNotContain("taxDue").doesNotContain("imposto devido:").doesNotContain("darf\":");
        assertThat(rep.body()).contains("Lei nº 11.033/2004, art. 3º, inciso I").contains("art. 2º, incisos I e II").contains("art. 3º, inciso III");
        assertThat(rep.body()).contains("Não calcula imposto devido").contains("não substitui um contador");
        @SuppressWarnings("unchecked")
        List<String> notComputed = (List<String>) r.get("notComputed");
        assertThat(notComputed.toString()).contains("Compensação de prejuízos").contains("Day trade").contains("DARF");
        assertThat(http.get("/api/me/tax/report?year=1999", bearer(t)).status()).isEqualTo(400);
        assertThat(http.get("/api/me/tax/report?year=" + (brasilia().getYear() + 1), bearer(t)).status()).isEqualTo(400);
        assertThat(http.get("/api/me/tax/report?year=abc", bearer(t)).status()).isEqualTo(400);
        assertThat(http.get("/api/me/tax/report", bearer(t)).status()).as("sem ano: o corrente").isEqualTo(200);
        assertThat(http.get("/api/me/tax/report?year=2026").status()).isEqualTo(401);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> y2025 = (List<Map<String, Object>>) http.get("/api/me/tax/report?year=2025", bearer(t)).json().get("holdings");
        assertThat(y2025).as("antes da primeira operação não havia nada em 31/12").isEmpty();
    }

    @Autowired
    dev.meridianwealth.infrastructure.provider.HttpGateway gateway;

    long ptaxCalls() {
        return ((dev.meridianwealth.testsupport.FixtureHttpGateway) gateway).requested().stream().filter(u -> u.contains("CotacaoDolarPeriodo")).count();
    }

    @Test void invalidDollarOperationsAreRefusedBeforeAnyExchangeRateLookupAndKeepTheirOwnError() {
        String t = newUserToken();
        String id = idOf(open(t, Map.of("type", "CRIPTO_ATIVO", "label", "Cripto validação", "currency", "USD", "quantity", "10", "averagePrice", "100", "fxRate", "5.00")));
        long before = ptaxCalls();
        Http.Reply tooMuch = sell(t, id, Map.of("quantity", "9999", "unitPrice", "100", "tradeDate", brasilia().toString()));
        assertThat(tooMuch.status()).as("erro do usuário, não 503 de câmbio").isEqualTo(400);
        assertThat((String) tooMuch.get("error")).contains("passa da quantidade");
        assertThat(buy(t, id, Map.of("quantity", "-1", "unitPrice", "100")).status()).isEqualTo(400);
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "fees", "-1")).status()).isEqualTo(400);
        assertThat(sell(t, id, Map.of("quantity", "1", "unitPrice", "1", "fees", "50")).status()).isEqualTo(400);
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "0", "averagePrice", "1", "expectedVersion", 0)).status()).isEqualTo(400);
        assertThat(open(t, Map.of("type", "CRIPTO_ATIVO", "label", "Cripto inválida", "currency", "USD", "quantity", "0", "averagePrice", "1")).status()).isEqualTo(400);
        assertThat(ptaxCalls()).as("nenhum pedido inválido gastou uma consulta ao Banco Central").isEqualTo(before);
    }

    @Test void importingManyDollarTradesOnTheSameDaysConsultsTheCentralBankOncePerDate() {
        String t = newUserToken();
        String yesterday = brasilia().minusDays(1).toString();
        String id = idOf(open(t, Map.of("type", "CRIPTO_ATIVO", "label", "Cripto importação", "currency", "USD", "quantity", "1", "averagePrice", "100", "tradeDate", yesterday)));
        long before = ptaxCalls();
        for (int i = 0; i < 14; i++) {
            Http.Reply r = buy(t, id, Map.of("quantity", "1", "unitPrice", "100", "tradeDate", yesterday));
            assertThat(r.status()).as("compra %d", i + 1).isEqualTo(200);
            assertThat(r.obj("entry").get("fxSource")).isEqualTo("PTAX");
        }
        assertThat(ptaxCalls() - before).as("14 compras na mesma data: nenhuma consulta nova (já estava em cache pela abertura)").isZero();
    }

    @Test void invalidInputIsRefusedWithClearErrors() {
        String t = newUserToken();
        assertThat(open(t, Map.of("symbol", "NAOEXISTE", "quantity", "1", "averagePrice", "1")).status()).isEqualTo(400);
        assertThat(open(t, Map.of("symbol", "IBOV", "quantity", "1", "averagePrice", "1")).status()).as("índice é só referência").isEqualTo(400);
        Http.Reply eur = open(t, Map.of("symbol", "SAP", "quantity", "1", "averagePrice", "1"));
        assertThat(eur.status()).isEqualTo(400);
        assertThat((String) eur.get("error")).contains("EUR").contains("BRL e USD");
        assertThat(open(t, Map.of("symbol", "PETR4", "currency", "USD", "quantity", "1", "averagePrice", "1")).status()).as("PETR4 é cotada em BRL").isEqualTo(400);
        assertThat(open(t, Map.of("type", "INDICE", "quantity", "1", "averagePrice", "1")).status()).isEqualTo(400);
        assertThat(open(t, Map.of("quantity", "1", "averagePrice", "1")).status()).as("sem símbolo nem tipo").isEqualTo(400);
        assertThat(open(t, Map.of("type", "LCI", "averagePrice", "1")).status()).as("sem quantidade").isEqualTo(400);
        assertThat(open(t, typed("LCI Z", "0", "10")).status()).isEqualTo(400);
        assertThat(open(t, typed("LCI Z", "-1", "10")).status()).isEqualTo(400);
        assertThat(open(t, typed("LCI Z", "1", "10.0000001")).status()).as("7 casas no preço").isEqualTo(400);
        assertThat(http.post("/api/me/portfolio/positions", bearer(t), "{\"type\":\"LCI\",\"quantity\":1E999999999,\"averagePrice\":10}").status()).as("notação científica").isEqualTo(400);
        assertThat(http.post("/api/me/portfolio/positions", bearer(t), "{\"type\":\"NAO_EXISTE\",\"quantity\":1,\"averagePrice\":10}").status()).isEqualTo(400);
        assertThat(open(t, typed("LCI Dup", "1", "10")).status()).isEqualTo(201);
        Http.Reply dup = open(t, typed("lci  dup", "1", "10"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.get("code")).isEqualTo("POSITION_EXISTS");
        assertThat(http.post("/api/me/portfolio/positions/nao-e-uuid/purchases", bearer(t), Map.of("quantity", "1", "unitPrice", "1")).status()).isEqualTo(400);
        assertThat(buy(t, UUID.randomUUID().toString(), Map.of("quantity", "1", "unitPrice", "1", "fees", "-1")).status()).isEqualTo(404);
        String id = idOf(open(t, typed("LCI Taxas", "1", "10")));
        assertThat(buy(t, id, Map.of("quantity", "1", "unitPrice", "1", "fees", "-1")).status()).isEqualTo(400);
        assertThat(sell(t, id, Map.of("quantity", "1", "unitPrice", "1", "fees", "5")).status()).as("taxas maiores que a venda").isEqualTo(400);
        assertThat(http.get("/api/me/portfolio").status()).as("sem token").isEqualTo(401);
        assertThat(http.get("/api/me/portfolio/transactions").status()).isEqualTo(401);
        assertThat(http.post("/api/me/portfolio/positions", null, typed("x", "1", "1")).status()).isEqualTo(401);
    }
}
