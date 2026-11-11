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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service to distribute a file to multiple peers concurrently using TCP.
 *
 * Each peer is handled by an independent {@link TcpFileSenderService} task.
 * Progress for each individual transfer is published via {@link WebSocketNotificationService}.
 * After all tasks complete, a summary progress is emitted.
 */
@Service
public class OneToManyDistributionService {

    private static final Logger log = LoggerFactory.getLogger(OneToManyDistributionService.class);

    private final TcpFileSenderService senderService;
    private final WebSocketNotificationService wsService;

    public OneToManyDistributionService(TcpFileSenderService senderService,
                                        WebSocketNotificationService wsService) {
        this.senderService = senderService;
        this.wsService = wsService;
    }

    /**
     * Distribute {@code file} to the provided list of {@code peers}.
     * Returns a list of {@link TransferProgress} objects – one per peer.
     */
    public List<TransferProgress> distributeFile(File file, List<PeerDevice> peers) {
        if (peers == null || peers.isEmpty()) {
            log.warn("No peers provided for distribution");
            return new ArrayList<>();
        }
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(peers.size(), 8));
        List<CompletableFuture<TransferProgress>> futures = new ArrayList<>();
        for (PeerDevice peer : peers) {
            CompletableFuture<TransferProgress> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // start from offset 0 for fresh distribution
                    TransferProgress progress = senderService.sendFile(peer, file, 0);
                    // individual progress notifications already emitted by senderService
                    return progress;
                } catch (Exception e) {
                    log.error("Distribution to peer {} failed: {}", peer.getDeviceName(), e.getMessage());
                    TransferProgress failed = TransferProgress.of("error-" + peer.getDeviceId(), "DISTRIBUTOR", file.getName(), 0, file.length());
                    failed.setState(com.omnidrop.model.TransferState.FAILED);
                    failed.setErrorMessage(e.getMessage());
                    wsService.notifyTransferFailed(failed);
                    return failed;
                }
            }, executor);
            futures.add(future);
        }
        // Wait for all to finish (with a reasonable timeout)
        List<TransferProgress> results = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
        } catch (Exception ex) {
            log.error("One‑to‑many distribution timeout or interruption: {}", ex.getMessage());
        }
        futures.forEach(f -> {
            try {
                results.add(f.get());
            } catch (Exception ignored) {}
        });
        executor.shutdownNow();
        // Emit a summary notification
        wsService.notifyDistributionCompleted(results);
        return results;
    }
}
