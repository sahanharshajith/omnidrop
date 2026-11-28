package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MEMBER 1: How the listener turns one datagram into registerPeer / unregisterPeer calls.
 */
class UdpPacketListenerTest {

    private UdpDiscoveryService service;
    private UdpPacketListener listener;

    @BeforeEach
    void setUp() {
        service = mock(UdpDiscoveryService.class);
        listener = new UdpPacketListener(null, new OmniDropProperties(), service);
    }

    private static DatagramPacket packet(byte[] data, String fromIp) throws Exception {
        DatagramPacket p = new DatagramPacket(data, data.length);
        p.setAddress(InetAddress.getByName(fromIp));
        return p;
    }

    private static byte[] beacon(String event) {
        return BeaconCodec.encode(new DiscoveryBeacon("peer-1", "Bob-Laptop", 9090, 8080, 1L, event));
    }

    @Test
    void aliveBeaconRegistersPeerWithSenderIp() throws Exception {
        listener.handlePacket(packet(beacon(DiscoveryBeacon.EVENT_ALIVE), "192.168.1.50"));

        ArgumentCaptor<PeerDevice> captor = ArgumentCaptor.forClass(PeerDevice.class);
        verify(service).registerPeer(captor.capture());
        PeerDevice peer = captor.getValue();
        assertThat(peer.getDeviceId()).isEqualTo("peer-1");
        assertThat(peer.getDeviceName()).isEqualTo("Bob-Laptop");
        assertThat(peer.getIpAddress()).isEqualTo("192.168.1.50");
        assertThat(peer.getTcpPort()).isEqualTo(9090);
    }

    @Test
    void leaveBeaconUnregistersPeer() throws Exception {
        listener.handlePacket(packet(beacon(DiscoveryBeacon.EVENT_LEAVE), "192.168.1.50"));

        verify(service).unregisterPeer("peer-1");
        verify(service, never()).registerPeer(any());
    }

    @Test
    void junkIsIgnored() throws Exception {
        listener.handlePacket(packet("hello world".getBytes(StandardCharsets.UTF_8), "192.168.1.50"));

        verify(service, never()).registerPeer(any());
        verify(service, never()).unregisterPeer(anyString());
    }

    @Test
    void oversizedPacketIsIgnored() throws Exception {
        byte[] big = new byte[UdpPacketListener.BUFFER_SIZE];
        byte[] valid = beacon(DiscoveryBeacon.EVENT_ALIVE);
        System.arraycopy(valid, 0, big, 0, valid.length);

        listener.handlePacket(packet(big, "192.168.1.50"));

        verify(service, never()).registerPeer(any());
    }
}
