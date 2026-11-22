package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.portfolio.AssetRef;
import dev.meridianwealth.domain.portfolio.Currency;
import dev.meridianwealth.domain.portfolio.Position;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Contrato do {@link PositionStore}: a versão em memória e a JDBC (PostgreSQL de verdade) precisam se comportar igual. */
abstract class PositionStoreContract {
    private static final Instant T0 = Instant.parse("2026-11-21T12:00:00Z");

    abstract PositionStore store();

    /** Cria um usuário (a versão JDBC precisa dele por causa da chave estrangeira). */
    abstract UUID newUser();

    static BigDecimal d(String v) { return new BigDecimal(v); }

    static Position position(UUID user, String symbol, String qty, String price, Instant created) {
        return Position.open(user, new AssetRef(symbol, AssetType.ACAO, symbol + " teste"), d(qty), d(price), Currency.BRL, created);
    }

    @Test void insertIsUniquePerUserAndAssetButNotAcrossUsers() {
        UUID a = newUser();
        UUID b = newUser();
        assertThat(store().insertIfAbsent(position(a, "PETR4", "10", "30", T0))).isTrue();
        assertThat(store().insertIfAbsent(position(a, "PETR4", "99", "99", T0))).as("mesmo ativo, mesmo usuário").isFalse();
        assertThat(store().insertIfAbsent(position(b, "PETR4", "5", "31", T0))).as("outro usuário pode ter o mesmo ativo").isTrue();
        assertThat(store().countByUser(a)).isEqualTo(1);
        assertThat(store().findByUser(a).get(0).quantity()).isEqualByComparingTo("10");
        Position typed = Position.open(a, new AssetRef(null, AssetType.LCI, "LCI Banco A"), d("1"), d("10000"), Currency.BRL, T0);
        assertThat(store().insertIfAbsent(typed)).isTrue();
        assertThat(store().insertIfAbsent(Position.open(a, new AssetRef(null, AssetType.LCI, "lci  banco a"), d("1"), d("5"), Currency.BRL, T0))).isFalse();
        assertThat(store().insertIfAbsent(Position.open(a, new AssetRef(null, AssetType.LCI, "LCI Banco B"), d("1"), d("5"), Currency.BRL, T0))).isTrue();
        assertThat(store().countByUser(a)).isEqualTo(3);
    }

    @Test void everyReadAndWriteIsScopedToTheOwner() {
        UUID a = newUser();
        UUID b = newUser();
        Position pa = position(a, "VALE3", "10", "60", T0);
        store().insertIfAbsent(pa);
        assertThat(store().find(pa.id(), a)).isPresent();
        assertThat(store().find(pa.id(), b)).as("B não encontra a posição de A").isEmpty();
        assertThat(store().findByUser(b)).isEmpty();
        assertThat(store().update(pa.declare(d("1"), d("1"), T0), 0)).isPresent();
        Position asB = new Position(pa.id(), b, pa.asset(), d("77"), d("77"), Currency.BRL, T0, T0, 1);
        assertThat(store().update(asB, 1)).as("atualizar com o id de A e o dono B não altera nada").isEmpty();
        assertThat(store().delete(pa.id(), b, 1)).as("B não apaga a posição de A").isFalse();
        assertThat(store().find(pa.id(), a).orElseThrow().quantity()).isEqualByComparingTo("1");
    }

    @Test void updateIsOptimisticAndBumpsTheVersion() {
        UUID u = newUser();
        Position p = position(u, "ITUB4", "100", "30", T0);
        store().insertIfAbsent(p);
        assertThat(store().find(p.id(), u).orElseThrow().version()).isZero();
        Position first = store().update(p.purchase(d("50"), d("33"), T0.plusSeconds(60)), 0).orElseThrow();
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.quantity()).isEqualByComparingTo("150");
        assertThat(first.averagePrice()).isEqualByComparingTo("31.000000");
        assertThat(store().update(p.purchase(d("1"), d("1"), T0), 0)).as("versão antiga").isEmpty();
        Position stored = store().find(p.id(), u).orElseThrow();
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.updatedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(store().update(stored.declare(d("7"), d("8"), T0), 1).orElseThrow().version()).isEqualTo(2);
        assertThat(store().update(position(u, "X", "1", "1", T0), 0)).as("posição que não existe").isEmpty();
    }

    @Test void deleteIsConditionalOnTheVersion() {
        UUID u = newUser();
        Position p = position(u, "ITUB4", "100", "30", T0);
        store().insertIfAbsent(p);
        assertThat(store().delete(p.id(), u, 5)).isFalse();
        assertThat(store().find(p.id(), u)).isPresent();
        assertThat(store().delete(p.id(), u, 0)).isTrue();
        assertThat(store().find(p.id(), u)).isEmpty();
        assertThat(store().delete(p.id(), u, 0)).isFalse();
    }

    @Test void concurrentWritesOnTheSameVersionLetExactlyOneThrough() throws Exception {
        UUID u = newUser();
        for (int round = 0; round < 15; round++) {
            Position p = position(u, "R" + round, "100", "10", T0);
            store().insertIfAbsent(p);
            int threads = 8;
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger wins = new AtomicInteger();
            List<Position> winners = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    int n = i;
                    fs.add(pool.submit(() -> {
                        go.await();
                        Optional<Position> r = store().update(p.declare(d(String.valueOf(200 + n)), d("10"), T0), 0);
                        if (r.isPresent()) {
                            wins.incrementAndGet();
                            synchronized (winners) { winners.add(r.get()); }
                        }
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : fs) f.get();
            }
            assertThat(wins.get()).as("rodada %d: uma única escrita vence", round).isEqualTo(1);
            assertThat(store().find(p.id(), u).orElseThrow().quantity()).isEqualByComparingTo(winners.get(0).quantity());
            assertThat(store().find(p.id(), u).orElseThrow().version()).isEqualTo(1);
        }
    }

    @Test void concurrentInsertsOfTheSameAssetCreateOnePosition() throws Exception {
        UUID u = newUser();
        AtomicInteger created = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    if (store().insertIfAbsent(position(u, "CORRIDA", "1", "1", T0))) created.incrementAndGet();
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
        }
        assertThat(created.get()).isEqualTo(1);
        assertThat(store().countByUser(u)).isEqualTo(1);
    }

    @Test void listingIsOrderedByCreationAndNumbersRoundTripWithoutLoss() {
        UUID u = newUser();
        store().insertIfAbsent(position(u, "B", "1", "1", T0.plusSeconds(10)));
        store().insertIfAbsent(position(u, "A", "1", "1", T0));
        store().insertIfAbsent(position(u, "C", "0.00000001", "0.000001", T0.plusSeconds(20)));
        store().insertIfAbsent(position(u, "D", "999999999999999", "123456789.123456", T0.plusSeconds(30)));
        List<Position> all = store().findByUser(u);
        assertThat(all).extracting(p -> p.asset().symbol()).containsExactly("A", "B", "C", "D");
        assertThat(all.get(2).quantity()).isEqualByComparingTo("0.00000001");
        assertThat(all.get(2).averagePrice()).isEqualByComparingTo("0.000001");
        assertThat(all.get(3).quantity()).isEqualByComparingTo("999999999999999");
        assertThat(all.get(3).averagePrice()).isEqualByComparingTo("123456789.123456");
        assertThat(all.get(0).asset().type()).isEqualTo(AssetType.ACAO);
        assertThat(all.get(0).currency()).isEqualTo(Currency.BRL);
        assertThat(all.get(0).createdAt()).isEqualTo(T0);
    }
}
