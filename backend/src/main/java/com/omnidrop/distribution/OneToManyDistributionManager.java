package com.omnidrop.distribution;

import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferProgress;
import com.omnidrop.sender.TcpFileSenderService;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ============================================================================
 * MEMBER 4 RESPONSIBILITY: One-to-Many Distribution Module
 * ============================================================================
 * Concepts:
 * - java.util.concurrent.ExecutorService (Thread pool management)
 * - Concurrent TCP connections to multiple distinct receiver devices
 * - Thread isolation: Slow/faulty receiver does not terminate other transfers
 * - Aggregation of distribution progress published via WebSockets
 * ============================================================================
 */
@Service
public class OneToManyDistributionManager {

    private static final Logger log = LoggerFactory.getLogger(OneToManyDistributionManager.class);

    private final TcpFileSenderService senderService;
    private final WebSocketNotificationService notificationService;
    private final ExecutorService distributionPool;

    public OneToManyDistributionManager(TcpFileSenderService senderService,
                                        WebSocketNotificationService notificationService) {
        this.senderService = senderService;
        this.notificationService = notificationService;
        // Bounded thread pool preventing resource exhaustion
        this.distributionPool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "OmniDrop-Distribution-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Concurrently distributes a single file to a list of target peer devices.
     */
    public String distributeFileConcurrently(File file, List<PeerDevice> targets) {
        String distributionId = "dist-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Starting One-to-Many distribution [{}] for file '{}' to {} receivers",
                distributionId, file.getName(), targets.size());

        List<Future<TransferProgress>> futures = new ArrayList<>();

        for (PeerDevice target : targets) {
            ConcurrentTransferWorker worker = new ConcurrentTransferWorker(target, file, senderService);
            futures.add(distributionPool.submit(worker));
        }

        // Asynchronously monitor aggregate completion
        distributionPool.submit(() -> monitorDistribution(distributionId, file.getName(), targets, futures));

        return distributionId;
    }

    private void monitorDistribution(String distributionId, String fileName, List<PeerDevice> targets, List<Future<TransferProgress>> futures) {
        int total = targets.size();
        int completed = 0;
        int failed = 0;

        for (Future<TransferProgress> f : futures) {
            try {
                TransferProgress progress = f.get();
                if (progress != null && progress.getErrorMessage() == null) {
                    completed++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("distributionId", distributionId);
        summary.put("fileName", fileName);
        summary.put("totalReceivers", total);
        summary.put("completedReceivers", completed);
        summary.put("failedReceivers", failed);
        summary.put("status", failed == 0 ? "SUCCESS" : "PARTIAL_SUCCESS");

        log.info("One-to-Many distribution [{}] completed: {} success, {} failed", distributionId, completed, failed);
        notificationService.notifyDistributionProgress(summary);
    }
}
