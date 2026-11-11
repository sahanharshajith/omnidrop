package com.omnidrop.protocol;

/**
 * 20-byte structured header prefixed to all TCP communications.
 */
public record FrameHeader(
        int magic,
        byte version,
        FrameOpcode opcode,
        short reserved,
        int chunkIndex,
        int payloadLength,
        long crc32
) {
    public boolean isValidMagic() {
        return magic == FrameConstants.MAGIC;
    }
}
