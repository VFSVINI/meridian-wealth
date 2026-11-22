package dev.meridianwealth.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.domain.portfolio.PortfolioEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventBusTest {
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-11-21");

    private static PortfolioEvent purchase() { return new PortfolioEvent.PurchaseRecorded(USER, UUID.randomUUID(), "PETR4", NOW, DAY, new BigDecimal("10"), new BigDecimal("30")); }

    @Test void everySubscriberReceivesTheEventInOrderOfSubscription() {
        InProcessEventBus bus = new InProcessEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(e -> seen.add("a:" + e.type()));
        bus.subscribe(e -> seen.add("b:" + e.type()));
        bus.publish(purchase());
        assertThat(seen).containsExactly("a:purchase_recorded", "b:purchase_recorded");
    }

    @Test void aFailingSubscriberNeitherBlocksTheOthersNorBreaksThePublisher() {
        InProcessEventBus bus = new InProcessEventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(e -> { throw new IllegalStateException("assinante quebrado"); });
        bus.subscribe(e -> seen.add(e.type()));
        bus.publish(purchase());
        assertThat(seen).as("o segundo assinante recebeu mesmo depois da falha do primeiro").containsExactly("purchase_recorded");
    }

    @Test void metricsCountEventsByTypeWithoutUserIdentifiersInTheLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InProcessEventBus bus = new InProcessEventBus();
        bus.subscribe(new PortfolioEventMetrics(registry));
        bus.publish(purchase());
        bus.publish(purchase());
        bus.publish(new PortfolioEvent.SaleRecorded(USER, UUID.randomUUID(), "PETR4", NOW, DAY, new BigDecimal("5"), new BigDecimal("50.00"), null, false));
        bus.publish(new PortfolioEvent.PositionOpened(USER, UUID.randomUUID(), "PETR4", NOW, DAY));
        bus.publish(new PortfolioEvent.PositionAdjusted(USER, UUID.randomUUID(), "PETR4", NOW, DAY));
        bus.publish(new PortfolioEvent.PositionClosed(USER, UUID.randomUUID(), "PETR4", NOW, DAY));
        assertThat(registry.counter("meridianwealth.portfolio.events", "type", "purchase_recorded").count()).isEqualTo(2.0);
        for (String type : new String[] {"sale_recorded", "position_opened", "position_adjusted", "position_closed"}) {
            assertThat(registry.counter("meridianwealth.portfolio.events", "type", type).count()).as(type).isEqualTo(1.0);
        }
        registry.getMeters().forEach(m -> m.getId().getTags().forEach(t -> assertThat(t.getValue()).doesNotContain(USER.toString()).doesNotContain("PETR4")));
    }
}
