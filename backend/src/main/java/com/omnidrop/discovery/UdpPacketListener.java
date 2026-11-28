package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.Optional;

/**
 * MEMBER 1: Listens on UDP port for peer discovery beacons using DatagramSocket and DatagramPacket.
 */
public class UdpPacketListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UdpPacketListener.class);

    // Biggest beacon we accept. A real beacon is about 200 bytes.
    static final int BUFFER_SIZE = 4096;

    private final DatagramSocket socket;
    private final OmniDropProperties properties;
    private final UdpDiscoveryService discoveryService;

    public UdpPacketListener(DatagramSocket socket, OmniDropProperties properties, UdpDiscoveryService discoveryService) {
        this.socket = socket;
        this.properties = properties;
        this.discoveryService = discoveryService;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (!socket.isClosed() && discoveryService.isRunning()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                // Blocks until a datagram arrives OR the socket timeout (setSoTimeout) passes
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                // Normal: nothing arrived in time. Loop again so we can notice a stop().
                continue;
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    log.warn("Error receiving UDP packet: {}", e.getMessage());
                }
                continue;
            }

            handlePacket(packet);
        }
    }

    /**
     * Validates one datagram and passes it to the discovery service.
     */
    void handlePacket(DatagramPacket packet) {
        String from = packet.getAddress() != null ? packet.getAddress().getHostAddress() : "unknown";

        // A full buffer means the datagram may have been cut off, so we don't trust it
        if (packet.getLength() >= BUFFER_SIZE) {
            log.debug("Dropped oversized UDP packet from {}", from);
            return;
        }

        Optional<DiscoveryBeacon> decoded = BeaconCodec.decode(packet.getData(), packet.getOffset(), packet.getLength());
        if (decoded.isEmpty()) {
            log.debug("Dropped invalid UDP packet from {}", from);
            return;
        }
        DiscoveryBeacon beacon = decoded.get();

        if (beacon.isLeave()) {
            // The sender is shutting down: remove it right away
            discoveryService.unregisterPeer(beacon.deviceId());
        } else {
            discoveryService.registerPeer(toPeer(beacon, from));
        }
    }

    /**
     * The sender's real IP comes from the packet itself, not from the JSON.
     */
    static PeerDevice toPeer(DiscoveryBeacon beacon, String senderIp) {
        return new PeerDevice(beacon.deviceId(), beacon.deviceName(), senderIp,
                beacon.tcpPort(), beacon.httpPort(), System.currentTimeMillis());
    }
}
