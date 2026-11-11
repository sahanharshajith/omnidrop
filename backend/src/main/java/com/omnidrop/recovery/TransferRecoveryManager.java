package com.omnidrop.recovery;

import com.omnidrop.model.Checkpoint;
import com.omnidrop.utils.ChecksumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * ============================================================================
 * MEMBER 5 RESPONSIBILITY: Transfer Recovery & Integrity Module
 * ============================================================================
 * Concepts:
 * - java.nio.channels.FileChannel
 * - java.io.RandomAccessFile (Direct byte positioning / seeking)
 * - Checkpoint state persistence & retrieval
 * - SHA-256 cryptographic checksum calculation and integrity enforcement
 * ============================================================================
 */
@Service
public class TransferRecoveryManager {

    private static final Logger log = LoggerFactory.getLogger(TransferRecoveryManager.class);

    private final CheckpointStore checkpointStore;

    public TransferRecoveryManager(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
    }

    public void saveCheckpoint(Checkpoint checkpoint) {
        checkpointStore.save(checkpoint);
    }

    public Checkpoint getCheckpoint(String transferId) {
        return checkpointStore.get(transferId);
    }

    public List<Checkpoint> getInterruptedTransfers() {
        return checkpointStore.getAll();
    }

    public void deleteCheckpoint(String transferId) {
        checkpointStore.delete(transferId);
    }

    /**
     * Inspects a partial file and confirms its current valid byte length using FileChannel.
     */
    public long verifyPartialFileLength(String partFilePath) {
        File file = new File(partFilePath);
        if (!file.exists()) return 0;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {
            return channel.size();
        } catch (Exception e) {
            log.error("Failed to inspect FileChannel for {}: {}", partFilePath, e.getMessage());
            return 0;
        }
    }

    /**
     * Verifies that the completed file matches the expected SHA-256 hash.
     */
    public boolean verifyFileIntegrity(File file, String expectedSha256) {
        try {
            String actual = ChecksumUtils.calculateSha256(file);
            return actual.equalsIgnoreCase(expectedSha256);
        } catch (Exception e) {
            log.error("Failed to verify checksum on {}: {}", file.getName(), e.getMessage());
            return false;
        }
    }
}
