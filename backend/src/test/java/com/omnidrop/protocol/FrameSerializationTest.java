package com.omnidrop.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FrameSerializationTest {

    @Test
    @DisplayName("Should encode and decode a valid binary frame with CRC32")
    void testEncodeAndDecodeFrame() throws IOException {
        String message = "OmniDrop Network Programming Test Payload";
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);

        byte[] encodedFrame = FrameEncoder.encodeToBytes(FrameOpcode.FILE_CHUNK, 42, payload);

        // Header size (20) + payload length
        assertEquals(FrameConstants.HEADER_SIZE + payload.length, encodedFrame.length);

        ByteArrayInputStream bais = new ByteArrayInputStream(encodedFrame);
        FrameHeader header = FrameDecoder.readHeader(bais);

        assertEquals(FrameConstants.MAGIC, header.magic());
        assertTrue(header.isValidMagic());
        assertEquals(FrameConstants.PROTOCOL_VERSION, header.version());
        assertEquals(FrameOpcode.FILE_CHUNK, header.opcode());
        assertEquals(42, header.chunkIndex());
        assertEquals(payload.length, header.payloadLength());

        byte[] decodedPayload = FrameDecoder.readPayload(bais, header);
        assertArrayEquals(payload, decodedPayload);
        assertEquals(message, new String(decodedPayload, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Should reject frame with invalid magic number")
    void testInvalidMagicRejection() {
        byte[] invalidFrame = new byte[FrameConstants.HEADER_SIZE];
        invalidFrame[0] = 0x12; // Invalid magic
        invalidFrame[1] = 0x34;

        ByteArrayInputStream bais = new ByteArrayInputStream(invalidFrame);
        assertThrows(IOException.class, () -> FrameDecoder.readHeader(bais));
    }
}
