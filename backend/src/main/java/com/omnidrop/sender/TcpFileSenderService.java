package com.omnidrop.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.FileMetadata;
import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferProgress;
import com.omnidrop.model.TransferState;
import com.omnidrop.protocol.FrameConstants;
import com.omnidrop.protocol.FrameDecoder;
import com.omnidrop.protocol.FrameEncoder;
import com.omnidrop.protocol.FrameHeader;
import com.omnidrop.protocol.FrameOpcode;
import com.omnidrop.utils.ChecksumUtils;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * MEMBER 2 RESPONSIBILITY: TCP File Sender Module
 * ============================================================================
 * Concepts:
 * - java.net.Socket (Client socket connection)
 * - java.io.InputStream and java.io.OutputStream
 * - Binary Protocol Framing (20-byte OmniDrop header)
 * - Chunked Streaming of Large Files without heap exhaustion
 * - Throughput & Progress Calculation published via WebSockets
 * - Cooperative cancellation & resumable transfers via in-memory registry
 * ============================================================================
 */
@Service
public class TcpFileSenderService {

    private static final Logger log = LoggerFactory.getLogger(TcpFileSenderService.class);

    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Registry of in-flight transfers, keyed by transferId. */
    private final Map<String, TransferContext> activeTransfers = new ConcurrentHashMap<>();

    public TcpFileSenderService(OmniDropProperties properties, WebSocketNotificationService notificationService) {
        this.properties = properties;
        this.notificationService = notificationService;
    }

    /**
     * Blocking entry point: generates a new transferId and runs the transfer
     * on the calling thread. Kept for the integration test and any synchronous
     * caller.
     */
    public TransferProgress sendFile(PeerDevice targetPeer, File file, long startOffset) throws IOException {
        String transferId = UUID.randomUUID().toString();
        return sendFileInternal(transferId, targetPeer, file, startOffset);
    }

    /**
     * Non-blocking variant. Generates a transferId, spawns a virtual thread to
     * run the transfer, and returns the id immediately. That id is immediately
     * usable with {@link #cancelTransfer(String)} and
     * {@link #resumeTransfer(String)}.
     */
    public String sendFileAsync(PeerDevice targetPeer, File file, long startOffset) {
        String transferId = UUID.randomUUID().toString();

        Thread.ofVirtual()
                .name("OmniDrop-Sender-" + transferId.substring(0, 8))
                .start(() -> {
                    try {
                        TransferProgress result = sendFileInternal(transferId, targetPeer, file, startOffset);
                        log.info("Async transfer {} finished with state {}", transferId, result.getState());
                    } catch (TransferCancelledException e) {
                        log.info("Async transfer {} cancelled: {}", transferId, e.getMessage());
                    } catch (Exception e) {
                        log.warn("Async transfer {} failed: {}", transferId, e.getMessage());
                    }
                });

        return transferId;
    }

    /**
     * Core transfer implementation. Caller supplies the transferId so the
     * registry entry (and thus cancel/resume) is keyed under an id the caller
     * can reference immediately.
     */
    private TransferProgress sendFileInternal(String transferId,
                                              PeerDevice targetPeer,
                                              File file,
                                              long startOffset) throws IOException {
        long fileSize = file.length();
        int chunkSize = properties.getTransfer().getChunkSizeBytes();
        String sha256 = ChecksumUtils.calculateSha256(file);

        FileMetadata metadata = new FileMetadata(transferId, file.getName(), fileSize, chunkSize, sha256);
        metadata.setStartOffset(startOffset);

        TransferProgress progress = TransferProgress.of(transferId, "SENDER", file.getName(), startOffset, fileSize);
        progress.setPeerName(targetPeer.getDeviceName());
        progress.setPeerIp(targetPeer.getIpAddress());
        progress.setTotalChunks(metadata.getTotalChunks());
        progress.setState(TransferState.CONNECTING);

        TransferContext ctx = new TransferContext(transferId, targetPeer, file, progress);
        activeTransfers.put(transferId, ctx);

        notificationService.notifyTransferStarted(progress);

        log.info("Initiating TCP connection to {}:{} for file '{}' ({} bytes, transferId={})",
                targetPeer.getIpAddress(), targetPeer.getTcpPort(), file.getName(), fileSize, transferId);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetPeer.getIpAddress(), targetPeer.getTcpPort()),
                    properties.getTransfer().getSocketTimeoutMs());
            socket.setSoTimeout(properties.getTransfer().getSocketTimeoutMs());

            try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                // Step 1: Handshake HELLO
                FrameEncoder.writeFrame(out, FrameOpcode.HELLO, 0, new byte[]{FrameConstants.PROTOCOL_VERSION});
                FrameHeader helloAck = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, helloAck);
                if (helloAck.opcode() != FrameOpcode.ACK) {
                    throw new IOException("Handshake failed, receiver sent opcode: " + helloAck.opcode());
                }

                // Step 2: Send FILE_OFFER with JSON metadata
                byte[] offerPayload = objectMapper.writeValueAsString(metadata).getBytes(StandardCharsets.UTF_8);
                FrameEncoder.writeFrame(out, FrameOpcode.FILE_OFFER, 0, offerPayload);

                // Step 3: Wait for ACCEPT or REJECT
                FrameHeader response = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, response);
                if (response.opcode() == FrameOpcode.REJECT) {
                    progress.setState(TransferState.FAILED);
                    progress.setErrorMessage("Receiver rejected the file offer.");
                    notificationService.notifyTransferFailed(progress);
                    return progress;
                } else if (response.opcode() != FrameOpcode.ACCEPT) {
                    throw new IOException("Unexpected response from receiver: " + response.opcode());
                }

                // Step 4: Stream file chunks
                progress.setState(TransferState.TRANSFERRING);
                streamChunks(file, metadata, startOffset, out, in, progress, ctx);

                // Step 5: Send TRANSFER_COMP
                FrameEncoder.writeFrame(out, FrameOpcode.TRANSFER_COMP, metadata.getTotalChunks(), new byte[0]);
                FrameHeader compAck = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, compAck);

                if (compAck.opcode() == FrameOpcode.ACK) {
                    progress.setState(TransferState.COMPLETED);
                    progress.setBytesTransferred(fileSize);
                    progress.setPercentage(100.0);
                    notificationService.notifyTransferCompleted(progress);
                    log.info("Successfully sent file '{}' to {}", file.getName(), targetPeer.getDeviceName());
                } else {
                    progress.setState(TransferState.FAILED);
                    progress.setErrorMessage("Receiver reported validation failure on completion.");
                    notificationService.notifyTransferFailed(progress);
                }

            }
        } catch (TransferCancelledException e) {
            log.info("Transfer {} cancelled by user at {} bytes", transferId, progress.getBytesTransferred());
            progress.setState(TransferState.INTERRUPTED);
            progress.setErrorMessage("Transfer cancelled by user.");
            notificationService.notifyTransferInterrupted(progress);
            throw e;
        } catch (Exception e) {
            log.error("TCP transfer failed to {}: {}", targetPeer.getDeviceName(), e.getMessage());
            progress.setState(TransferState.INTERRUPTED);
            progress.setErrorMessage(e.getMessage());
            notificationService.notifyTransferInterrupted(progress);
            throw new IOException("TCP file transfer failed: " + e.getMessage(), e);
        } finally {
            activeTransfers.remove(transferId);
        }

        return progress;
    }

    private void streamChunks(File file, FileMetadata metadata, long startOffset,
                              BufferedOutputStream out, BufferedInputStream in,
                              TransferProgress progress, TransferContext ctx) throws IOException {
        byte[] buffer = new byte[metadata.getChunkSize()];
        long bytesSent = startOffset;
        int chunkIndex = (int) (startOffset / metadata.getChunkSize());
        long startTime = System.currentTimeMillis();

        try (FileInputStream fis = new FileInputStream(file)) {
            if (startOffset > 0) {
                long skipped = fis.skip(startOffset);
                log.info("Resuming transfer from byte offset: {} (skipped: {})", startOffset, skipped);
            }

            int read;
            while ((read = fis.read(buffer)) != -1) {
                // Cooperative cancellation check — aborts at next chunk boundary.
                if (ctx.isCancelled()) {
                    throw new TransferCancelledException("Transfer cancelled by user at chunk " + chunkIndex);
                }

                chunkIndex++;
                byte[] chunkPayload = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);

                FrameEncoder.writeFrame(out, FrameOpcode.FILE_CHUNK, chunkIndex, chunkPayload);

                FrameHeader ack = FrameDecoder.readHeader(in);
                if (ack.opcode() != FrameOpcode.ACK || ack.chunkIndex() != chunkIndex) {
                    throw new IOException("Chunk ACK mismatch for chunk " + chunkIndex);
                }

                bytesSent += read;
                progress.setBytesTransferred(bytesSent);
                progress.setCurrentChunk(chunkIndex);
                progress.setPercentage((double) bytesSent * 100.0 / metadata.getFileSize());

                long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                double speed = ((double) (bytesSent - startOffset) / elapsed) * 1000.0;
                progress.setSpeedBytesPerSec(speed);
                progress.setSpeedFormatted(String.format("%.2f MB/s", speed / (1024 * 1024)));

                long remainingBytes = metadata.getFileSize() - bytesSent;
                progress.setEstimatedRemainingSeconds(speed > 0 ? remainingBytes / speed : 0);

                if (chunkIndex % 5 == 0 || bytesSent == metadata.getFileSize()) {
                    notificationService.notifyTransferProgress(progress);
                }
            }
        }
    }

    /**
     * Requests cooperative cancellation of an in-flight transfer.
     * The transfer aborts at the next chunk boundary and emits TRANSFER_INTERRUPTED.
     */
    public void cancelTransfer(String transferId) {
        TransferContext ctx = activeTransfers.get(transferId);
        if (ctx == null) {
            log.info("cancelTransfer ignored: no active transfer with id={}", transferId);
            return;
        }
        ctx.cancel();
        log.info("cancelTransfer requested for transferId={} (bytesTransferred={})",
                transferId, ctx.getProgress().getBytesTransferred());
    }

    /**
     * Resumes a previously interrupted transfer from its last known byte offset.
     * Does not block the caller — spawns a new virtual thread via sendFileAsync.
     */
    public void resumeTransfer(String transferId) {
        TransferContext ctx = activeTransfers.get(transferId);
        if (ctx == null) {
            log.info("resumeTransfer ignored: no context for transferId={} (may have completed or been cleaned up)", transferId);
            return;
        }
        long lastBytes = ctx.getProgress().getBytesTransferred();
        PeerDevice peer = ctx.getPeer();
        File file = ctx.getFile();

        log.info("resumeTransfer requested for transferId={} (resuming from offset {})", transferId, lastBytes);

        // Clean up the interrupted context before starting the new one.
        activeTransfers.remove(transferId);

        sendFileAsync(peer, file, lastBytes);
    }

    /** Snapshot of an in-flight transfer, used for cancel & resume. */
    private static final class TransferContext {
        private final String transferId;
        private final PeerDevice peer;
        private final File file;
        private final TransferProgress progress;
        private volatile boolean cancelled;

        TransferContext(String transferId, PeerDevice peer, File file, TransferProgress progress) {
            this.transferId = transferId;
            this.peer = peer;
            this.file = file;
            this.progress = progress;
        }

        boolean isCancelled() { return cancelled; }
        void cancel() { this.cancelled = true; }
        PeerDevice getPeer() { return peer; }
        File getFile() { return file; }
        TransferProgress getProgress() { return progress; }
        String getTransferId() { return transferId; }
    }

    /** Signals a user-initiated cancellation inside the chunk streaming loop. */
    public static class TransferCancelledException extends IOException {
        public TransferCancelledException(String message) {
            super(message);
        }
    }
}