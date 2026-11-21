package dev.meridianwealth.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.TickBatch;
import dev.meridianwealth.application.port.TickSink;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Canal SSE do stream ({@code /api/stream}), usado pelo painel quando o WebSocket não conecta. */
@Component
public class SseTickSink implements TickSink {
    private static final Logger log = LoggerFactory.getLogger(SseTickSink.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper;

    public SseTickSink(ObjectMapper mapper) { this.mapper = mapper; }

    public SseEmitter subscribe() {
        SseEmitter e = new SseEmitter(0L);
        emitters.add(e);
        Runnable remove = () -> emitters.remove(e);
        e.onCompletion(remove);
        e.onTimeout(remove);
        e.onError(t -> remove.run());
        return e;
    }

    @Override
    public String channel() { return "SSE"; }

    @Override
    public void publish(TickBatch batch) {
        if (emitters.isEmpty()) return;
        String json;
        try {
            json = mapper.writeValueAsString(batch);
        } catch (JsonProcessingException ex) {
            log.error("Falha ao serializar o lote de ticks", ex);
            return;
        }
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("tick").data(json));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        }
    }

    @Override
    public int clients() { return emitters.size(); }
}
