package com.omnidrop.protocol;

/**
 * Protocol constants for OmniDrop Framed TCP Network Protocol.
 */
public final class FrameConstants {

    private FrameConstants() {}

    /** Magic 4-byte identifier: ASCII "OMNI" (0x4F, 0x4D, 0x4E, 0x49) */
    public static final int MAGIC = 0x4F4D4E49;

    /** Fixed length of the binary framing header in bytes */
    public static final int HEADER_SIZE = 20;

    /** Protocol Version 1 */
    public static final byte PROTOCOL_VERSION = 1;

    /** Default chunk size for streaming file slices (64 KB) */
    public static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    /** Maximum allowed single-frame payload to prevent heap exhaustion (16 MB) */
    public static final int MAX_FRAME_PAYLOAD_SIZE = 16 * 1024 * 1024;
}
