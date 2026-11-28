package com.omnidrop.distribution;

import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferState;
import com.omnidrop.sender.TcpFileSenderService;
import com.omnidrop.websocket.WebSocketNotificationService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.IntStream;

@Service
public class OneToManyDistributionManager {
    private final TcpFileSenderService sender;
    private final WebSocketNotificationService notifications;
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final Map<String, Batch> batches = new ConcurrentHashMap<>();

    public OneToManyDistributionManager(TcpFileSenderService sender, WebSocketNotificationService notifications) {
        this.sender = sender;
        this.notifications = notifications;
    }

    public String distributeFileConcurrently(File file, List<PeerDevice> targets) {
        Instant cutoff = Instant.now().minusSeconds(900);
        batches.values().removeIf(batch -> batch.finishedAt.isBefore(cutoff));

        String id = "dist-" + UUID.randomUUID();
        Batch batch = new Batch(id, file, targets);
        batches.put(id, batch);
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            int slot = i;
            PeerDevice target = targets.get(slot);
            tasks[slot] = CompletableFuture.runAsync(() -> {
                batch.update(slot, 0, TransferState.CONNECTING, null);
                var worker = new ConcurrentTransferWorker(target, file, sender,
                        bytes -> batch.update(slot, bytes, TransferState.TRANSFERRING, null));
                var result = worker.call();
                batch.update(slot, Math.max(result.getBytesTransferred(), batch.receivers.get(slot).bytesTransferred()),
                        result.getState(), result.getErrorMessage());
            }, workers);
        }
        CompletableFuture.allOf(tasks).thenRun(() -> {
            batch.finishedAt = Instant.now();
            notifications.notifyDistributionProgress(batch.snapshot());
        });
        return id;
    }

    public Map<String, Object> getProgress(String id) {
        Batch batch = batches.get(id);
        return batch == null ? null : batch.snapshot();
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    public record ReceiverProgress(String deviceId, String deviceName, long bytesTransferred,
                                   TransferState state, String errorMessage) {}

    private static class Batch {
        private final String id;
        private final String fileName;
        private final long fileSize;
        private final AtomicReferenceArray<ReceiverProgress> receivers;
        private volatile Instant finishedAt = Instant.MAX;

        Batch(String id, File file, List<PeerDevice> targets) {
            this.id = id;
            fileName = file.getName();
            fileSize = file.length();
            receivers = new AtomicReferenceArray<>(targets.stream()
                    .map(peer -> new ReceiverProgress(peer.getDeviceId(), peer.getDeviceName(), 0,
                            TransferState.PENDING, null))
                    .toArray(ReceiverProgress[]::new));
        }

        void update(int slot, long bytes, TransferState state, String error) {
            ReceiverProgress previous = receivers.get(slot);
            receivers.set(slot, new ReceiverProgress(previous.deviceId(), previous.deviceName(), bytes, state, error));
        }

        Map<String, Object> snapshot() {
            List<ReceiverProgress> rows = IntStream.range(0, receivers.length()).mapToObj(receivers::get).toList();
            long completed = rows.stream().filter(row -> row.state() == TransferState.COMPLETED).count();
            long failed = rows.stream().filter(row -> switch (row.state()) {
                case FAILED, INTERRUPTED, CANCELLED -> true;
                default -> false;
            }).count();
            String status = "IN_PROGRESS";
            if (completed + failed == rows.size()) {
                status = failed == 0 ? "SUCCESS" : completed == 0 ? "FAILED" : "PARTIAL_SUCCESS";
            }
            return Map.of("distributionId", id, "fileName", fileName, "fileSize", fileSize,
                    "totalReceivers", rows.size(), "completedReceivers", completed, "failedReceivers", failed,
                    "status", status, "receivers", rows,
                    "bytesTransferred", rows.stream().mapToLong(ReceiverProgress::bytesTransferred).sum());
        }
    }
}
