package com.omnidrop.networking;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferProgress;
import com.omnidrop.model.TransferState;
import com.omnidrop.receiver.TcpFileReceiverService;
import com.omnidrop.recovery.CheckpointStore;
import com.omnidrop.recovery.TransferRecoveryManager;
import com.omnidrop.sender.TcpFileSenderService;
import com.omnidrop.utils.ChecksumUtils;
import com.omnidrop.websocket.TransferWebSocketHandler;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test verifying that:
 * 1. TcpFileReceiverService starts a ServerSocket on loopback.
 * 2. TcpFileSenderService connects via java.net.Socket.
 * 3. Frames are streamed, assembled, and verified with SHA-256.
 */
class TcpTransferIntegrationTest {

    @TempDir
    Path tempDir;

    private OmniDropProperties properties;
    private TcpFileReceiverService receiverService;
    private TcpFileSenderService senderService;
    private int testPort;

    @BeforeEach
    void setUp() {
        testPort = 29090; // Use high ephemeral port for loopback testing

        properties = new OmniDropProperties();
        properties.getNetwork().setTcpPort(testPort);
        properties.getTransfer().setChunkSizeBytes(32768); // 32 KB chunks
        properties.getStorage().setDownloadsDir(tempDir.resolve("downloads").toString());
        properties.getStorage().setCheckpointsDir(tempDir.resolve("checkpoints").toString());

        TransferWebSocketHandler wsHandler = new TransferWebSocketHandler();
        WebSocketNotificationService wsService = new WebSocketNotificationService(wsHandler);
        CheckpointStore checkpointStore = new CheckpointStore(properties);
        TransferRecoveryManager recoveryManager = new TransferRecoveryManager(checkpointStore);

        receiverService = new TcpFileReceiverService(properties, wsService, recoveryManager);
        receiverService.startServer();

        senderService = new TcpFileSenderService(properties, wsService);
    }

    @AfterEach
    void tearDown() {
        if (receiverService != null) {
            receiverService.stopServer();
        }
    }

    @Test
    @DisplayName("Should successfully transfer multi-chunk file over local TCP socket and verify SHA-256")
    void testEndToEndTcpFileTransfer() throws IOException, InterruptedException {
        // Create synthetic 100 KB payload
        File sourceFile = tempDir.resolve("test_document.dat").toFile();
        byte[] payload = new byte[100 * 1024];
        new Random(42).nextBytes(payload);
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(payload);
        }

        String originalSha256 = ChecksumUtils.calculateSha256(sourceFile);

        // Define target peer pointing to local ServerSocket
        PeerDevice targetPeer = new PeerDevice("test-receiver", "LocalReceiver", "127.0.0.1", testPort, 8080, System.currentTimeMillis());

        // Perform TCP transfer
        TransferProgress result = senderService.sendFile(targetPeer, sourceFile, 0);

        assertEquals(TransferState.COMPLETED, result.getState());
        assertEquals(sourceFile.length(), result.getBytesTransferred());

        // Verify receiver wrote the file to disk
        File receivedFile = tempDir.resolve("downloads").resolve("test_document.dat").toFile();
        assertTrue(receivedFile.exists(), "Received file must exist in downloads directory");
        assertEquals(sourceFile.length(), receivedFile.length(), "File lengths must match");

        // Verify cryptographic SHA-256 hash match
        String receivedSha256 = ChecksumUtils.calculateSha256(receivedFile);
        assertEquals(originalSha256, receivedSha256, "SHA-256 checksums must match identically");
    }
}
