package com.omnidrop.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.Checkpoint;
import com.omnidrop.model.FileMetadata;
import com.omnidrop.protocol.FrameConstants;
import com.omnidrop.protocol.FrameDecoder;
import com.omnidrop.protocol.FrameEncoder;
import com.omnidrop.protocol.FrameHeader;
import com.omnidrop.protocol.FrameOpcode;
import com.omnidrop.recovery.TransferRecoveryManager;
import com.omnidrop.utils.ChecksumUtils;
import com.omnidrop.websocket.TransferWebSocketHandler;
import com.omnidrop.websocket.WebSocketEvent;
import com.omnidrop.websocket.WebSocketNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MEMBER 3: Runs the real ReceiverConnectionHandler against a fake sender
 * over a real TCP connection on localhost (no Mockito needed).
 */
class ReceiverConnectionHandlerTest {

    private static final int CHUNK = 65536;
    private static final String FILE_NAME = "sample.bin";

    @TempDir
    Path tempDir;

    private Path downloadsDir;
    private OmniDropProperties properties;
    private ObjectMapper mapper;
    private WebSocketNotificationService notifier;
    private TransferRecoveryManager recoveryManager;
    private ServerSocket server;

    /** Every WebSocket event the receiver tried to send, in order. */
    private final List<WebSocketEvent> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        downloadsDir = tempDir.resolve("downloads");

        properties = new OmniDropProperties();
        properties.getStorage().setDownloadsDir(downloadsDir.toString());
        properties.getStorage().setCheckpointsDir(tempDir.resolve("checkpoints").toString());
        properties.getTransfer().setSocketTimeoutMs(5000); // tests must not hang for 15 s

        mapper = new ObjectMapper();

        // A WebSocket handler that records events instead of sending them to browsers.
        TransferWebSocketHandler recordingHandler = new TransferWebSocketHandler(null, null) {
            @Override
            public synchronized void broadcast(WebSocketEvent event) {
                events.add(event);
            }
        };
        notifier = new WebSocketNotificationService(recordingHandler);

        // The receiver only calls saveCheckpoint and deleteCheckpoint.
        // Here both do nothing, so we do not need Member 5's CheckpointStore.
        recoveryManager = new TransferRecoveryManager(null) {
            @Override
            public void saveCheckpoint(Checkpoint checkpoint) {
                // ignored in tests
            }

            @Override
            public void deleteCheckpoint(String transferId) {
                // ignored in tests
            }
        };

        // Port 0 = "any free port"
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    // ===============================================================
    // Tests
    // ===============================================================

    @Test
    void fullTransferSavesVerifiedFile() throws Exception {
        byte[] data = makeData(200_000);
        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-full", FILE_NAME, data, 0));
            Reply accept = sender.readReply();
            assertEquals(FrameOpcode.ACCEPT, accept.opcode());
            assertEquals("0", accept.text());

            sender.sendRange(data, 0, data.length);
            sender.sendFrame(FrameOpcode.TRANSFER_COMP, 4, new byte[0]);

            Reply done = sender.readReply();
            assertEquals(FrameOpcode.ACK, done.opcode());
            assertEquals("VERIFIED", done.text());
        }
        join(receiver);

        assertArrayEquals(data, Files.readAllBytes(downloadsDir.resolve(FILE_NAME)));
        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME + ".part")), ".part file should be renamed");
        assertTrue(eventTypes().contains("TRANSFER_STARTED"));
        assertTrue(eventTypes().contains("TRANSFER_COMPLETED"));
    }

    @Test
    void traversalFileNameIsSavedInsideDownloads() throws Exception {
        byte[] data = makeData(1000);
        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-evil", "../../evil.txt", data, 0));
            assertEquals(FrameOpcode.ACCEPT, sender.readReply().opcode());

            sender.sendRange(data, 0, data.length);
            sender.sendFrame(FrameOpcode.TRANSFER_COMP, 1, new byte[0]);
            assertEquals("VERIFIED", sender.readReply().text());
        }
        join(receiver);

        assertTrue(Files.exists(downloadsDir.resolve("evil.txt")), "File should be inside downloads");
        assertFalse(Files.exists(tempDir.resolve("evil.txt")), "File must not escape one level up");
        assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")), "File must not escape two levels up");
    }

    @Test
    void corruptedChunkInterruptsTransfer() throws Exception {
        byte[] data = makeData(5000);
        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-corrupt", FILE_NAME, data, 0));
            assertEquals(FrameOpcode.ACCEPT, sender.readReply().opcode());

            // Build a valid chunk, then flip one bit in its payload so the CRC no longer matches
            byte[] frame = FrameEncoder.encodeToBytes(FrameOpcode.FILE_CHUNK, 1, data);
            frame[FrameConstants.HEADER_SIZE] ^= 0x01;
            sender.sendRaw(frame);
        }
        join(receiver);

        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME)), "Corrupted file must not be finalized");
        assertTrue(eventTypes().contains("TRANSFER_INTERRUPTED"));
        assertFalse(eventTypes().contains("TRANSFER_COMPLETED"));
    }

    @Test
    void wrongSha256IsReportedAndPartFileDeleted() throws Exception {
        byte[] data = makeData(10_000);
        FileMetadata badOffer = offer("t-badsha", FILE_NAME, data, 0);
        badOffer.setSha256Checksum("0".repeat(64)); // wrong on purpose

        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(badOffer);
            assertEquals(FrameOpcode.ACCEPT, sender.readReply().opcode());

            sender.sendRange(data, 0, data.length);
            sender.sendFrame(FrameOpcode.TRANSFER_COMP, 1, new byte[0]);

            Reply reply = sender.readReply();
            assertEquals(FrameOpcode.ERROR, reply.opcode());
            assertEquals("SHA256 mismatch", reply.text());
        }
        join(receiver);

        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME)));
        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME + ".part")), "Bad .part must be deleted");
        assertTrue(eventTypes().contains("TRANSFER_FAILED"));
    }

    @Test
    void disconnectKeepsPartFile() throws Exception {
        byte[] data = makeData(200_000);
        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-drop", FILE_NAME, data, 0));
            assertEquals(FrameOpcode.ACCEPT, sender.readReply().opcode());

            sender.sendRange(data, 0, 2 * CHUNK); // two chunks, then the connection "drops"
        }
        join(receiver);

        Path part = downloadsDir.resolve(FILE_NAME + ".part");
        assertTrue(Files.exists(part), "Partial data must be kept for resume");
        assertEquals(2L * CHUNK, Files.size(part));
        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME)));
        assertTrue(eventTypes().contains("TRANSFER_INTERRUPTED"));
    }

    @Test
    void resumeContinuesFromPartFile() throws Exception {
        byte[] data = makeData(200_000);
        int resumeOffset = 2 * CHUNK;

        // ---- First connection: send 2 chunks, then drop ----
        Thread first = startReceiver();
        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-resume", FILE_NAME, data, 0));
            assertEquals(FrameOpcode.ACCEPT, sender.readReply().opcode());
            sender.sendRange(data, 0, resumeOffset);
        }
        join(first);
        assertEquals(resumeOffset, Files.size(downloadsDir.resolve(FILE_NAME + ".part")));

        // ---- Second connection: same transferId, resume from the offset ----
        Thread second = startReceiver();
        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-resume", FILE_NAME, data, resumeOffset));

            Reply accept = sender.readReply();
            assertEquals(FrameOpcode.ACCEPT, accept.opcode());
            assertEquals(String.valueOf(resumeOffset), accept.text());

            sender.sendRange(data, resumeOffset, data.length);
            sender.sendFrame(FrameOpcode.TRANSFER_COMP, 4, new byte[0]);
            assertEquals("VERIFIED", sender.readReply().text());
        }
        join(second);

        assertArrayEquals(data, Files.readAllBytes(downloadsDir.resolve(FILE_NAME)));
        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME + ".part")));
        assertTrue(eventTypes().contains("TRANSFER_COMPLETED"));
    }

    @Test
    void resumeWithoutPartFileIsRejected() throws Exception {
        byte[] data = makeData(200_000);
        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-nopart", FILE_NAME, data, 100_000)); // no .part exists

            Reply reply = sender.readReply();
            assertEquals(FrameOpcode.REJECT, reply.opcode());
        }
        join(receiver);

        assertTrue(eventTypes().contains("TRANSFER_FAILED"));
        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME)));
    }

    @Test
    void senderSendingTooManyBytesIsStopped() throws Exception {
        byte[] offered = makeData(100);
        byte[] tooMuch = makeData(500);
        Thread receiver = startReceiver();

        try (FakeSender sender = new FakeSender(server.getLocalPort())) {
            sender.hello();
            sender.sendOffer(offer("t-big", FILE_NAME, offered, 0));
            assertEquals(FrameOpcode.ACCEPT, sender.readReply().opcode());

            sender.sendFrame(FrameOpcode.FILE_CHUNK, 1, tooMuch); // more than the 100 bytes promised
        }
        join(receiver);

        assertFalse(Files.exists(downloadsDir.resolve(FILE_NAME)));
        assertTrue(eventTypes().contains("TRANSFER_INTERRUPTED"));
    }

    // ===============================================================
    // Helpers
    // ===============================================================

    /** Starts the real handler on one accepted connection, in its own thread. */
    private Thread startReceiver() {
        Thread thread = new Thread(() -> {
            try {
                Socket socket = server.accept();
                new ReceiverConnectionHandler(socket, properties, notifier, recoveryManager, mapper).run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "test-receiver");
        thread.start();
        return thread;
    }

    private void join(Thread thread) throws InterruptedException {
        thread.join(10_000);
        assertFalse(thread.isAlive(), "Receiver thread did not finish in time");
    }

    private List<String> eventTypes() {
        return events.stream().map(WebSocketEvent::getType).toList();
    }

    /** Same bytes every run, so failures are repeatable. */
    private static byte[] makeData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }

    private static FileMetadata offer(String transferId, String fileName, byte[] data, long startOffset)
            throws IOException {
        String sha = ChecksumUtils.calculateSha256(new ByteArrayInputStream(data));
        FileMetadata metadata = new FileMetadata(transferId, fileName, data.length, CHUNK, sha);
        metadata.setStartOffset(startOffset);
        return metadata;
    }

    private record Reply(FrameOpcode opcode, int chunkIndex, byte[] payload) {
        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    /** A tiny fake "sender" that speaks the OmniDrop protocol. */
    private class FakeSender implements AutoCloseable {
        private final Socket socket;
        private final BufferedInputStream in;
        private final BufferedOutputStream out;

        FakeSender(int port) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), port);
            socket.setSoTimeout(5000);
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());
        }

        void hello() throws IOException {
            sendFrame(FrameOpcode.HELLO, 0, new byte[]{FrameConstants.PROTOCOL_VERSION});
            assertEquals(FrameOpcode.ACK, readReply().opcode());
        }

        void sendOffer(FileMetadata metadata) throws IOException {
            sendFrame(FrameOpcode.FILE_OFFER, 0, mapper.writeValueAsBytes(metadata));
        }

        void sendFrame(FrameOpcode opcode, int chunkIndex, byte[] payload) throws IOException {
            FrameEncoder.writeFrame(out, opcode, chunkIndex, payload);
        }

        void sendRaw(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        /** Sends data[from, to) in 64 KB chunks and checks every ACK. */
        void sendRange(byte[] data, int from, int to) throws IOException {
            int chunkIndex = from / CHUNK + 1;
            int position = from;
            while (position < to) {
                int length = Math.min(CHUNK, to - position);
                sendFrame(FrameOpcode.FILE_CHUNK, chunkIndex, Arrays.copyOfRange(data, position, position + length));

                Reply ack = readReply();
                assertEquals(FrameOpcode.ACK, ack.opcode());
                assertEquals(chunkIndex, ack.chunkIndex());

                position += length;
                chunkIndex++;
            }
        }

        Reply readReply() throws IOException {
            FrameHeader header = FrameDecoder.readHeader(in);
            byte[] payload = FrameDecoder.readPayload(in, header);
            return new Reply(header.opcode(), header.chunkIndex(), payload);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}