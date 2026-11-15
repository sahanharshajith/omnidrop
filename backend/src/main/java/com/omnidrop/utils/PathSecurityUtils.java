package com.omnidrop.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    /**
     * NEW (Member 3). Same safety as the method above, plus:
     *  - handles both \ and / as folder separators on any OS
     *  - never overwrites: if "report.pdf" exists, returns "report_1.pdf", "report_2.pdf", ...
     *
     * The numbers are the same every time (not a timestamp), so a resumed
     * transfer finds the same ".part" file again.
     */
    public static Path resolveUniqueDestinationPath(File baseDir, String untrustedFilename) throws IOException {
        String name = untrustedFilename;

        if (name != null) {
            // Keep only the part after the last / or \
            name = name.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1);

            // Clean bad characters BEFORE Paths.get() sees them (Windows throws on "?" or "*")
            name = name.replaceAll("[^a-zA-Z0-9._-]", "_");

            // "", ".", ".." and similar are not real names
            if (name.isBlank() || name.matches("\\.+")) {
                name = null; // the method above will make a safe default name
            }
        }

        Path target = resolveSafeDestinationPath(baseDir, name);

        Path folder = target.getParent();
        String fileName = target.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";

        int counter = 1;
        while (Files.exists(target)) {
            target = folder.resolve(stem + "_" + counter + extension);
            counter++;
        }
        return target;
    }
}