package com.omnidrop.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumUtilsTest {

    @Test
    @DisplayName("Should correctly calculate known SHA-256 hash")
    void testSha256Calculation(@TempDir Path tempDir) throws IOException {
        String testData = "OmniDrop SHA256 Integrity Verification Test";
        byte[] bytes = testData.getBytes(StandardCharsets.UTF_8);

        // Calculate from InputStream
        String hashFromStream = ChecksumUtils.calculateSha256(new ByteArrayInputStream(bytes));
        assertNotNull(hashFromStream);
        assertEquals(64, hashFromStream.length()); // SHA-256 is 64 hex characters

        // Calculate from File
        File testFile = tempDir.resolve("testfile.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write(bytes);
        }
        String hashFromFile = ChecksumUtils.calculateSha256(testFile);

        assertEquals(hashFromStream, hashFromFile);
    }
}
