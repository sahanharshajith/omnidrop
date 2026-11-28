package com.omnidrop.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * MEMBER 1: Listens on UDP port for peer discovery beacons using DatagramSocket and DatagramPacket.
 */
public class UdpPacketListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UdpPacketListener.class);

    // Biggest beacon we accept. A real beacon is about 200 bytes.
    private static final int BUFFER_SIZE = 4096;
    private static final String MAGIC = "OMNI_DISCOVER";
    private static final int VERSION = 1;

    private final DatagramSocket socket;
    private final OmniDropProperties properties;
    private final UdpDiscoveryService discoveryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            // A full buffer means the datagram may have been cut off, so we don't trust it
            if (packet.getLength() >= BUFFER_SIZE) {
                log.debug("Dropped oversized UDP packet from {}", packet.getAddress());
                continue;
            }

            PeerDevice peer = parseBeacon(packet);
            if (peer == null) {
                continue;
            }
            if (isLeave(packet)) {
                // The sender is shutting down: remove it right away
                discoveryService.unregisterPeer(peer.getDeviceId());
            } else {
                discoveryService.registerPeer(peer);
            }
        }
    }

    /**
     * Turns a datagram into a PeerDevice, or returns null if it is not a valid OmniDrop beacon.
     */
    PeerDevice parseBeacon(DatagramPacket packet) {
        String json = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
        String from = packet.getAddress() != null ? packet.getAddress().getHostAddress() : "unknown";

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.debug("Dropped non-JSON UDP packet from {}", from);
            return null;
        }
        if (root == null || !root.isObject()) {
            log.debug("Dropped UDP packet from {}: not a JSON object", from);
            return null;
        }

        // Rule 1: must be our protocol
        if (!MAGIC.equals(root.path("magic").asText())) {
            log.debug("Dropped UDP packet from {}: wrong magic", from);
            return null;
        }
        // Rule 2: must be a version we understand
        if (root.path("version").asInt(-1) != VERSION) {
            log.debug("Dropped beacon from {}: unsupported version", from);
            return null;
        }
        // Rule 3: required fields must be present
        String deviceId = root.path("deviceId").asText("").trim();
        String deviceName = root.path("deviceName").asText("").trim();
        if (deviceId.isEmpty() || deviceName.isEmpty()) {
            log.debug("Dropped beacon from {}: missing deviceId or deviceName", from);
            return null;
        }
        // Rule 4: ports must be real port numbers
        int tcpPort = root.path("tcpPort").asInt(-1);
        int httpPort = root.path("httpPort").asInt(8080);
        if (!isValidPort(tcpPort) || !isValidPort(httpPort)) {
            log.debug("Dropped beacon from {}: invalid port", from);
            return null;
        }

        // The sender's real IP comes from the packet itself, not from the JSON
        return new PeerDevice(deviceId, deviceName, from, tcpPort, httpPort, System.currentTimeMillis());
    }

    /**
     * True when the beacon says "event": "LEAVE". A missing event means ALIVE (older nodes don't send it).
     */
    boolean isLeave(DatagramPacket packet) {
        try {
            JsonNode root = objectMapper.readTree(packet.getData(), packet.getOffset(), packet.getLength());
            return "LEAVE".equals(root.path("event").asText("ALIVE"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }
}
