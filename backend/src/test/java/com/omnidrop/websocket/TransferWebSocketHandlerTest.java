package com.omnidrop.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.discovery.UdpDiscoveryService;
import com.omnidrop.sender.TcpFileSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransferWebSocketHandler}.
 * Verifies command dispatch (to the correct service method) and event broadcasting
 * (to all connected sessions) without spinning up a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TransferWebSocketHandlerTest {

    @Mock
    private UdpDiscoveryService udpDiscoveryService;

    @Mock
    private TcpFileSenderService tcpFileSenderService;

    private TransferWebSocketHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new TransferWebSocketHandler(udpDiscoveryService, tcpFileSenderService);
    }

    // ------------------------------------------------------------------
    // Command dispatch tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TRIGGER_DISCOVERY_PING calls UdpDiscoveryService.triggerManualBroadcast()")
    void triggerDiscoveryPingInvokesUdpService() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("{\"command\":\"TRIGGER_DISCOVERY_PING\"}"));

        verify(udpDiscoveryService, times(1)).triggerManualBroadcast();
        verifyNoInteractions(tcpFileSenderService);
    }

    @Test
    @DisplayName("CANCEL_TRANSFER forwards the transferId to TcpFileSenderService.cancelTransfer")
    void cancelTransferDispatchesToSenderService() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        String transferId = "abc-123";

        String payload = objectMapper.writeValueAsString(
                java.util.Map.of("command", "CANCEL_TRANSFER", "transferId", transferId));
        handler.handleTextMessage(session, new TextMessage(payload));

        verify(tcpFileSenderService, times(1)).cancelTransfer(eq(transferId));
        verifyNoInteractions(udpDiscoveryService);
    }

    @Test
    @DisplayName("RESUME_TRANSFER forwards the transferId to TcpFileSenderService.resumeTransfer")
    void resumeTransferDispatchesToSenderService() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        String transferId = "xyz-789";

        String payload = objectMapper.writeValueAsString(
                java.util.Map.of("command", "RESUME_TRANSFER", "transferId", transferId));
        handler.handleTextMessage(session, new TextMessage(payload));

        verify(tcpFileSenderService, times(1)).resumeTransfer(eq(transferId));
        verifyNoInteractions(udpDiscoveryService);
    }

    @Test
    @DisplayName("CANCEL_TRANSFER without transferId is ignored (no service call, no throw)")
    void cancelTransferWithoutTransferIdIsIgnored() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("{\"command\":\"CANCEL_TRANSFER\"}"));

        verifyNoInteractions(tcpFileSenderService);
        verifyNoInteractions(udpDiscoveryService);
    }

    @Test
    @DisplayName("RESUME_TRANSFER without transferId is ignored (no service call, no throw)")
    void resumeTransferWithoutTransferIdIsIgnored() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("{\"command\":\"RESUME_TRANSFER\"}"));

        verifyNoInteractions(tcpFileSenderService);
        verifyNoInteractions(udpDiscoveryService);
    }

    @Test
    @DisplayName("Unknown command is ignored safely (no throw, no dispatch)")
    void unknownCommandIsIgnored() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        assertDoesNotThrow(() ->
                handler.handleTextMessage(session, new TextMessage("{\"command\":\"SOMETHING_ELSE\"}")));

        verifyNoInteractions(tcpFileSenderService, udpDiscoveryService);
    }

    @Test
    @DisplayName("Malformed JSON is swallowed without throwing")
    void malformedJsonIsSwallowed() {
        WebSocketSession session = mock(WebSocketSession.class);

        assertDoesNotThrow(() ->
                handler.handleTextMessage(session, new TextMessage("this is not json at all")));

        verifyNoInteractions(tcpFileSenderService, udpDiscoveryService);
    }

    // ------------------------------------------------------------------
    // Broadcast tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("broadcast serializes the event and sends it to every open session")
    void broadcastSendsToAllOpenSessions() throws Exception {
        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);
        when(s1.isOpen()).thenReturn(true);
        when(s2.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(s1);
        handler.afterConnectionEstablished(s2);

        WebSocketEvent event = new WebSocketEvent("TRANSFER_PROGRESS",
                java.util.Map.of("transferId", "t-1", "percentage", 42.0));

        handler.broadcast(event);

        // Each session should have received exactly one TextMessage
        ArgumentCaptor<TextMessage> captor1 = ArgumentCaptor.forClass(TextMessage.class);
        ArgumentCaptor<TextMessage> captor2 = ArgumentCaptor.forClass(TextMessage.class);
        verify(s1, times(1)).sendMessage(captor1.capture());
        verify(s2, times(1)).sendMessage(captor2.capture());

        // The serialized JSON should round-trip to the same event
        WebSocketEvent parsed1 = objectMapper.readValue(captor1.getValue().getPayload(), WebSocketEvent.class);
        WebSocketEvent parsed2 = objectMapper.readValue(captor2.getValue().getPayload(), WebSocketEvent.class);

        assertEquals("TRANSFER_PROGRESS", parsed1.getType());
        assertEquals("TRANSFER_PROGRESS", parsed2.getType());
        assertNotNull(parsed1.getPayload());
        assertNotNull(parsed2.getPayload());
    }

    @Test
    @DisplayName("broadcast skips closed sessions")
    void broadcastSkipsClosedSessions() throws Exception {
        WebSocketSession open = mock(WebSocketSession.class);
        WebSocketSession closed = mock(WebSocketSession.class);
        when(open.isOpen()).thenReturn(true);
        when(closed.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(open);
        handler.afterConnectionEstablished(closed);

        handler.broadcast(new WebSocketEvent("PING", java.util.Map.of()));

        verify(open, times(1)).sendMessage(any(TextMessage.class));
        verify(closed, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("afterConnectionClosed removes the session from the broadcast set")
    void disconnectedSessionNoLongerReceivesBroadcasts() throws Exception {
        WebSocketSession s1 = mock(WebSocketSession.class);
        // NOTE: intentionally NO when(s1.isOpen()).thenReturn(true) here —
        // after disconnect the session is removed from the broadcast set, so
        // isOpen() is never invoked. A stub here would trigger
        // Mockito's UnnecessaryStubbingException under strict stubs.

        handler.afterConnectionEstablished(s1);
        handler.afterConnectionClosed(s1, org.springframework.web.socket.CloseStatus.NORMAL);

        handler.broadcast(new WebSocketEvent("PING", java.util.Map.of()));

        verify(s1, never()).sendMessage(any(TextMessage.class));
    }
}