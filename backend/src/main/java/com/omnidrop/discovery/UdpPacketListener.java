package com.omnidrop.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * MEMBER 1: Listens on UDP port for peer discovery beacons using DatagramSocket and DatagramPacket.
 */
public class UdpPacketListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UdpPacketListener.class);

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
        byte[] buffer = new byte[4096];

        while (!socket.isClosed() && discoveryService.isRunning()) {
            try {
                // Construct DatagramPacket to receive inbound UDP datagram
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String receivedJson = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(receivedJson);

                if (root.has("magic") && "OMNI_DISCOVER".equals(root.get("magic").asText())) {
                    String deviceId = root.get("deviceId").asText();
                    String deviceName = root.get("deviceName").asText();
                    int tcpPort = root.get("tcpPort").asInt(9090);
                    int httpPort = root.has("httpPort") ? root.get("httpPort").asInt(8080) : 8080;
                    String senderIp = packet.getAddress().getHostAddress();

                    PeerDevice peer = new PeerDevice(deviceId, deviceName, senderIp, tcpPort, httpPort, System.currentTimeMillis());
                    discoveryService.registerPeer(peer);
                }

            } catch (Exception e) {
                if (!socket.isClosed()) {
                    log.warn("Error receiving UDP packet: {}", e.getMessage());
                }
            }
        }
    }
}
