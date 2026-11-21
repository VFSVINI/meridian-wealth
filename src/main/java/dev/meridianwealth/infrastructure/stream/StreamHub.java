package dev.meridianwealth.infrastructure.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.TickBatch;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Publica cada lote por SSE e WebSocket. O JSON é serializado uma vez e reaproveitado. */
@Component
public class StreamHub {
    private static final Logger log = LoggerFactory.getLogger(StreamHub.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final StreamWebSocketHandler ws;
    private final ObjectMapper mapper;

    public StreamHub(StreamWebSocketHandler ws, ObjectMapper mapper) {
        this.ws = ws;
        this.mapper = mapper;
    }

    public SseEmitter subscribe() {
        SseEmitter e = new SseEmitter(0L);
        emitters.add(e);
        Runnable remove = () -> emitters.remove(e);
        e.onCompletion(remove);
        e.onTimeout(remove);
        e.onError(t -> remove.run());
        return e;
    }

    public void publish(TickBatch batch) {
        String json;
        try {
            json = mapper.writeValueAsString(batch);
        } catch (JsonProcessingException ex) {
            log.error("Falha ao serializar o lote de ticks", ex);
            return;
        }
        ws.broadcast(json);
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("tick").data(json));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        }
    }

    public int sseClients() { return emitters.size(); }

    public int wsClients() { return ws.connections(); }
}
