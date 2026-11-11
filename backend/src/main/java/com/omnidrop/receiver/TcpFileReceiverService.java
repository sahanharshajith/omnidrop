package com.omnidrop.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.recovery.TransferRecoveryManager;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 * MEMBER 3 RESPONSIBILITY: TCP File Receiver Module
 * ============================================================================
 * Concepts:
 * - java.net.ServerSocket (Server-side TCP listener)
 * - java.net.Socket (Accepted client connections)
 * - Safe Path Validation & Sanitization (prevent ../ traversal attacks)
 * - Streaming received frames directly to disk without memory bloat
 * - Receiver-side Progress and Event Emission via WebSockets
 * ============================================================================
 */
@Service
public class TcpFileReceiverService {

    private static final Logger log = LoggerFactory.getLogger(TcpFileReceiverService.class);

    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final TransferRecoveryManager recoveryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private ExecutorService connectionWorkerPool;

    public TcpFileReceiverService(OmniDropProperties properties,
                                  WebSocketNotificationService notificationService,
                                  TransferRecoveryManager recoveryManager) {
        this.properties = properties;
        this.notificationService = notificationService;
        this.recoveryManager = recoveryManager;
    }

    /**
     * Binds ServerSocket to port 9090 and listens for incoming connections.
     */
    public synchronized void startServer() {
        if (running) return;

        int port = properties.getNetwork().getTcpPort();
        try {
            // Core Java Networking: ServerSocket
            serverSocket = new ServerSocket(port);
            running = true;
            connectionWorkerPool = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "OmniDrop-Receiver-Worker");
                t.setDaemon(true);
                return t;
            });

            // Ensure download directories exist
            File downloadDir = new File(properties.getStorage().getDownloadsDir());
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            Thread acceptThread = new Thread(this::listenLoop, "OmniDrop-ServerSocket-Accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            log.info("TCP File Receiver ServerSocket listening on port {}", port);

        } catch (IOException e) {
            log.error("Failed to bind TCP ServerSocket on port {}: {}", port, e.getMessage());
        }
    }

    public synchronized void stopServer() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        if (connectionWorkerPool != null) {
            connectionWorkerPool.shutdownNow();
        }
        log.info("TCP File Receiver ServerSocket stopped.");
    }

    private void listenLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                // Blocks until an incoming TCP client connects
                Socket clientSocket = serverSocket.accept();
                log.info("Accepted incoming TCP connection from {}:{}",
                        clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());

                ReceiverConnectionHandler handler = new ReceiverConnectionHandler(
                        clientSocket, properties, notificationService, recoveryManager, objectMapper);
                connectionWorkerPool.submit(handler);

            } catch (IOException e) {
                if (running) {
                    log.warn("Error accepting TCP connection: {}", e.getMessage());
                }
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
