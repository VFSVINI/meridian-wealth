package dev.meridianwealth.application;

import dev.meridianwealth.application.port.TickSink;
import java.lang.System.Logger.Level;
import java.util.List;

/** Caso de uso do stream: avança o estado de mercado e entrega o lote a todos os canais. */
public class StreamService {
    private static final System.Logger LOG = System.getLogger(StreamService.class.getName());

    public record Channel(String name, int clients) {}

    private final MarketService market;
    private final List<TickSink> sinks;

    public StreamService(MarketService market, List<TickSink> sinks) {
        this.market = market;
        this.sinks = List.copyOf(sinks);
    }

    /** Publica o próximo lote; a falha de um canal não impede a entrega nos demais. */
    public TickBatch publishNext() {
        TickBatch batch = market.advance();
        for (TickSink sink : sinks) {
            try {
                sink.publish(batch);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Canal {0} falhou ao publicar: {1}", sink.channel(), e.getMessage());
            }
        }
        return batch;
    }

    public List<Channel> channels() {
        return sinks.stream().map(s -> new Channel(s.channel(), s.clients())).toList();
    }
}
