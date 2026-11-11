package com.omnidrop.protocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.util.zip.CRC32;

/**
 * Decodes protocol frames from an incoming TCP InputStream with deterministic header alignment.
 */
public final class FrameDecoder {

    private FrameDecoder() {}

    /**
     * Reads exactly the 20-byte header from the input stream.
     */
    public static FrameHeader readHeader(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        int magic = dis.readInt();
        if (magic != FrameConstants.MAGIC) {
            throw new ProtocolException("Invalid frame magic: 0x" + Integer.toHexString(magic) + " (expected 'OMNI')");
        }

        byte version = dis.readByte();
        byte opcodeByte = dis.readByte();
        FrameOpcode opcode = FrameOpcode.fromCode(opcodeByte);
        short reserved = dis.readShort();
        int chunkIndex = dis.readInt();
        int payloadLength = dis.readInt();

        if (payloadLength < 0 || payloadLength > FrameConstants.MAX_FRAME_PAYLOAD_SIZE) {
            throw new ProtocolException("Illegal payload length in frame: " + payloadLength);
        }

        long crc32 = dis.readInt() & 0xFFFFFFFFL;

        return new FrameHeader(magic, version, opcode, reserved, chunkIndex, payloadLength, crc32);
    }

    /**
     * Reads the exact payload bytes specified in the header and verifies CRC32.
     */
    public static byte[] readPayload(InputStream in, FrameHeader header) throws IOException {
        int length = header.payloadLength();
        if (length == 0) {
            return new byte[0];
        }

        byte[] buffer = new byte[length];
        DataInputStream dis = new DataInputStream(in);
        dis.readFully(buffer);

        // Validate CRC32
        CRC32 crc = new CRC32();
        crc.update(buffer);
        long computed = crc.getValue();
        if (computed != header.crc32()) {
            throw new ProtocolException("Frame CRC32 checksum mismatch: computed=" + computed + ", header=" + header.crc32());
        }

        return buffer;
    }
}
