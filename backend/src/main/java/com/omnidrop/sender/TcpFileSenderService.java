package com.omnidrop.sender;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

@Service
public class TcpFileSenderService {

    private static final Logger log =
            LoggerFactory.getLogger(TcpFileSenderService.class);

    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Registry of in-flight transfers, keyed by transferId. */
    private final Map<String, TransferContext> activeTransfers =
            new ConcurrentHashMap<>();

    public TcpFileSenderService(
            OmniDropProperties properties,
            WebSocketNotificationService notificationService) {
        this.properties = properties;
        this.notificationService = notificationService;
    }

    /**
     * Blocking entry point.
     */
    public TransferProgress sendFile(
            PeerDevice targetPeer,
            File file,
            long startOffset) throws IOException {

        String transferId = UUID.randomUUID().toString();

        return sendFileInternal(
                transferId,
                targetPeer,
                file,
                startOffset
        );
    }

    /**
     * Non-blocking variant.
     */
    public String sendFileAsync(
            PeerDevice targetPeer,
            File file,
            long startOffset) {

        String transferId = UUID.randomUUID().toString();

        Thread.ofVirtual()
                .name("OmniDrop-Sender-" + transferId.substring(0, 8))
                .start(() -> {
                    try {
                        TransferProgress result = sendFileInternal(
                                transferId,
                                targetPeer,
                                file,
                                startOffset
                        );

                        log.info(
                                "Async transfer {} finished with state {}",
                                transferId,
                                result.getState()
                        );

                    } catch (TransferCancelledException e) {
                        log.info(
                                "Async transfer {} cancelled: {}",
                                transferId,
                                e.getMessage()
                        );

                    } catch (Exception e) {
                        log.warn(
                                "Async transfer {} failed: {}",
                                transferId,
                                e.getMessage()
                        );
                    }
                });

        return transferId;
    }

    /**
     * Core transfer implementation.
     */
    private TransferProgress sendFileInternal(
            String transferId,
            PeerDevice targetPeer,
            File file,
            long startOffset) throws IOException {

        long fileSize = file.length();

        int chunkSize =
                properties.getTransfer().getChunkSizeBytes();

        String sha256 =
                ChecksumUtils.calculateSha256(file);

        FileMetadata metadata = new FileMetadata(
                transferId,
                file.getName(),
                fileSize,
                chunkSize,
                sha256
        );

        metadata.setStartOffset(startOffset);

        TransferProgress progress = TransferProgress.of(
                transferId,
                "SENDER",
                file.getName(),
                startOffset,
                fileSize
        );

        progress.setPeerName(targetPeer.getDeviceName());
        progress.setPeerIp(targetPeer.getIpAddress());
        progress.setTotalChunks(metadata.getTotalChunks());
        progress.setState(TransferState.CONNECTING);

        TransferContext ctx = new TransferContext(
                transferId,
                targetPeer,
                file,
                progress
        );

        activeTransfers.put(transferId, ctx);

        notificationService.notifyTransferStarted(progress);

        log.info(
                "Initiating TCP connection to {}:{} for file '{}' ({} bytes, transferId={})",
                targetPeer.getIpAddress(),
                targetPeer.getTcpPort(),
                file.getName(),
                fileSize,
                transferId
        );

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(
                            targetPeer.getIpAddress(),
                            targetPeer.getTcpPort()
                    ),
                    properties.getTransfer().getSocketTimeoutMs()
            );

            socket.setSoTimeout(
                    properties.getTransfer().getSocketTimeoutMs()
            );

            try (BufferedOutputStream out =
                         new BufferedOutputStream(socket.getOutputStream());

                 BufferedInputStream in =
                         new BufferedInputStream(socket.getInputStream())) {

                // Step 1: Handshake HELLO
                FrameEncoder.writeFrame(
                        out,
                        FrameOpcode.HELLO,
                        0,
                        new byte[]{FrameConstants.PROTOCOL_VERSION}
                );

                FrameHeader helloAck = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, helloAck);

                if (helloAck.opcode() != FrameOpcode.ACK) {
                    throw new IOException(
                            "Handshake failed, receiver sent opcode: "
                                    + helloAck.opcode()
                    );
                }

                // Step 2: Send FILE_OFFER with JSON metadata
                byte[] offerPayload = objectMapper
                        .writeValueAsString(metadata)
                        .getBytes(StandardCharsets.UTF_8);

                FrameEncoder.writeFrame(
                        out,
                        FrameOpcode.FILE_OFFER,
                        0,
                        offerPayload
                );

                // Step 3: Wait for ACCEPT or REJECT
                FrameHeader response = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, response);

                if (response.opcode() == FrameOpcode.REJECT) {

                    progress.setState(TransferState.FAILED);

                    progress.setErrorMessage(
                            "Receiver rejected the file offer."
                    );

                    notificationService.notifyTransferFailed(progress);

                    activeTransfers.remove(transferId);

                    return progress;

                } else if (response.opcode() != FrameOpcode.ACCEPT) {

                    throw new IOException(
                            "Unexpected response from receiver: "
                                    + response.opcode()
                    );
                }

                // Step 4: Stream file chunks
                progress.setState(TransferState.TRANSFERRING);

                streamChunks(
                        file,
                        metadata,
                        startOffset,
                        out,
                        in,
                        progress,
                        ctx
                );

                // Step 5: Send TRANSFER_COMP
                FrameEncoder.writeFrame(
                        out,
                        FrameOpcode.TRANSFER_COMP,
                        metadata.getTotalChunks(),
                        new byte[0]
                );

                FrameHeader compAck = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, compAck);

                if (compAck.opcode() == FrameOpcode.ACK) {

                    progress.setState(TransferState.COMPLETED);
                    progress.setBytesTransferred(fileSize);
                    progress.setPercentage(100.0);

                    notificationService.notifyTransferCompleted(progress);

                    log.info(
                            "Successfully sent file '{}' to {}",
                            file.getName(),
                            targetPeer.getDeviceName()
                    );

                } else {

                    progress.setState(TransferState.FAILED);

                    progress.setErrorMessage(
                            "Receiver reported validation failure on completion."
                    );

                    notificationService.notifyTransferFailed(progress);
                }
            }

        } catch (TransferCancelledException e) {

            log.info(
                    "Transfer {} cancelled by user at {} bytes",
                    transferId,
                    progress.getBytesTransferred()
            );

            progress.setState(TransferState.INTERRUPTED);

            progress.setErrorMessage("Transfer cancelled by user.");

            notificationService.notifyTransferInterrupted(progress);

            // IMPORTANT: Do NOT remove from activeTransfers here.
            // The context must survive so resumeTransfer() can find it.
            throw e;

        } catch (Exception e) {

            log.error(
                    "TCP transfer failed to {}: {}",
                    targetPeer.getDeviceName(),
                    e.getMessage()
            );

            progress.setState(TransferState.INTERRUPTED);

            progress.setErrorMessage(e.getMessage());

            notificationService.notifyTransferInterrupted(progress);

            // Genuine failure: clean up the transfer context.
            activeTransfers.remove(transferId);

            throw new IOException(
                    "TCP file transfer failed: " + e.getMessage(),
                    e
            );
        }

        // Successful completion: remove from registry.
        activeTransfers.remove(transferId);

        return progress;
    }

    /**
     * Streams file chunks and reports transfer progress.
     */
    private void streamChunks(
            File file,
            FileMetadata metadata,
            long startOffset,
            BufferedOutputStream out,
            BufferedInputStream in,
            TransferProgress progress,
            TransferContext ctx) throws IOException {

        byte[] buffer = new byte[metadata.getChunkSize()];

        long bytesSent = startOffset;

        int chunkIndex =
                (int) (startOffset / metadata.getChunkSize());

        long startTime = System.currentTimeMillis();

        try (FileInputStream fis = new FileInputStream(file)) {

            if (startOffset > 0) {

                long skipped = fis.skip(startOffset);

                log.info(
                        "Resuming transfer from byte offset: {} (skipped: {})",
                        startOffset,
                        skipped
                );
            }

            int read;

            while ((read = fis.read(buffer)) != -1) {

                // Cooperative cancellation check.
                if (ctx.isCancelled()) {
                    throw new TransferCancelledException(
                            "Transfer cancelled by user at chunk "
                                    + chunkIndex
                    );
                }

                chunkIndex++;

                byte[] chunkPayload =
                        (read == buffer.length)
                                ? buffer
                                : java.util.Arrays.copyOf(buffer, read);

                FrameEncoder.writeFrame(
                        out,
                        FrameOpcode.FILE_CHUNK,
                        chunkIndex,
                        chunkPayload
                );

                FrameHeader ack = FrameDecoder.readHeader(in);

                if (ack.opcode() != FrameOpcode.ACK
                        || ack.chunkIndex() != chunkIndex) {

                    throw new IOException(
                            "Chunk ACK mismatch for chunk "
                                    + chunkIndex
                    );
                }

                bytesSent += read;

                progress.setBytesTransferred(bytesSent);
                progress.setCurrentChunk(chunkIndex);

                progress.setPercentage(
                        (double) bytesSent * 100.0
                                / metadata.getFileSize()
                );

                long elapsed = Math.max(
                        1,
                        System.currentTimeMillis() - startTime
                );

                double speed =
                        ((double) (bytesSent - startOffset) / elapsed)
                                * 1000.0;

                progress.setSpeedBytesPerSec(speed);

                progress.setSpeedFormatted(
                        String.format(
                                "%.2f MB/s",
                                speed / (1024 * 1024)
                        )
                );

                long remainingBytes =
                        metadata.getFileSize() - bytesSent;

                progress.setEstimatedRemainingSeconds(
                        speed > 0 ? remainingBytes / speed : 0
                );

                if (chunkIndex % 5 == 0
                        || bytesSent == metadata.getFileSize()) {

                    notificationService.notifyTransferProgress(progress);
                }
            }
        }
    }

    /**
     * Requests cooperative cancellation.
     */
    public void cancelTransfer(String transferId) {

        TransferContext ctx = activeTransfers.get(transferId);

        if (ctx == null) {

            log.info(
                    "cancelTransfer ignored: no active transfer with id={}",
                    transferId
            );

            return;
        }

        ctx.cancel();

        log.info(
                "cancelTransfer requested for transferId={} (bytesTransferred={})",
                transferId,
                ctx.getProgress().getBytesTransferred()
        );
    }

    /**
     * Resumes an interrupted transfer using the SAME transferId.
     */
    public void resumeTransfer(String transferId) {

        TransferContext ctx = activeTransfers.get(transferId);

        if (ctx == null) {

            log.info(
                    "resumeTransfer ignored: no context for transferId={} (may have completed or been cleaned up)",
                    transferId
            );

            return;
        }

        long lastBytes =
                ctx.getProgress().getBytesTransferred();

        PeerDevice peer = ctx.getPeer();

        File file = ctx.getFile();

        log.info(
                "resumeTransfer requested for transferId={} (resuming from offset {})",
                transferId,
                lastBytes
        );

        // Reset cancellation flag.
        // Reuse the original transferId so the UI updates
        // the existing transfer progress card.
        ctx.clearCancelled();

        Thread.ofVirtual()
                .name("OmniDrop-Resume-" + transferId.substring(0, 8))
                .start(() -> {

                    try {

                        sendFileInternal(
                                transferId,
                                peer,
                                file,
                                lastBytes
                        );

                    } catch (TransferCancelledException ce) {

                        log.info(
                                "Resumed transfer {} cancelled again by user",
                                transferId
                        );

                    } catch (Exception ex) {

                        log.warn(
                                "Resumed transfer {} failed: {}",
                                transferId,
                                ex.getMessage()
                        );
                    }
                });
    }

    /**
     * Snapshot of an in-flight transfer.
     */
    private static final class TransferContext {

        private final String transferId;
        private final PeerDevice peer;
        private final File file;
        private final TransferProgress progress;

        private volatile boolean cancelled;

        TransferContext(
                String transferId,
                PeerDevice peer,
                File file,
                TransferProgress progress) {

            this.transferId = transferId;
            this.peer = peer;
            this.file = file;
            this.progress = progress;
        }

        boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            this.cancelled = true;
        }

        // NEW METHOD: Reset cancellation flag for resume.
        void clearCancelled() {
            this.cancelled = false;
        }

        PeerDevice getPeer() {
            return peer;
        }

        File getFile() {
            return file;
        }

        TransferProgress getProgress() {
            return progress;
        }

        String getTransferId() {
            return transferId;
        }
    }

    /**
     * Signals user-initiated cancellation.
     */
    public static class TransferCancelledException extends IOException {

        public TransferCancelledException(String message) {
            super(message);
        }
    }
}
