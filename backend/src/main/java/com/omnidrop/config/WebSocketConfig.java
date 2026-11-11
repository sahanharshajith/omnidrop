package com.omnidrop.config;

import com.omnidrop.websocket.TransferWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers Spring WebSocket endpoints for real-time frontend notifications.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TransferWebSocketHandler transferWebSocketHandler;

    public WebSocketConfig(TransferWebSocketHandler transferWebSocketHandler) {
        this.transferWebSocketHandler = transferWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(transferWebSocketHandler, "/ws/transfers")
                .setAllowedOrigins("*");
    }
}
