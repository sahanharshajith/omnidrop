package com.omnidrop.discovery;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.websocket.WebSocketNotificationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
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

    // How long receive() waits before giving up, so the listener can notice stop()
    static final int LISTENER_TIMEOUT_MS = 1000;

    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final int httpPort;
    private final PeerRegistry registry;

    private DatagramSocket broadcastSocket;
    private DatagramSocket listenerSocket;
    private volatile boolean running = false;
    private volatile String lastError;
    private ScheduledExecutorService scheduler;
    private Thread listenerThread;

    @Autowired
    public UdpDiscoveryService(OmniDropProperties properties,
                               WebSocketNotificationService notificationService,
                               @Value("${server.port:8080}") int httpPort) {
        this(properties, notificationService, httpPort, new PeerRegistry());
    }

    /** For tests: lets a test pass a PeerRegistry with a fake clock. */
    UdpDiscoveryService(OmniDropProperties properties,
                        WebSocketNotificationService notificationService,
                        int httpPort,
                        PeerRegistry registry) {
        this.properties = properties;
        this.notificationService = notificationService;
        this.httpPort = httpPort;
        this.registry = registry;
    }

    /**
     * Initializes DatagramSockets and launches the background broadcast & listener threads.
     */
    public synchronized void start() {
        if (running) return;

        int udpPort = properties.getNetwork().getUdpPort();
        try {
            // Sockets initialized using java.net.DatagramSocket
            broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);

            // Listener socket: create unbound, allow address reuse, then bind to the UDP port.
            // Reuse avoids "Address already in use" when the backend is restarted quickly.
            listenerSocket = new DatagramSocket(null);
            listenerSocket.setReuseAddress(true);
            listenerSocket.setBroadcast(true);
            listenerSocket.bind(new InetSocketAddress(udpPort));
            // receive() gives up after 1 second so the listener thread can check if we stopped
            listenerSocket.setSoTimeout(LISTENER_TIMEOUT_MS);

            running = true;
            lastError = null;
            scheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "OmniDrop-UDP-Discovery");
                t.setDaemon(true);
                return t;
            });

            // Start UDP Packet Listener thread (daemon, so it never keeps the JVM alive)
            UdpPacketListener packetListener = new UdpPacketListener(listenerSocket, properties, this);
            listenerThread = new Thread(packetListener, "OmniDrop-UDP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Schedule periodic broadcast beacons
            UdpBeaconBroadcaster broadcaster = new UdpBeaconBroadcaster(broadcastSocket, properties, httpPort);
            scheduler.scheduleAtFixedRate(broadcaster, 0, properties.getNetwork().getBroadcastIntervalMs(), TimeUnit.MILLISECONDS);

            // Schedule stale peer eviction cleaner
            scheduler.scheduleAtFixedRate(this::evictStalePeers, 5, 5, TimeUnit.SECONDS);

            log.info("UDP Discovery Service started on port {}", getBoundPort());

        } catch (SocketException e) {
            lastError = "Could not open UDP port " + udpPort + ": " + e.getMessage();
            log.error("Failed to start UDP Discovery Service on port {}: {}", udpPort, e.getMessage());
            closeSockets();
        }
    }

    /**
     * Stops broadcasting and listening. Called automatically when Spring shuts down.
     */
    @PreDestroy
    public synchronized void stop() {
        if (!running && scheduler == null) return;
        // Tell the other nodes we are leaving, so they drop us at once instead of after the timeout
        if (running && broadcastSocket != null && !broadcastSocket.isClosed()) {
            new UdpBeaconBroadcaster(broadcastSocket, properties, httpPort, "LEAVE").run();
        }
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        closeSockets();
        if (listenerThread != null) {
            try {
                listenerThread.join(LISTENER_TIMEOUT_MS * 2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            listenerThread = null;
        }
        log.info("UDP Discovery Service stopped.");
    }

    private void closeSockets() {
        if (broadcastSocket != null && !broadcastSocket.isClosed()) broadcastSocket.close();
        if (listenerSocket != null && !listenerSocket.isClosed()) listenerSocket.close();
    }

    public boolean isRunning() {
        return running;
    }

    /** Why discovery could not start (e.g. port in use), or null when it is fine. */
    public String getLastError() {
        return lastError;
    }

    /** The UDP port the listener is actually bound to, or -1 when not running. */
    public int getBoundPort() {
        return listenerSocket != null && listenerSocket.isBound() && !listenerSocket.isClosed()
                ? listenerSocket.getLocalPort() : -1;
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
     * Called by UdpPacketListener when a peer sends a LEAVE beacon (it is shutting down).
     */
    public void unregisterPeer(String deviceId) {
        if (deviceId.equals(properties.getNode().getId())) {
            return;
        }
        registry.remove(deviceId).ifPresent(peer -> {
            log.info("Peer left: {} ({})", peer.getDeviceName(), deviceId);
            notificationService.notifyPeerLost(deviceId);
        });
    }

    /**
     * Evicts peers that have timed out without sending recent heartbeats.
     */
    void evictStalePeers() {
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
            new UdpBeaconBroadcaster(broadcastSocket, properties, httpPort).run();
        }
    }
}
