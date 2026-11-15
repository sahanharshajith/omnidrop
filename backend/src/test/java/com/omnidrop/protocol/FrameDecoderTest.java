package com.omnidrop.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MEMBER 3: Tests for reading frames from a TCP stream.
 */
class FrameDecoderTest {

    private static byte[] sampleFrame(FrameOpcode opcode, int chunkIndex, byte[] payload) throws IOException {
        return FrameEncoder.encodeToBytes(opcode, chunkIndex, payload);
    }

    /** Builds a header by hand so we can write invalid values. */
    private static byte[] rawHeader(int magic, int opcode, int chunkIndex, int length, int crc) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(magic);
        out.writeByte(1);       // version
        out.writeByte(opcode);
        out.writeShort(0);      // reserved
        out.writeInt(chunkIndex);
        out.writeInt(length);
        out.writeInt(crc);
        return bytes.toByteArray();
    }

    // ---------------------------------------------------------------
    // Good frames
    // ---------------------------------------------------------------

    @Test
    void encodedFrameIsDecodedExactly() throws IOException {
        byte[] payload = "hello omnidrop".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = sampleFrame(FrameOpcode.FILE_CHUNK, 7, payload);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        FrameHeader header = FrameDecoder.readHeader(in);
        byte[] decoded = FrameDecoder.readPayload(in, header);

        assertEquals(FrameConstants.MAGIC, header.magic());
        assertEquals(FrameConstants.PROTOCOL_VERSION, header.version());
        assertEquals(FrameOpcode.FILE_CHUNK, header.opcode());
        assertEquals(7, header.chunkIndex());
        assertEquals(payload.length, header.payloadLength());

        CRC32 crc = new CRC32();
        crc.update(payload);
        assertEquals(crc.getValue(), header.crc32());

        assertArrayEquals(payload, decoded);
    }

    @Test
    void frameStartsWithOmniAndHasTwentyByteHeader() throws IOException {
        byte[] payload = new byte[10];
        byte[] bytes = sampleFrame(FrameOpcode.ACK, 0, payload);

        assertEquals(FrameConstants.HEADER_SIZE + payload.length, bytes.length);
        assertEquals("OMNI", new String(Arrays.copyOfRange(bytes, 0, 4), StandardCharsets.US_ASCII));
    }

    @Test
    void emptyPayloadReturnsEmptyArray() throws IOException {
        byte[] bytes = sampleFrame(FrameOpcode.ACK, 3, new byte[0]);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        FrameHeader header = FrameDecoder.readHeader(in);
        byte[] payload = FrameDecoder.readPayload(in, header);

        assertEquals(0, header.payloadLength());
        assertEquals(0, payload.length);
    }

    @Test
    void severalFramesInOneStreamAreReadInOrder() throws IOException {
        // TCP has no message borders: frames arrive back to back.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(sampleFrame(FrameOpcode.HELLO, 0, new byte[]{1}));
        stream.write(sampleFrame(FrameOpcode.FILE_CHUNK, 1, new byte[]{10, 20, 30}));
        stream.write(sampleFrame(FrameOpcode.TRANSFER_COMP, 2, new byte[0]));

        ByteArrayInputStream in = new ByteArrayInputStream(stream.toByteArray());

        FrameHeader h1 = FrameDecoder.readHeader(in);
        assertArrayEquals(new byte[]{1}, FrameDecoder.readPayload(in, h1));
        assertEquals(FrameOpcode.HELLO, h1.opcode());

        FrameHeader h2 = FrameDecoder.readHeader(in);
        assertArrayEquals(new byte[]{10, 20, 30}, FrameDecoder.readPayload(in, h2));
        assertEquals(FrameOpcode.FILE_CHUNK, h2.opcode());

        FrameHeader h3 = FrameDecoder.readHeader(in);
        assertEquals(0, FrameDecoder.readPayload(in, h3).length);
        assertEquals(FrameOpcode.TRANSFER_COMP, h3.opcode());
    }

    // ---------------------------------------------------------------
    // Bad frames
    // ---------------------------------------------------------------

    @Test
    void wrongMagicBytesAreRejected() throws IOException {
        byte[] bytes = sampleFrame(FrameOpcode.ACK, 0, new byte[0]);
        bytes[0] = 0x00; // break the "O" of OMNI

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        assertThrows(ProtocolException.class, () -> FrameDecoder.readHeader(in));
    }

    @Test
    void corruptedPayloadFailsCrcCheck() throws IOException {
        byte[] bytes = sampleFrame(FrameOpcode.FILE_CHUNK, 1, "some file data".getBytes(StandardCharsets.UTF_8));
        bytes[FrameConstants.HEADER_SIZE] ^= 0x01; // flip one bit in the payload

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        FrameHeader header = FrameDecoder.readHeader(in);

        assertThrows(ProtocolException.class, () -> FrameDecoder.readPayload(in, header));
    }

    @Test
    void payloadLengthAboveLimitIsRejectedBeforeAllocating() throws IOException {
        byte[] bytes = rawHeader(FrameConstants.MAGIC, FrameOpcode.FILE_CHUNK.getCode(), 1,
                FrameConstants.MAX_FRAME_PAYLOAD_SIZE + 1, 0);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        assertThrows(ProtocolException.class, () -> FrameDecoder.readHeader(in));
    }

    @Test
    void negativePayloadLengthIsRejected() throws IOException {
        byte[] bytes = rawHeader(FrameConstants.MAGIC, FrameOpcode.FILE_CHUNK.getCode(), 1, -5, 0);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        assertThrows(ProtocolException.class, () -> FrameDecoder.readHeader(in));
    }

    @Test
    void truncatedPayloadThrowsEndOfStream() throws IOException {
        byte[] bytes = sampleFrame(FrameOpcode.FILE_CHUNK, 1, new byte[100]);
        byte[] cutOff = Arrays.copyOf(bytes, FrameConstants.HEADER_SIZE + 50); // only half the payload

        ByteArrayInputStream in = new ByteArrayInputStream(cutOff);
        FrameHeader header = FrameDecoder.readHeader(in);

        assertThrows(EOFException.class, () -> FrameDecoder.readPayload(in, header));
    }

    @Test
    void truncatedHeaderThrowsEndOfStream() throws IOException {
        byte[] bytes = sampleFrame(FrameOpcode.ACK, 0, new byte[0]);
        byte[] cutOff = Arrays.copyOf(bytes, 10); // only half the header

        ByteArrayInputStream in = new ByteArrayInputStream(cutOff);

        assertThrows(EOFException.class, () -> FrameDecoder.readHeader(in));
    }

    @Test
    void unknownOpcodeIsRejected() throws IOException {
        byte[] bytes = rawHeader(FrameConstants.MAGIC, 0x7F, 0, 0, 0);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        // FrameOpcode.fromCode throws IllegalArgumentException for unknown codes
        assertThrows(IllegalArgumentException.class, () -> FrameDecoder.readHeader(in));
    }

    @Test
    void decoderReadsHeaderSizeBytesOnly() throws IOException {
        byte[] bytes = sampleFrame(FrameOpcode.ACK, 0, new byte[]{9, 9});
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        FrameDecoder.readHeader(in);

        // Exactly the 20 header bytes were consumed; the 2 payload bytes remain.
        assertTrue(in.available() == 2);
    }
}