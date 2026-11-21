package dev.meridianwealth.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.application.port.TickSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StreamServiceTest {
    static final class RecordingSink implements TickSink {
        final String name;
        final boolean broken;
        final List<TickBatch> received = new ArrayList<>();

        RecordingSink(String name, boolean broken) {
            this.name = name;
            this.broken = broken;
        }

        @Override public String channel() { return name; }

        @Override public void publish(TickBatch batch) {
            if (broken) throw new IllegalStateException("canal quebrado");
            received.add(batch);
        }

        @Override public int clients() { return received.size(); }
    }

    @Test void aBrokenChannelDoesNotStopTheOthers() {
        MarketService market = new MarketService(new MarketServiceTest.InMemoryMarketData(), new SignalStoreStub(),
                new MarketServiceTest.CountingCache(), symbol -> Optional.empty(), new BacktestService());
        RecordingSink broken = new RecordingSink("WebSocket", true);
        RecordingSink ok = new RecordingSink("SSE", false);
        StreamService stream = new StreamService(market, List.of(broken, ok));

        TickBatch b = stream.publishNext();

        assertThat(ok.received).containsExactly(b);
        assertThat(stream.channels()).extracting(StreamService.Channel::name).containsExactly("WebSocket", "SSE");
        assertThat(stream.channels()).extracting(StreamService.Channel::clients).containsExactly(0, 1);
    }

    static final class SignalStoreStub implements dev.meridianwealth.application.port.SignalStore {
        @Override public void save(SignalRecord record) { }

        @Override public List<SignalRecord> recent(String symbol, int limit) { return List.of(); }
    }
}
