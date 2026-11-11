package com.omnidrop.model;

import java.io.Serializable;

/**
 * Checkpoint record persisted to disk to enable transfer recovery and resumption.
 */
public class Checkpoint implements Serializable {

    private String transferId;
    private String fileName;
    private long fileSize;
    private long lastOffset;
    private int lastChunkIndex;
    private String expectedSha256;
    private String partFilePath;
    private long lastUpdatedTimestamp;

    public Checkpoint() {}

    public Checkpoint(String transferId, String fileName, long fileSize, long lastOffset, int lastChunkIndex, String expectedSha256, String partFilePath) {
        this.transferId = transferId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.lastOffset = lastOffset;
        this.lastChunkIndex = lastChunkIndex;
        this.expectedSha256 = expectedSha256;
        this.partFilePath = partFilePath;
        this.lastUpdatedTimestamp = System.currentTimeMillis();
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getLastOffset() {
        return lastOffset;
    }

    public void setLastOffset(long lastOffset) {
        this.lastOffset = lastOffset;
    }

    public int getLastChunkIndex() {
        return lastChunkIndex;
    }

    public void setLastChunkIndex(int lastChunkIndex) {
        this.lastChunkIndex = lastChunkIndex;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }

    public String getPartFilePath() {
        return partFilePath;
    }

    public void setPartFilePath(String partFilePath) {
        this.partFilePath = partFilePath;
    }

    public long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp(long lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }
}
