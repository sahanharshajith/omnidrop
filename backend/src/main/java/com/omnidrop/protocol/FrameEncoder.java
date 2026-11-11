package com.omnidrop.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

/**
 * Encodes protocol frames into on-the-wire binary bytes according to the specification.
 */
public final class FrameEncoder {

    private FrameEncoder() {}

    /**
     * Writes a complete framed message to the provided TCP OutputStream.
     */
    public static void writeFrame(OutputStream out, FrameOpcode opcode, int chunkIndex, byte[] payload) throws IOException {
        int length = (payload != null) ? payload.length : 0;
        long crc = 0L;
        if (payload != null && payload.length > 0) {
            CRC32 crc32 = new CRC32();
            crc32.update(payload);
            crc = crc32.getValue();
        }

        DataOutputStream dos = new DataOutputStream(out);
        // 1. Magic (4 bytes)
        dos.writeInt(FrameConstants.MAGIC);
        // 2. Version (1 byte)
        dos.writeByte(FrameConstants.PROTOCOL_VERSION);
        // 3. Opcode (1 byte)
        dos.writeByte(opcode.getCode());
        // 4. Reserved (2 bytes)
        dos.writeShort(0);
        // 5. Chunk Index (4 bytes)
        dos.writeInt(chunkIndex);
        // 6. Payload Length (4 bytes)
        dos.writeInt(length);
        // 7. CRC32 Checksum (4 bytes stored as int)
        dos.writeInt((int) crc);

        // 8. Payload Data
        if (length > 0) {
            dos.write(payload);
        }
        dos.flush();
    }

    /**
     * Serializes a frame into an in-memory byte array (useful for testing and buffer pipelines).
     */
    public static byte[] encodeToBytes(FrameOpcode opcode, int chunkIndex, byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(FrameConstants.HEADER_SIZE + (payload != null ? payload.length : 0));
        writeFrame(baos, opcode, chunkIndex, payload);
        return baos.toByteArray();
    }
}
