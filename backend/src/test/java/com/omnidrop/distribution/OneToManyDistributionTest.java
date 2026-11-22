package com.omnidrop.distribution;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.receiver.TcpFileReceiverService;
import com.omnidrop.recovery.CheckpointStore;
import com.omnidrop.recovery.TransferRecoveryManager;
import com.omnidrop.sender.TcpFileSenderService;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OneToManyDistributionTest {

    @TempDir
    Path tempDir;

    @Test
    void sendsTheSameFileToTwoReceiversDespiteAnIndependentFailure() throws Exception {
        var notifications = mock(WebSocketNotificationService.class);
        var first = receiverProperties("first");
        var second = receiverProperties("second");
        var firstReceiver = new TcpFileReceiverService(first, notifications, new TransferRecoveryManager(new CheckpointStore(first)));
        var secondReceiver = new TcpFileReceiverService(second, notifications, new TransferRecoveryManager(new CheckpointStore(second)));

        var senderProperties = new OmniDropProperties();
        senderProperties.getTransfer().setChunkSizeBytes(32768);
        senderProperties.getTransfer().setSocketTimeoutMs(1500);
        var manager = new OneToManyDistributionManager(new TcpFileSenderService(senderProperties, notifications),
                notifications);

        byte[] payload = new byte[96 * 1024];
        new Random(42).nextBytes(payload);
        Path input = Files.write(tempDir.resolve("distribution.bin"), payload);

        try (var unresponsiveReceiver = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            firstReceiver.startServer();
            secondReceiver.startServer();
            String id = manager.distributeFileConcurrently(input.toFile(), List.of(
                    peer("first", first.getNetwork().getTcpPort()),
                    peer("unresponsive", unresponsiveReceiver.getLocalPort()),
                    peer("second", second.getNetwork().getTcpPort())));

            Map<String, Object> result = manager.getProgress(id);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ("IN_PROGRESS".equals(result.get("status")) && System.nanoTime() < deadline) {
                Thread.sleep(20);
                result = manager.getProgress(id);
            }

            assertEquals("PARTIAL_SUCCESS", result.get("status"));
            assertEquals(2L, result.get("completedReceivers"));
            assertEquals(1L, result.get("failedReceivers"));
            assertEquals(2L * payload.length, result.get("bytesTransferred"));
            assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve("first/distribution.bin")));
            assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve("second/distribution.bin")));
        } finally {
            manager.shutdown();
            firstReceiver.stopServer();
            secondReceiver.stopServer();
        }
    }

    private OmniDropProperties receiverProperties(String name) throws Exception {
        var properties = new OmniDropProperties();
        properties.getStorage().setDownloadsDir(tempDir.resolve(name).toString());
        properties.getStorage().setCheckpointsDir(tempDir.resolve(name + "-checkpoints").toString());
        try (var socket = new ServerSocket(0)) {
            properties.getNetwork().setTcpPort(socket.getLocalPort());
        }
        return properties;
    }

    private PeerDevice peer(String name, int port) {
        return new PeerDevice(name, name, "127.0.0.1", port, 8080, System.currentTimeMillis());
    }
}
