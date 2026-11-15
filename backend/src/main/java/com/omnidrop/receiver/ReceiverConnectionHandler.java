package com.omnidrop.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.Checkpoint;
import com.omnidrop.model.FileMetadata;
import com.omnidrop.model.TransferProgress;
import com.omnidrop.model.TransferState;
import com.omnidrop.protocol.FrameConstants;
import com.omnidrop.protocol.FrameDecoder;
import com.omnidrop.protocol.FrameEncoder;
import com.omnidrop.protocol.FrameHeader;
import com.omnidrop.protocol.FrameOpcode;
import com.omnidrop.recovery.TransferRecoveryManager;
import com.omnidrop.utils.ChecksumUtils;
import com.omnidrop.utils.PathSecurityUtils;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * MEMBER 3: Handles an individual accepted client TCP socket connection.
 */
public class ReceiverConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReceiverConnectionHandler.class);

    private final Socket socket;
    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final TransferRecoveryManager recoveryManager;
    private final ObjectMapper objectMapper;

    public ReceiverConnectionHandler(Socket socket,
                                     OmniDropProperties properties,
                                     WebSocketNotificationService notificationService,
                                     TransferRecoveryManager recoveryManager,
                                     ObjectMapper objectMapper) {
        this.socket = socket;
        this.properties = properties;
        this.notificationService = notificationService;
        this.recoveryManager = recoveryManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run() {
        TransferProgress progress = new TransferProgress();

        try (socket;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            socket.setSoTimeout(properties.getTransfer().getSocketTimeoutMs());

            // ---------------------------------------------------------------
            // 1. HELLO -> reply ACK
            // ---------------------------------------------------------------
            FrameHeader helloHeader = FrameDecoder.readHeader(in);
            if (helloHeader.opcode() != FrameOpcode.HELLO) {
                FrameEncoder.writeFrame(out, FrameOpcode.ERROR, 0, "Expected HELLO".getBytes(StandardCharsets.UTF_8));
                return;
            }
            FrameDecoder.readPayload(in, helloHeader);
            FrameEncoder.writeFrame(out, FrameOpcode.ACK, 0, new byte[]{FrameConstants.PROTOCOL_VERSION});

            // ---------------------------------------------------------------
            // 2. FILE_OFFER (or RESUME_REQ) -> read the file details
            // ---------------------------------------------------------------
            FrameHeader offerHeader = FrameDecoder.readHeader(in);
            byte[] offerBytes = FrameDecoder.readPayload(in, offerHeader);
            if (offerHeader.opcode() != FrameOpcode.FILE_OFFER
                    && offerHeader.opcode() != FrameOpcode.RESUME_REQ) {
                FrameEncoder.writeFrame(out, FrameOpcode.ERROR, 0, "Expected FILE_OFFER".getBytes(StandardCharsets.UTF_8));
                return;
            }
            FileMetadata metadata = objectMapper.readValue(offerBytes, FileMetadata.class);

            progress.setTransferId(metadata.getTransferId());
            progress.setRole("RECEIVER");
            progress.setPeerIp(socket.getInetAddress().getHostAddress());
            progress.setFileName(metadata.getFileName());
            progress.setTotalBytes(metadata.getFileSize());
            progress.setTotalChunks(metadata.getTotalChunks());
            progress.setState(TransferState.TRANSFERRING);
            notificationService.notifyTransferStarted(progress);

            // Basic sanity check on the numbers the sender gave us
            long requestedOffset = metadata.getStartOffset();
            if (metadata.getFileSize() < 0 || requestedOffset < 0 || requestedOffset > metadata.getFileSize()) {
                reject(out, progress, "Invalid file size or start offset");
                return;
            }

            // ---------------------------------------------------------------
            // 3. Decide where to save (safe path, never overwrite)
            // ---------------------------------------------------------------
            File downloadsBase = new File(properties.getStorage().getDownloadsDir());
            Path safePath = PathSecurityUtils.resolveUniqueDestinationPath(downloadsBase, metadata.getFileName());
            File finalFile = safePath.toFile();
            File partFile = new File(finalFile.getParentFile(), finalFile.getName() + ".part");

            // Is there enough free disk space for the bytes still to come?
            long bytesStillNeeded = metadata.getFileSize() - requestedOffset;
            if (finalFile.getParentFile().getUsableSpace() < bytesStillNeeded) {
                reject(out, progress, "Not enough disk space on receiver");
                return;
            }

            // ---------------------------------------------------------------
            // 4. Resume handling: sender and receiver must agree on the offset
            // ---------------------------------------------------------------
            long startOffset = 0;
            if (requestedOffset > 0) {
                if (partFile.exists() && partFile.length() >= requestedOffset) {
                    // Cut off any extra bytes so both sides stop at the same place
                    try (FileChannel channel = FileChannel.open(partFile.toPath(), StandardOpenOption.WRITE)) {
                        channel.truncate(requestedOffset);
                    }
                    startOffset = requestedOffset;
                } else {
                    reject(out, progress, "Cannot resume: partial file missing or too short");
                    return;
                }
            }

            FrameEncoder.writeFrame(out, FrameOpcode.ACCEPT, 0, String.valueOf(startOffset).getBytes(StandardCharsets.UTF_8));

            // ---------------------------------------------------------------
            // 5. Receive chunks and write them straight to the .part file
            // ---------------------------------------------------------------
            long bytesReceived = startOffset;
            long startTime = System.currentTimeMillis();
            boolean finished = false;

            try (FileOutputStream fos = new FileOutputStream(partFile, startOffset > 0)) {
                while (!finished) {
                    FrameHeader frame = FrameDecoder.readHeader(in);

                    if (frame.opcode() == FrameOpcode.FILE_CHUNK) {
                        byte[] chunkData = FrameDecoder.readPayload(in, frame);
                        bytesReceived += chunkData.length;

                        // Never accept more than the sender promised
                        if (bytesReceived > metadata.getFileSize()) {
                            throw new IOException("Sender sent more bytes than offered");
                        }

                        fos.write(chunkData);
                        FrameEncoder.writeFrame(out, FrameOpcode.ACK, frame.chunkIndex(), new byte[0]);

                        progress.setBytesTransferred(bytesReceived);
                        progress.setCurrentChunk(frame.chunkIndex());
                        progress.setPercentage(metadata.getFileSize() > 0
                                ? (double) bytesReceived * 100.0 / metadata.getFileSize()
                                : 100.0);

                        if (frame.chunkIndex() % 10 == 0) {
                            // Speed and time left
                            long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                            double speed = ((double) (bytesReceived - startOffset) / elapsed) * 1000.0;
                            progress.setSpeedBytesPerSec(speed);
                            progress.setSpeedFormatted(String.format("%.2f MB/s", speed / (1024 * 1024)));
                            progress.setEstimatedRemainingSeconds(
                                    speed > 0 ? (metadata.getFileSize() - bytesReceived) / speed : 0);

                            recoveryManager.saveCheckpoint(new Checkpoint(
                                    metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(),
                                    bytesReceived, frame.chunkIndex(), metadata.getSha256Checksum(),
                                    partFile.getAbsolutePath()
                            ));
                            notificationService.notifyTransferProgress(progress);
                        }

                    } else if (frame.opcode() == FrameOpcode.TRANSFER_COMP) {
                        FrameDecoder.readPayload(in, frame);
                        finished = true;

                    } else {
                        // Anything else here is a protocol error: treat as interrupted
                        throw new IOException("Unexpected opcode during streaming: " + frame.opcode());
                    }
                }
            } // fos is closed here, so the file is fully written before we check it

            // ---------------------------------------------------------------
            // 6. Verify size and SHA-256
            // ---------------------------------------------------------------
            if (bytesReceived != metadata.getFileSize()) {
                throw new IOException("Incomplete file: got " + bytesReceived
                        + " of " + metadata.getFileSize() + " bytes");
            }

            String actualSha256 = ChecksumUtils.calculateSha256(partFile);
            if (metadata.getSha256Checksum() != null
                    && !metadata.getSha256Checksum().equalsIgnoreCase(actualSha256)) {
                log.error("Integrity check failed! Expected={}, Actual={}", metadata.getSha256Checksum(), actualSha256);

                // Remove the bad file so it cannot be resumed or opened by mistake
                Files.deleteIfExists(partFile.toPath());
                recoveryManager.deleteCheckpoint(metadata.getTransferId());

                FrameEncoder.writeFrame(out, FrameOpcode.ERROR, 0, "SHA256 mismatch".getBytes(StandardCharsets.UTF_8));
                progress.setState(TransferState.FAILED);
                progress.setErrorMessage("SHA-256 integrity verification failed");
                notificationService.notifyTransferFailed(progress);
                return;
            }

            // ---------------------------------------------------------------
            // 7. Finish: rename .part -> final name (will not overwrite)
            // ---------------------------------------------------------------
            Files.move(partFile.toPath(), finalFile.toPath());
            recoveryManager.deleteCheckpoint(metadata.getTransferId());

            FrameEncoder.writeFrame(out, FrameOpcode.ACK, metadata.getTotalChunks(), "VERIFIED".getBytes(StandardCharsets.UTF_8));

            progress.setState(TransferState.COMPLETED);
            progress.setBytesTransferred(metadata.getFileSize());
            progress.setPercentage(100.0);
            notificationService.notifyTransferCompleted(progress);
            log.info("Received and verified file '{}' ({} bytes)", finalFile.getName(), finalFile.length());

        } catch (Exception e) {
            log.error("Receiver connection interrupted: {}", e.getMessage());
            // If we never learned the transferId, there is no card in the UI to update
            if (progress.getTransferId() != null) {
                progress.setState(TransferState.INTERRUPTED);
                progress.setErrorMessage(e.getMessage());
                notificationService.notifyTransferInterrupted(progress);
            }
        }
    }

    /** Tells the sender "no" (with a reason) and shows the failure in the UI. */
    private void reject(BufferedOutputStream out, TransferProgress progress, String reason) throws IOException {
        FrameEncoder.writeFrame(out, FrameOpcode.REJECT, 0, reason.getBytes(StandardCharsets.UTF_8));
        progress.setState(TransferState.FAILED);
        progress.setErrorMessage(reason);
        notificationService.notifyTransferFailed(progress);
        log.warn("Rejected transfer {}: {}", progress.getTransferId(), reason);
    }
}