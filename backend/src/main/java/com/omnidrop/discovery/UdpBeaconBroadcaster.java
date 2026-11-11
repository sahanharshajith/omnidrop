package com.omnidrop.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.utils.NetworkInterfaceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MEMBER 1: Transmits UDP discovery beacons to broadcast addresses.
 */
public class UdpBeaconBroadcaster implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UdpBeaconBroadcaster.class);

    private final DatagramSocket socket;
    private final OmniDropProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UdpBeaconBroadcaster(DatagramSocket socket, OmniDropProperties properties) {
        this.socket = socket;
        this.properties = properties;
    }

    @Override
    public void run() {
        try {
            if (socket.isClosed()) return;

            Map<String, Object> beaconPayload = new HashMap<>();
            beaconPayload.put("magic", "OMNI_DISCOVER");
            beaconPayload.put("version", 1);
            beaconPayload.put("deviceId", properties.getNode().getId());
            beaconPayload.put("deviceName", properties.getNode().getName());
            beaconPayload.put("tcpPort", properties.getNetwork().getTcpPort());
            beaconPayload.put("httpPort", 8080);
            beaconPayload.put("timestamp", System.currentTimeMillis());

            byte[] data = objectMapper.writeValueAsString(beaconPayload).getBytes(StandardCharsets.UTF_8);

            List<InetAddress> targets = NetworkInterfaceUtils.getBroadcastAddresses();
            int port = properties.getNetwork().getUdpPort();

            for (InetAddress target : targets) {
                try {
                    // Uses java.net.DatagramPacket
                    DatagramPacket packet = new DatagramPacket(data, data.length, target, port);
                    socket.send(packet);
                } catch (Exception e) {
                    log.trace("Failed broadcast to {}: {}", target, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in UDP beacon broadcaster: {}", e.getMessage());
        }
    }
}
