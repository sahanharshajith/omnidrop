package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * MEMBER 1 RESPONSIBILITY: UDP Device Discovery Module
 * ============================================================================
 * Concepts:
 * - java.net.DatagramSocket
 * - java.net.DatagramPacket
 * - UDP Subnet Broadcasting
 * - Socket Timeout and Peer Heartbeat Eviction
 * ============================================================================
 */

@Service
public class UdpDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryService.class);

    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final PeerRegistry registry = new PeerRegistry();

    private DatagramSocket broadcastSocket;
    private DatagramSocket listenerSocket;
    private volatile boolean running = false;
    private ScheduledExecutorService scheduler;

    public UdpDiscoveryService(OmniDropProperties properties, WebSocketNotificationService notificationService) {
        this.properties = properties;
        this.notificationService = notificationService;
    }

    /**
     * Initializes DatagramSockets and launches the background broadcast & listener threads.
     */
    public synchronized void start() {
        if (running) return;

        try {
            int udpPort = properties.getNetwork().getUdpPort();

            // Sockets initialized using java.net.DatagramSocket
            broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);

            // Listener socket bound to UDP port
            listenerSocket = new DatagramSocket(udpPort);
            listenerSocket.setBroadcast(true);

            running = true;
            scheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "OmniDrop-UDP-Discovery");
                t.setDaemon(true);
                return t;
            });

            // Start UDP Packet Listener thread
            UdpPacketListener packetListener = new UdpPacketListener(listenerSocket, properties, this);
            new Thread(packetListener, "OmniDrop-UDP-Listener").start();

            // Schedule periodic broadcast beacons
            UdpBeaconBroadcaster broadcaster = new UdpBeaconBroadcaster(broadcastSocket, properties);
            scheduler.scheduleAtFixedRate(broadcaster, 0, properties.getNetwork().getBroadcastIntervalMs(), TimeUnit.MILLISECONDS);

            // Schedule stale peer eviction cleaner
            scheduler.scheduleAtFixedRate(this::evictStalePeers, 5, 5, TimeUnit.SECONDS);

            log.info("UDP Discovery Service started on port {}", udpPort);

        } catch (SocketException e) {
            log.error("Failed to start UDP Discovery Service on port {}: {}", properties.getNetwork().getUdpPort(), e.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        if (broadcastSocket != null && !broadcastSocket.isClosed()) broadcastSocket.close();
        if (listenerSocket != null && !listenerSocket.isClosed()) listenerSocket.close();
        log.info("UDP Discovery Service stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Called by UdpPacketListener when a valid beacon datagram is decoded.
     */
    public void registerPeer(PeerDevice peer) {
        //if we get our beacon this ignores it
        if (peer.getDeviceId().equals(properties.getNode().getId())) {
            return;
        }

        PeerRegistry.Result result = registry.upsert(peer);

        if (result == PeerRegistry.Result.NEW) {
            log.info("Discovered new peer: {} ({}:{})", peer.getDeviceName(), peer.getIpAddress(), peer.getTcpPort());
            notificationService.notifyPeerDiscovered(peer);
        } else if (result == PeerRegistry.Result.UPDATED) {
            log.info("Peer changed: {} is now at {}:{}", peer.getDeviceName(), peer.getIpAddress(), peer.getTcpPort());
            notificationService.notifyPeerDiscovered(peer);
        }
        //REFRESHED is just a heartbeat, nothing to tell the UI
    }

    /**
     * Evicts peers that have timed out without sending recent heartbeats.
     */
    private void evictStalePeers() {
        long timeout = properties.getNetwork().getPeerTimeoutMs();
        for (PeerDevice peer : registry.evictStale(timeout)) {
            log.info("Peer timed out: {} ({})", peer.getDeviceName(), peer.getDeviceId());
            notificationService.notifyPeerLost(peer.getDeviceId());
        }
    }

    public List<PeerDevice> getActivePeers() {
        return registry.snapshot();
    }

    /**
     * Triggers an immediate one-off broadcast beacon.
     */
    public void triggerManualBroadcast() {
        if (broadcastSocket != null && !broadcastSocket.isClosed()) {
            new UdpBeaconBroadcaster(broadcastSocket, properties).run();
        }
    }
}
