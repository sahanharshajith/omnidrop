package com.omnidrop.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Defensive utility to prevent path traversal attacks (e.g. "../../../etc/passwd").
 */
public final class PathSecurityUtils {

    private PathSecurityUtils() {}

    /**
     * Sanitizes a remote filename and resolves it strictly inside the target base directory.
     *
     * @param baseDir the trusted local downloads directory
     * @param untrustedFilename the filename received over TCP or REST
     * @return a safe canonical Path guaranteed to reside inside baseDir
     * @throws SecurityException if path traversal is detected
     */
    public static Path resolveSafeDestinationPath(File baseDir, String untrustedFilename) throws IOException {
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        if (untrustedFilename == null || untrustedFilename.isBlank()) {
            untrustedFilename = "received_file_" + System.currentTimeMillis();
        }

        // Strip any directory path components (forward and backward slashes)
        String cleanName = Paths.get(untrustedFilename).getFileName().toString();

        // Remove dangerous characters
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_");

        Path baseDirPath = baseDir.toPath().toRealPath();
        Path targetPath = baseDirPath.resolve(cleanName).normalize();

        // Enforce that targetPath starts with baseDirPath
        if (!targetPath.startsWith(baseDirPath)) {
            throw new SecurityException("Potential Path Traversal Attack detected for filename: " + untrustedFilename);
        }

        return targetPath;
    }
}
