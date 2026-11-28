package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MEMBER 1: Self filter, WebSocket notifications and heartbeat expiry (no sockets, fake clock).
 */
class UdpDiscoveryServiceTest {

    private static final String SELF_ID = "self-node";

    private OmniDropProperties properties;
    private WebSocketNotificationService notifications;
    private AtomicLong now;
    private UdpDiscoveryService service;

    @BeforeEach
    void setUp() {
        properties = new OmniDropProperties();
        properties.getNode().setId(SELF_ID);
        properties.getNetwork().setPeerTimeoutMs(12_000L);
        notifications = mock(WebSocketNotificationService.class);
        now = new AtomicLong(0L);
        service = new UdpDiscoveryService(properties, notifications, 8080, new PeerRegistry(now::get));
    }

    private static PeerDevice peer(String id, String ip) {
        return new PeerDevice(id, "Laptop-" + id, ip, 9090, 8080, 0L);
    }

    @Test
    void ownBeaconIsIgnored() {
        service.registerPeer(peer(SELF_ID, "192.168.1.2"));

        assertThat(service.getActivePeers()).isEmpty();
        verify(notifications, never()).notifyPeerDiscovered(any());
    }

    @Test
    void newPeerIsNotifiedOnce() {
        service.registerPeer(peer("bob", "192.168.1.3"));
        service.registerPeer(peer("bob", "192.168.1.3")); // heartbeat

        verify(notifications, times(1)).notifyPeerDiscovered(any());
        assertThat(service.getActivePeers()).hasSize(1);
    }

    @Test
    void ipChangeIsNotifiedAgain() {
        service.registerPeer(peer("bob", "192.168.1.3"));
        service.registerPeer(peer("bob", "192.168.1.99"));

        verify(notifications, times(2)).notifyPeerDiscovered(any());
        assertThat(service.getActivePeers().get(0).getIpAddress()).isEqualTo("192.168.1.99");
    }

    @Test
    void silentPeerIsEvictedAndReportedLost() {
        service.registerPeer(peer("bob", "192.168.1.3"));

        now.set(5_000L);
        service.evictStalePeers();
        verify(notifications, never()).notifyPeerLost(anyString());

        now.set(12_001L);
        service.evictStalePeers();
        verify(notifications).notifyPeerLost("bob");
        assertThat(service.getActivePeers()).isEmpty();
    }

    @Test
    void leaveRemovesPeerImmediately() {
        service.registerPeer(peer("bob", "192.168.1.3"));

        service.unregisterPeer("bob");

        verify(notifications).notifyPeerLost("bob");
        assertThat(service.getActivePeers()).isEmpty();
    }

    @Test
    void leaveForUnknownPeerOrSelfDoesNothing() {
        service.unregisterPeer("nobody");
        service.unregisterPeer(SELF_ID);

        verify(notifications, never()).notifyPeerLost(anyString());
    }

    @Test
    void notRunningBeforeStart() {
        assertThat(service.isRunning()).isFalse();
        assertThat(service.getBoundPort()).isEqualTo(-1);
        assertThat(service.getLastError()).isNull();
        service.triggerManualBroadcast(); // must not throw when not started
    }
}
