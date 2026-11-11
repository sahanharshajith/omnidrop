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
import java.util.UUID;

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
 * ============================================================================
 */
@Service
public class TcpFileSenderService {

    private static final Logger log = LoggerFactory.getLogger(TcpFileSenderService.class);

    private final OmniDropProperties properties;
    private final WebSocketNotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TcpFileSenderService(OmniDropProperties properties, WebSocketNotificationService notificationService) {
        this.properties = properties;
        this.notificationService = notificationService;
    }

    /**
     * Connects to a target peer over TCP and streams the file chunk by chunk.
     *
     * @param targetPeer the receiving device
     * @param file the local file to send
     * @param startOffset byte offset if resuming, or 0 for fresh transfer
     * @return TransferProgress indicating final completion status
     */
    public TransferProgress sendFile(PeerDevice targetPeer, File file, long startOffset) throws IOException {
        String transferId = UUID.randomUUID().toString();
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
        notificationService.notifyTransferStarted(progress);

        log.info("Initiating TCP connection to {}:{} for file '{}' ({} bytes)",
                targetPeer.getIpAddress(), targetPeer.getTcpPort(), file.getName(), fileSize);

        // Core Java Networking: Client Socket
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetPeer.getIpAddress(), targetPeer.getTcpPort()),
                    properties.getTransfer().getSocketTimeoutMs());
            socket.setSoTimeout(properties.getTransfer().getSocketTimeoutMs());

            try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                // Step 1: Handshake HELLO
                FrameEncoder.writeFrame(out, FrameOpcode.HELLO, 0, new byte[]{FrameConstants.PROTOCOL_VERSION});
                FrameHeader helloAck = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, helloAck); // Consume HELLO ACK payload
                if (helloAck.opcode() != FrameOpcode.ACK) {
                    throw new IOException("Handshake failed, receiver sent opcode: " + helloAck.opcode());
                }

                // Step 2: Send FILE_OFFER with JSON metadata
                byte[] offerPayload = objectMapper.writeValueAsString(metadata).getBytes(StandardCharsets.UTF_8);
                FrameEncoder.writeFrame(out, FrameOpcode.FILE_OFFER, 0, offerPayload);

                // Step 3: Wait for ACCEPT or REJECT
                FrameHeader response = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, response); // Consume response payload
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
                streamChunks(file, metadata, startOffset, out, in, progress);

                // Step 5: Send TRANSFER_COMP
                FrameEncoder.writeFrame(out, FrameOpcode.TRANSFER_COMP, metadata.getTotalChunks(), new byte[0]);
                FrameHeader compAck = FrameDecoder.readHeader(in);
                FrameDecoder.readPayload(in, compAck); // Consume completion ACK payload

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
        } catch (Exception e) {
            log.error("TCP transfer failed to {}: {}", targetPeer.getDeviceName(), e.getMessage());
            progress.setState(TransferState.INTERRUPTED);
            progress.setErrorMessage(e.getMessage());
            notificationService.notifyTransferInterrupted(progress);
            throw new IOException("TCP file transfer failed: " + e.getMessage(), e);
        }

        return progress;
    }

    private void streamChunks(File file, FileMetadata metadata, long startOffset,
                              BufferedOutputStream out, BufferedInputStream in,
                              TransferProgress progress) throws IOException {
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
                chunkIndex++;
                byte[] chunkPayload = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);

                // Write framed chunk over TCP
                FrameEncoder.writeFrame(out, FrameOpcode.FILE_CHUNK, chunkIndex, chunkPayload);

                // Read receiver ACK for chunk
                FrameHeader ack = FrameDecoder.readHeader(in);
                if (ack.opcode() != FrameOpcode.ACK || ack.chunkIndex() != chunkIndex) {
                    throw new IOException("Chunk ACK mismatch for chunk " + chunkIndex);
                }

                bytesSent += read;
                progress.setBytesTransferred(bytesSent);
                progress.setCurrentChunk(chunkIndex);
                progress.setPercentage((double) bytesSent * 100.0 / metadata.getFileSize());

                // Calculate speed and ETA
                long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                double speed = ((double) (bytesSent - startOffset) / elapsed) * 1000.0; // bytes/sec
                progress.setSpeedBytesPerSec(speed);
                progress.setSpeedFormatted(String.format("%.2f MB/s", speed / (1024 * 1024)));

                long remainingBytes = metadata.getFileSize() - bytesSent;
                progress.setEstimatedRemainingSeconds(speed > 0 ? remainingBytes / speed : 0);

                // Publish progress every 5 chunks or at completion
                if (chunkIndex % 5 == 0 || bytesSent == metadata.getFileSize()) {
                    notificationService.notifyTransferProgress(progress);
                }
            }
        }
    }
}
