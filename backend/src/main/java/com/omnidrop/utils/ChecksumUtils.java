package com.omnidrop.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility functions for computing SHA-256 digests and file integrity verification.
 */
public final class ChecksumUtils {

    private ChecksumUtils() {}

    public static String calculateSha256(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return calculateSha256(is);
        }
    }

    public static String calculateSha256(InputStream is) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in standard JVM", e);
        }
    }
}
