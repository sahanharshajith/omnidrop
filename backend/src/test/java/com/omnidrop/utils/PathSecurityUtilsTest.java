package com.omnidrop.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathSecurityUtilsTest {

    @Test
    @DisplayName("Should sanitize filename and prevent path traversal escape")
    void testPreventPathTraversal(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.toFile();

        // Malicious filenames attempting directory escape
        String malicious1 = "../../etc/passwd";
        String malicious2 = "..\\..\\Windows\\System32\\calc.exe";
        String malicious3 = "normal_document.pdf";

        Path safePath1 = PathSecurityUtils.resolveSafeDestinationPath(baseDir, malicious1);
        Path safePath2 = PathSecurityUtils.resolveSafeDestinationPath(baseDir, malicious2);
        Path safePath3 = PathSecurityUtils.resolveSafeDestinationPath(baseDir, malicious3);

        // All resolved paths MUST remain inside baseDir
        assertTrue(safePath1.startsWith(baseDir.toPath().toRealPath()));
        assertTrue(safePath2.startsWith(baseDir.toPath().toRealPath()));
        assertTrue(safePath3.startsWith(baseDir.toPath().toRealPath()));

        assertEquals("passwd", safePath1.getFileName().toString());
        assertEquals("normal_document.pdf", safePath3.getFileName().toString());
    }
}
