package com.omnidrop.model;

import java.io.Serializable;

/**
 * Metadata describing a file being offered or received over TCP.
 */
public class FileMetadata implements Serializable {

    private String transferId;
    private String fileName;
    private long fileSize;
    private int chunkSize;
    private int totalChunks;
    private String sha256Checksum;
    private long startOffset;

    public FileMetadata() {}

    public FileMetadata(String transferId, String fileName, long fileSize, int chunkSize, String sha256Checksum) {
        this.transferId = transferId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.sha256Checksum = sha256Checksum;
        this.totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        this.startOffset = 0;
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

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(long startOffset) {
        this.startOffset = startOffset;
    }
}
