package com.omnidrop.protocol;

/**
 * 1-byte opcode identifying the intent of a TCP protocol frame.
 */
public enum FrameOpcode {
    HELLO((byte) 0x01),
    FILE_OFFER((byte) 0x02),
    ACCEPT((byte) 0x03),
    REJECT((byte) 0x04),
    FILE_CHUNK((byte) 0x05),
    ACK((byte) 0x06),
    RESUME_REQ((byte) 0x07),
    TRANSFER_COMP((byte) 0x08),
    ERROR((byte) 0x09);

    private final byte code;

    FrameOpcode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static FrameOpcode fromCode(byte code) {
        for (FrameOpcode op : values()) {
            if (op.code == code) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown FrameOpcode byte: 0x" + Integer.toHexString(code & 0xFF));
    }
}
