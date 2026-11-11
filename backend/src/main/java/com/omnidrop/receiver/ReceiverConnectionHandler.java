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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        File partFile = null;
        File finalFile = null;

        try (socket;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            socket.setSoTimeout(properties.getTransfer().getSocketTimeoutMs());

            // 1. Receive HELLO and reply with ACK
            FrameHeader helloHeader = FrameDecoder.readHeader(in);
            if (helloHeader.opcode() != FrameOpcode.HELLO) {
                FrameEncoder.writeFrame(out, FrameOpcode.ERROR, 0, "Expected HELLO".getBytes(StandardCharsets.UTF_8));
                return;
            }
            FrameDecoder.readPayload(in, helloHeader); // Consume HELLO payload
            FrameEncoder.writeFrame(out, FrameOpcode.ACK, 0, new byte[]{FrameConstants.PROTOCOL_VERSION});

            // 2. Receive FILE_OFFER or RESUME_REQ
            FrameHeader offerHeader = FrameDecoder.readHeader(in);
            byte[] offerBytes = FrameDecoder.readPayload(in, offerHeader);
            FileMetadata metadata = objectMapper.readValue(offerBytes, FileMetadata.class);

            // Safe path resolution (Security Defense against ../)
            File downloadsBase = new File(properties.getStorage().getDownloadsDir());
            Path safePath = PathSecurityUtils.resolveSafeDestinationPath(downloadsBase, metadata.getFileName());
            finalFile = safePath.toFile();
            // Ensure the downloads directory exists
            finalFile.getParentFile().mkdirs();
            partFile = new File(finalFile.getParentFile(), finalFile.getName() + ".part");

            progress.setTransferId(metadata.getTransferId());
            progress.setRole("RECEIVER");
            progress.setPeerIp(socket.getInetAddress().getHostAddress());
            progress.setFileName(metadata.getFileName());
            progress.setTotalBytes(metadata.getFileSize());
            progress.setTotalChunks(metadata.getTotalChunks());
            progress.setState(TransferState.TRANSFERRING);
            notificationService.notifyTransferStarted(progress);

            // Check if resumption checkpoint exists (Member 5 coordination)
            long startOffset = 0;
            if (partFile.exists() && metadata.getStartOffset() > 0) {
                startOffset = partFile.length();
            }

            // Accept transfer
            FrameEncoder.writeFrame(out, FrameOpcode.ACCEPT, 0, String.valueOf(startOffset).getBytes(StandardCharsets.UTF_8));

            // 3. Receive Chunks and write to disk
            long bytesReceived = startOffset;
            try (FileOutputStream fos = new FileOutputStream(partFile, startOffset > 0)) {
                while (true) {
                    FrameHeader frame = FrameDecoder.readHeader(in);

                    if (frame.opcode() == FrameOpcode.FILE_CHUNK) {
                        byte[] chunkData = FrameDecoder.readPayload(in, frame);
                        fos.write(chunkData);
                        bytesReceived += chunkData.length;

                        // Acknowledge chunk
                        FrameEncoder.writeFrame(out, FrameOpcode.ACK, frame.chunkIndex(), new byte[0]);

                        // Update progress
                        progress.setBytesTransferred(bytesReceived);
                        progress.setCurrentChunk(frame.chunkIndex());
                        progress.setPercentage((double) bytesReceived * 100.0 / metadata.getFileSize());

                        // Periodically save checkpoint
                        if (frame.chunkIndex() % 10 == 0) {
                            recoveryManager.saveCheckpoint(new Checkpoint(
                                    metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(),
                                    bytesReceived, frame.chunkIndex(), metadata.getSha256Checksum(), partFile.getAbsolutePath()
                            ));
                            notificationService.notifyTransferProgress(progress);
                        }

                    } else if (frame.opcode() == FrameOpcode.TRANSFER_COMP) {
                        FrameDecoder.readPayload(in, frame); // Consume TRANSFER_COMP payload
                        fos.flush();
                        break;
                    } else {
                        log.warn("Unexpected opcode during streaming: {}", frame.opcode());
                        break;
                    }
                }
            }

            // 4. Validate SHA-256 Checksum on completed file
            String actualSha256 = ChecksumUtils.calculateSha256(partFile);
            if (metadata.getSha256Checksum() != null && !metadata.getSha256Checksum().equalsIgnoreCase(actualSha256)) {
                log.error("Integrity check failed! Expected={}, Actual={}", metadata.getSha256Checksum(), actualSha256);
                FrameEncoder.writeFrame(out, FrameOpcode.ERROR, 0, "SHA256 mismatch".getBytes(StandardCharsets.UTF_8));
                progress.setState(TransferState.FAILED);
                progress.setErrorMessage("SHA-256 integrity verification failed");
                notificationService.notifyTransferFailed(progress);
                return;
            }

            // 5. Finalize: Atomic rename .part -> final filename
            Files.move(partFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            recoveryManager.deleteCheckpoint(metadata.getTransferId());

            FrameEncoder.writeFrame(out, FrameOpcode.ACK, metadata.getTotalChunks(), "VERIFIED".getBytes(StandardCharsets.UTF_8));

            progress.setState(TransferState.COMPLETED);
            progress.setBytesTransferred(metadata.getFileSize());
            progress.setPercentage(100.0);
            notificationService.notifyTransferCompleted(progress);
            log.info("Received and verified file '{}' ({} bytes)", finalFile.getName(), finalFile.length());

        } catch (Exception e) {
            log.error("Receiver connection interrupted: {}", e.getMessage());
            progress.setState(TransferState.INTERRUPTED);
            progress.setErrorMessage(e.getMessage());
            notificationService.notifyTransferInterrupted(progress);
        }
    }
}
