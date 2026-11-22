package dev.meridianwealth.web;

import static dev.meridianwealth.testsupport.Http.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.Http;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * Cenários da carteira pela borda HTTP, com cotações e PTAX reais gravados (fixtures). Rodam duas vezes: com a persistência em memória
 * (sem Docker) e com o PostgreSQL de verdade, para provar que o comportamento é o mesmo.
 */
abstract class PortfolioApiScenarios {
    static final String PASSWORD = "uma frase longa e boa";

    @Value("${local.server.port}")
    int port;

    Http http;

    @org.springframework.beans.factory.annotation.Autowired
    dev.meridianwealth.infrastructure.bcb.LiveBrazilRates brazilRates;

    @BeforeEach
    void client() { http = new Http(port); }

    String newUserToken() {
        String email = "carteira-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
        Http.Reply r = http.post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Carteira"));
        assertThat(r.status()).isEqualTo(201);
        return (String) r.get("accessToken");
    }

    Http.Reply open(String token, Map<String, Object> body) { return http.post("/api/me/portfolio/positions", bearer(token), body); }

    Map<String, Object> typed(String label, String qty, String price) { return Map.of("type", "LCI", "label", label, "quantity", qty, "averagePrice", price); }

    static BigDecimal num(Object o) { return new BigDecimal(String.valueOf(o)); }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> positions(String token) { return (List<Map<String, Object>>) http.get("/api/me/portfolio", bearer(token)).json().get("positions"); }

    @Test void theWeightedAveragePriceFollowsPurchasesAndSalesKeepTheAverage() {
        String t = newUserToken();
        Http.Reply opened = open(t, typed("LCI Banco Teste 2028", "10", "1000"));
        assertThat(opened.status()).isEqualTo(201);
        assertThat(opened.get("assetKey")).isEqualTo("T:LCI:lci-banco-teste-2028");
        assertThat(opened.get("currency")).isEqualTo("BRL");
        assertThat(((Number) opened.get("version")).longValue()).isZero();
        String id = (String) opened.get("id");

        Http.Reply bought = http.post("/api/me/portfolio/positions/" + id + "/purchases", bearer(t), Map.of("quantity", "10", "unitPrice", "1100"));
        assertThat(bought.status()).isEqualTo(200);
        assertThat(num(bought.get("averagePrice"))).isEqualByComparingTo("1050");
        assertThat(num(bought.get("quantity"))).isEqualByComparingTo("20");
        assertThat(((Number) bought.get("version")).longValue()).isEqualTo(1);
        assertThat(num(bought.get("costBasis"))).isEqualByComparingTo("21000.00");

        Http.Reply sold = http.post("/api/me/portfolio/positions/" + id + "/sales", bearer(t), Map.of("quantity", "5"));
        assertThat(sold.get("closed")).isEqualTo(false);
        Map<String, Object> after = sold.obj("position");
        assertThat(num(after.get("quantity"))).isEqualByComparingTo("15");
        assertThat(num(after.get("averagePrice"))).as("a venda mantém o preço médio").isEqualByComparingTo("1050");

        assertThat(http.post("/api/me/portfolio/positions/" + id + "/sales", bearer(t), Map.of("quantity", "15.5")).status()).as("vender mais do que tem").isEqualTo(400);
        Http.Reply closed = http.post("/api/me/portfolio/positions/" + id + "/sales", bearer(t), Map.of("quantity", "15"));
        assertThat(closed.get("closed")).isEqualTo(true);
        assertThat(positions(t)).isEmpty();
    }

    @Test void positionsAreValuedWithRealQuotesAndTheOfficialPtax() {
        brazilRates.refresh();
        String t = newUserToken();
        assertThat(open(t, Map.of("symbol", "petr4", "quantity", "100", "averagePrice", "30")).status()).isEqualTo(201);
        assertThat(open(t, Map.of("symbol", "SPY", "quantity", "10", "averagePrice", "500")).status()).isEqualTo(201);
        assertThat(open(t, Map.of("type", "CRIPTO_ATIVO", "label", "Bitcoin guardado", "currency", "USD", "quantity", "0.5", "averagePrice", "60000")).status()).isEqualTo(201);

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
        assertThat(rate.signum()).isPositive();

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

        Map<String, Object> spy = lines.stream().filter(l -> "SPY".equals(((Map<?, ?>) l.get("position")).get("assetKey"))).findFirst().orElseThrow();
        assertThat(spy.get("basis")).isEqualTo("MARKET");
        BigDecimal spyMarket = num(spy.get("marketValue"));
        assertThat(num(spy.get("currentValueBrl"))).as("valor em dólar x PTAX").isEqualByComparingTo(spyMarket.multiply(rate).setScale(2, RoundingMode.HALF_EVEN));
        assertThat(num(spy.get("costBasisBrl"))).isEqualByComparingTo(new BigDecimal("5000.00").multiply(rate).setScale(2, RoundingMode.HALF_EVEN));

        Map<String, Object> btc = lines.stream().filter(l -> ((String) ((Map<?, ?>) l.get("position")).get("assetKey")).startsWith("T:CRIPTO")).findFirst().orElseThrow();
        assertThat(btc.get("basis")).as("sem cotação: avaliada pelo custo").isEqualTo("COST");
        assertThat(btc.get("price")).isNull();
        assertThat(btc.get("marketValue")).isNull();
        assertThat(btc.get("unrealizedPnl")).as("não informado, nunca estimado").isNull();
        assertThat(num(btc.get("currentValueBrl"))).isEqualByComparingTo(new BigDecimal("30000.00").multiply(rate).setScale(2, RoundingMode.HALF_EVEN));

        BigDecimal total = num(petr.get("currentValueBrl")).add(num(spy.get("currentValueBrl"))).add(num(btc.get("currentValueBrl")));
        assertThat(num(r.get("totalCurrentValueBrl"))).isEqualByComparingTo(total);
        assertThat(((Number) r.get("positionsWithoutBrlValue")).intValue()).isZero();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byClass = (List<Map<String, Object>>) r.get("byClass");
        assertThat(byClass.stream().map(c -> num(c.get("percent"))).reduce(BigDecimal.ZERO, BigDecimal::add).subtract(new BigDecimal("100")).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
        assertThat((String) r.get("disclaimer")).contains("não executa ordens").contains("não é recomendação");
        assertThat(((List<?>) r.get("notes")).toString()).contains("custo").contains("câmbio de hoje");
    }

    @Test void staleVersionsAreRejectedWith409AndReplacementNeedsTheVersion() {
        String t = newUserToken();
        Map<String, Object> p = open(t, typed("LCI A", "10", "100")).json();
        String id = (String) p.get("id");
        assertThat(http.post("/api/me/portfolio/positions/" + id + "/purchases", bearer(t), Map.of("quantity", "1", "unitPrice", "100", "expectedVersion", 0)).status()).isEqualTo(200);
        Http.Reply stale = http.post("/api/me/portfolio/positions/" + id + "/purchases", bearer(t), Map.of("quantity", "1", "unitPrice", "100", "expectedVersion", 0));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.get("code")).isEqualTo("VERSION_CONFLICT");
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "5", "averagePrice", "90")).status()).as("sem expectedVersion").isEqualTo(400);
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "5", "averagePrice", "90", "expectedVersion", 0)).status()).isEqualTo(409);
        Http.Reply replaced = http.put("/api/me/portfolio/positions/" + id, bearer(t), Map.of("quantity", "5", "averagePrice", "90", "expectedVersion", 1));
        assertThat(replaced.status()).isEqualTo(200);
        assertThat(num(replaced.get("quantity"))).isEqualByComparingTo("5");
        assertThat(((Number) replaced.get("version")).longValue()).isEqualTo(2);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id + "?expectedVersion=1", bearer(t), null).status()).isEqualTo(409);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id + "?expectedVersion=2", bearer(t), null).status()).isEqualTo(204);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id, bearer(t), null).status()).as("já não existe").isEqualTo(404);
    }

    @Test void parallelPurchasesNeverLoseAnUpdate() throws Exception {
        String t = newUserToken();
        String id = (String) open(t, typed("LCI Corrida", "1", "100")).get("id");
        int threads = 10;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    Http.Reply r = new Http(port).post("/api/me/portfolio/positions/" + id + "/purchases", bearer(t), Map.of("quantity", "1", "unitPrice", "100"));
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
        Map<String, Object> finalPosition = positions(t).stream().map(l -> (Map<String, Object>) l.get("position")).findFirst().orElseThrow();
        assertThat(num(finalPosition.get("quantity"))).as("nenhuma compra aceita se perdeu").isEqualByComparingTo(String.valueOf(1 + ok.get()));
        assertThat(((Number) finalPosition.get("version")).longValue()).isEqualTo(ok.get());
        assertThat(num(finalPosition.get("averagePrice"))).isEqualByComparingTo("100");
    }

    @Test void usersCannotSeeOrTouchEachOthersPositions() {
        String a = newUserToken();
        String b = newUserToken();
        String id = (String) open(a, Map.of("symbol", "PETR4", "quantity", "10", "averagePrice", "30")).get("id");
        assertThat(positions(b)).isEmpty();
        assertThat(http.post("/api/me/portfolio/positions/" + id + "/purchases", bearer(b), Map.of("quantity", "1", "unitPrice", "1")).status()).isEqualTo(404);
        assertThat(http.post("/api/me/portfolio/positions/" + id + "/sales", bearer(b), Map.of("quantity", "1")).status()).isEqualTo(404);
        assertThat(http.put("/api/me/portfolio/positions/" + id, bearer(b), Map.of("quantity", "1", "averagePrice", "1", "expectedVersion", 0)).status()).isEqualTo(404);
        assertThat(http.send("DELETE", "/api/me/portfolio/positions/" + id, bearer(b), null).status()).isEqualTo(404);
        assertThat(open(b, Map.of("symbol", "PETR4", "quantity", "1", "averagePrice", "1")).status()).as("B pode ter o mesmo ativo").isEqualTo(201);
        Map<String, Object> mine = positions(a).get(0);
        assertThat(num(((Map<?, ?>) mine.get("position")).get("quantity"))).isEqualByComparingTo("10");
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
        assertThat(http.get("/api/me/portfolio").status()).as("sem token").isEqualTo(401);
        assertThat(http.post("/api/me/portfolio/positions", null, typed("x", "1", "1")).status()).isEqualTo(401);
    }
}
