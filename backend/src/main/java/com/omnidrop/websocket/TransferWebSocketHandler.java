package com.omnidrop.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.discovery.UdpDiscoveryService;
import com.omnidrop.sender.TcpFileSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active WebSocket sessions with browser clients, broadcasts events,
 * and dispatches incoming JSON commands (discovery ping, cancel, resume)
 * to the appropriate backend services.
 */
@Component
public class TransferWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UdpDiscoveryService udpDiscoveryService;
    private final TcpFileSenderService tcpFileSenderService;

    public TransferWebSocketHandler(@Lazy UdpDiscoveryService udpDiscoveryService,
                                    @Lazy TcpFileSenderService tcpFileSenderService) {
        this.udpDiscoveryService = udpDiscoveryService;
        this.tcpFileSenderService = tcpFileSenderService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: session ID={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: session ID={}, status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("Received message from client {}: {}", session.getId(), payload);

        try {
            JsonNode node = objectMapper.readTree(payload);
            String command = node.path("command").asText(null);

            if (command == null || command.isBlank()) {
                log.warn("Received command without 'command' field from session {}", session.getId());
                return;
            }

            switch (command) {
                case "TRIGGER_DISCOVERY_PING" -> {
                    log.info("Dispatching TRIGGER_DISCOVERY_PING from client {}", session.getId());
                    udpDiscoveryService.triggerManualBroadcast();
                }
                case "CANCEL_TRANSFER" -> {
                    String transferId = node.path("transferId").asText(null);
                    if (transferId == null || transferId.isBlank()) {
                        log.warn("CANCEL_TRANSFER received without transferId");
                        return;
                    }
                    log.info("Dispatching CANCEL_TRANSFER for transferId={}", transferId);
                    tcpFileSenderService.cancelTransfer(transferId);
                }
                case "RESUME_TRANSFER" -> {
                    String transferId = node.path("transferId").asText(null);
                    if (transferId == null || transferId.isBlank()) {
                        log.warn("RESUME_TRANSFER received without transferId");
                        return;
                    }
                    log.info("Dispatching RESUME_TRANSFER for transferId={}", transferId);
                    tcpFileSenderService.resumeTransfer(transferId);
                }
                default -> log.warn("Unknown command received: {}", command);
            }
        } catch (IOException e) {
            log.warn("Failed to parse WebSocket command payload: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error handling WebSocket command: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcasts an event to all connected frontends.
     */
    public synchronized void broadcast(WebSocketEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket event: {}", e.getMessage());
        }
    }
}