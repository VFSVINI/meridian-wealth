package dev.meridianwealth.infrastructure.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meridianwealth.application.TickBatch;
import dev.meridianwealth.application.port.TickSink;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Canal WebSocket do stream: difunde cada lote para os painéis conectados em {@code /ws/stream}. */
@Component
public class StreamWebSocketHandler extends TextWebSocketHandler implements TickSink {
    private static final Logger log = LoggerFactory.getLogger(StreamWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper mapper;

    public StreamWebSocketHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) { sessions.add(session); }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session); }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) { sessions.remove(session); }

    @Override
    public String channel() { return "WebSocket"; }

    @Override
    public void publish(TickBatch batch) {
        if (sessions.isEmpty()) return;
        TextMessage msg;
        try {
            msg = new TextMessage(mapper.writeValueAsString(batch));
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar o lote de ticks", e);
            return;
        }
        for (WebSocketSession s : sessions) {
            try {
                synchronized (s) {
                    if (s.isOpen()) s.sendMessage(msg);
                }
            } catch (IOException | RuntimeException e) {
                sessions.remove(s);
            }
        }
    }

    @Override
    public int clients() { return sessions.size(); }
}
