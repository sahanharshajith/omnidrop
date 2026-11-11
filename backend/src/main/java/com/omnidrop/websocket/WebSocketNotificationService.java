package com.omnidrop.websocket;

import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferProgress;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * High-level notification service called by networking modules (TCP/UDP)
 * to emit real-time status updates to connected browser interfaces.
 */
@Service
public class WebSocketNotificationService {

    private final TransferWebSocketHandler webSocketHandler;

    public WebSocketNotificationService(TransferWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public void notifyPeerDiscovered(PeerDevice peer) {
        webSocketHandler.broadcast(new WebSocketEvent("DEVICE_DISCOVERED", peer));
    }

    public void notifyPeerLost(String deviceId) {
        webSocketHandler.broadcast(new WebSocketEvent("DEVICE_LOST", Map.of("deviceId", deviceId)));
    }

    public void notifyTransferStarted(TransferProgress progress) {
        webSocketHandler.broadcast(new WebSocketEvent("TRANSFER_STARTED", progress));
    }

    public void notifyTransferProgress(TransferProgress progress) {
        webSocketHandler.broadcast(new WebSocketEvent("TRANSFER_PROGRESS", progress));
    }

    public void notifyTransferCompleted(TransferProgress progress) {
        webSocketHandler.broadcast(new WebSocketEvent("TRANSFER_COMPLETED", progress));
    }

    public void notifyTransferFailed(TransferProgress progress) {
        webSocketHandler.broadcast(new WebSocketEvent("TRANSFER_FAILED", progress));
    }

    public void notifyTransferInterrupted(TransferProgress progress) {
        webSocketHandler.broadcast(new WebSocketEvent("TRANSFER_INTERRUPTED", progress));
    }

    public void notifyDistributionCompleted(java.util.List<com.omnidrop.model.TransferProgress> results) {
        webSocketHandler.broadcast(new WebSocketEvent("DISTRIBUTION_COMPLETED", results));
    }

    public void notifyDistributionProgress(java.util.Map<String, Object> summary) {
        webSocketHandler.broadcast(new WebSocketEvent("DISTRIBUTION_PROGRESS", summary));
    }
}
