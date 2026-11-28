package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * MEMBER 1: Real DatagramSockets on loopback. Starts the discovery service on a free UDP port,
 * sends beacons to it with a second socket, and checks the WebSocket notifications.
 */
class UdpLoopbackIntegrationTest {

    private WebSocketNotificationService notifications;
    private UdpDiscoveryService service;
    private DatagramSocket sender;

    @BeforeEach
    void setUp() throws Exception {
        OmniDropProperties properties = new OmniDropProperties();
        properties.getNode().setId("self-node");
        properties.getNetwork().setUdpPort(0); // 0 = let the OS pick a free port
        notifications = mock(WebSocketNotificationService.class);
        service = new UdpDiscoveryService(properties, notifications, 8080);
        service.start();
        sender = new DatagramSocket();
    }

    @AfterEach
    void tearDown() {
        sender.close();
        service.stop();
    }

    private void send(byte[] data) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        sender.send(new DatagramPacket(data, data.length, loopback, service.getBoundPort()));
    }

    private static byte[] beacon(String id, String event) {
        return BeaconCodec.encode(new DiscoveryBeacon(id, "Peer-" + id, 9090, 8080, 1L, event));
    }

    @Test
    void serviceStartsAndBindsAPort() {
        assertThat(service.isRunning()).isTrue();
        assertThat(service.getBoundPort()).isPositive();
        assertThat(service.getLastError()).isNull();
    }

    @Test
    void beaconOverLoopbackIsDiscovered() throws Exception {
        send(beacon("bob", DiscoveryBeacon.EVENT_ALIVE));

        ArgumentCaptor<PeerDevice> captor = ArgumentCaptor.forClass(PeerDevice.class);
        verify(notifications, timeout(3000)).notifyPeerDiscovered(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo("bob");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(service.getActivePeers()).extracting(PeerDevice::getDeviceId).containsExactly("bob");
    }

    @Test
    void leaveBeaconOverLoopbackRemovesPeer() throws Exception {
        send(beacon("bob", DiscoveryBeacon.EVENT_ALIVE));
        verify(notifications, timeout(3000)).notifyPeerDiscovered(any());

        send(beacon("bob", DiscoveryBeacon.EVENT_LEAVE));
        verify(notifications, timeout(3000)).notifyPeerLost("bob");
        assertThat(service.getActivePeers()).isEmpty();
    }

    @Test
    void junkDatagramIsIgnored() throws Exception {
        send("GET / HTTP/1.1".getBytes(StandardCharsets.UTF_8));

        verify(notifications, after(1500).never()).notifyPeerDiscovered(any());
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    void stopClosesEverything() {
        service.stop();

        assertThat(service.isRunning()).isFalse();
        assertThat(service.getBoundPort()).isEqualTo(-1);
    }
}
