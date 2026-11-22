package dev.meridianwealth.infrastructure.bcb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.FxHistoryPort.PtaxQuote;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import dev.meridianwealth.testsupport.MutableClock;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** O PTAX por data: cache só do que não muda mais, sem cachear hoje, o futuro nem as falhas, e sem aceitar cotação de data posterior à pedida. */
class BcbFxHistoryTest {
    private static final String ANSWER = "{\"value\":[{\"cotacaoCompra\":5.88830,\"cotacaoVenda\":5.88890,\"dataHoraCotacao\":\"2026-11-19 13:04:18.325373\"}]}";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-21T15:00:00Z"));
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean failing;

    private BcbFxHistory history() {
        BcbClient client = new BcbClient((url, headers) -> {
            calls.incrementAndGet();
            if (failing) throw new IOException("BCB fora do ar");
            return ANSWER;
        }, ResilientExecutor.withDefaults("BCB-teste", 1000, 1000));
        return new BcbFxHistory(client, clock);
    }

    @Test void aPastDateIsFetchedOnceAndThenServedFromTheCache() {
        BcbFxHistory h = history();
        PtaxQuote first = h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20")).orElseThrow();
        assertThat(first.brlPerUsd()).isEqualByComparingTo("5.8889");
        assertThat(first.referenceDate()).as("o dia útil da cotação, não a data pedida").isEqualTo(LocalDate.parse("2026-11-19"));
        for (int i = 0; i < 20; i++) assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20"))).contains(first);
        assertThat(calls.get()).as("uma importação com 21 operações no mesmo dia consulta o Banco Central uma vez").isEqualTo(1);
        h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-19"));
        assertThat(calls.get()).as("outra data, outra consulta").isEqualTo(2);
    }

    @Test void todayAndTheFutureAreNeverCached() {
        BcbFxHistory h = history();
        LocalDate today = LocalDate.parse("2026-11-21");
        h.ptaxSellOnOrBefore(today);
        h.ptaxSellOnOrBefore(today);
        h.ptaxSellOnOrBefore(today.plusDays(2));
        h.ptaxSellOnOrBefore(today.plusDays(2));
        assertThat(calls.get()).as("a cotação do dia sai no meio da tarde: hoje e o futuro sempre consultam").isEqualTo(4);
    }

    @Test void aFailureIsNotCachedAndReturnsNoDataInsteadOfAGuess() {
        BcbFxHistory h = history();
        failing = true;
        assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20"))).isEmpty();
        int afterFailure = calls.get();
        assertThat(afterFailure).as("o executor tentou (com reintentos)").isGreaterThanOrEqualTo(1);
        failing = false;
        assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20"))).as("a próxima tentativa consulta de novo").isPresent();
        assertThat(calls.get()).isGreaterThan(afterFailure);
        int afterSuccess = calls.get();
        assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20"))).isPresent();
        assertThat(calls.get()).as("agora sim, em cache").isEqualTo(afterSuccess);
    }

    @Test void aQuoteDatedAfterTheRequestedDateIsRefusedAndNotCached() {
        BcbFxHistory h = history();
        assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-10"))).as("a resposta é de 19/11, depois de 10/11: nunca vale para uma data anterior").isEmpty();
        assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-10"))).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void theCacheIsBoundedAndKeepsWorkingWhenItFills() {
        BcbFxHistory h = history();
        for (int i = 0; i < BcbFxHistory.MAX_CACHED + 5; i++) h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20").minusDays(0));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(h.ptaxSellOnOrBefore(LocalDate.parse("2026-11-20"))).isPresent();
    }
}
