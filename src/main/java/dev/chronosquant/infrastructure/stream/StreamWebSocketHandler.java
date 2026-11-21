package dev.chronosquant.infrastructure.stream;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Difunde cada lote de ticks para os painéis conectados por WebSocket. */
@Component
public class StreamWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) { sessions.add(session); }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session); }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) { sessions.remove(session); }

    public void broadcast(String json) {
        TextMessage msg = new TextMessage(json);
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

    public int connections() { return sessions.size(); }
}
