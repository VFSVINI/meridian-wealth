package dev.meridianwealth.infrastructure.stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final StreamWebSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(StreamWebSocketHandler handler, @Value("${meridianwealth.security.cors-allowed-origins:}") String origins) {
        this.handler = handler;
        this.allowedOrigins = java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // sem lista configurada, só a própria origem pode abrir o WebSocket (impede conexão de outro site)
        registry.addHandler(handler, "/ws/stream").setAllowedOrigins(allowedOrigins);
    }
}
