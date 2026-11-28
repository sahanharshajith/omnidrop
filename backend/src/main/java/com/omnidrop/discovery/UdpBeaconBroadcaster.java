package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.utils.NetworkInterfaceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

/**
 * MEMBER 1: Transmits UDP discovery beacons to broadcast addresses.
 */
public class UdpBeaconBroadcaster implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UdpBeaconBroadcaster.class);

    private final DatagramSocket socket;
    private final OmniDropProperties properties;
    private final int httpPort;
    private final String event;

    /** Normal heartbeat beacon ("I am here"). */
    public UdpBeaconBroadcaster(DatagramSocket socket, OmniDropProperties properties, int httpPort) {
        this(socket, properties, httpPort, DiscoveryBeacon.EVENT_ALIVE);
    }

    /** event = ALIVE for heartbeats, LEAVE when this node is shutting down. */
    public UdpBeaconBroadcaster(DatagramSocket socket, OmniDropProperties properties, int httpPort, String event) {
        this.socket = socket;
        this.properties = properties;
        this.httpPort = httpPort;
        this.event = event;
    }

    /** Builds the beacon this node sends right now. */
    DiscoveryBeacon buildBeacon() {
        return new DiscoveryBeacon(
                properties.getNode().getId(),
                properties.getNode().getName(),
                properties.getNetwork().getTcpPort(),
                httpPort,
                System.currentTimeMillis(),
                event);
    }

    @Override
    public void run() {
        try {
            if (socket.isClosed()) return;

            byte[] data = BeaconCodec.encode(buildBeacon());

            // One packet per IPv4 subnet broadcast address (fallback 255.255.255.255)
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
