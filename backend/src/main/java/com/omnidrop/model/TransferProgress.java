package com.omnidrop.model;

import java.io.Serializable;

/**
 * Real-time progress update structure broadcast to WebSocket clients.
 */
public class TransferProgress implements Serializable {

    private String transferId;
    private String role; // "SENDER" or "RECEIVER"
    private String peerName;
    private String peerIp;
    private String fileName;
    private long bytesTransferred;
    private long totalBytes;
    private double percentage;
    private int currentChunk;
    private int totalChunks;
    private double speedBytesPerSec;
    private String speedFormatted;
    private double estimatedRemainingSeconds;
    private TransferState state;
    private String errorMessage;

    public TransferProgress() {}

    public static TransferProgress of(String transferId, String role, String fileName, long bytesTransferred, long totalBytes) {
        TransferProgress p = new TransferProgress();
        p.setTransferId(transferId);
        p.setRole(role);
        p.setFileName(fileName);
        p.setBytesTransferred(bytesTransferred);
        p.setTotalBytes(totalBytes);
        p.setPercentage(totalBytes > 0 ? (bytesTransferred * 100.0 / totalBytes) : 0.0);
        p.setState(TransferState.TRANSFERRING);
        return p;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPeerName() {
        return peerName;
    }

    public void setPeerName(String peerName) {
        this.peerName = peerName;
    }

    public String getPeerIp() {
        return peerIp;
    }

    public void setPeerIp(String peerIp) {
        this.peerIp = peerIp;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public void setBytesTransferred(long bytesTransferred) {
        this.bytesTransferred = bytesTransferred;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public int getCurrentChunk() {
        return currentChunk;
    }

    public void setCurrentChunk(int currentChunk) {
        this.currentChunk = currentChunk;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public double getSpeedBytesPerSec() {
        return speedBytesPerSec;
    }

    public void setSpeedBytesPerSec(double speedBytesPerSec) {
        this.speedBytesPerSec = speedBytesPerSec;
    }

    public String getSpeedFormatted() {
        return speedFormatted;
    }

    public void setSpeedFormatted(String speedFormatted) {
        this.speedFormatted = speedFormatted;
    }

    public double getEstimatedRemainingSeconds() {
        return estimatedRemainingSeconds;
    }

    public void setEstimatedRemainingSeconds(double estimatedRemainingSeconds) {
        this.estimatedRemainingSeconds = estimatedRemainingSeconds;
    }

    public TransferState getState() {
        return state;
    }

    public void setState(TransferState state) {
        this.state = state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
