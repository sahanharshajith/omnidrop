package com.omnidrop.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MEMBER 3: Path traversal defense and no-overwrite behaviour.
 */
class PathSecurityUtilsTest {

    @TempDir
    Path tempDir;

    private File baseDir() {
        return tempDir.toFile();
    }

    private Path realBase() throws IOException {
        return tempDir.toRealPath();
    }

    private String nameOf(Path p) {
        return p.getFileName().toString();
    }

    // ---------------------------------------------------------------
    // Traversal attacks
    // ---------------------------------------------------------------

    @Test
    void unixTraversalIsStrippedToFileName() throws IOException {
        Path result = PathSecurityUtils.resolveSafeDestinationPath(baseDir(), "../../../../etc/passwd");

        assertEquals("passwd", nameOf(result));
        assertEquals(realBase(), result.getParent());
    }

    @Test
    void slashTraversalInUniqueMethodIsStripped() throws IOException {
        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "../../Windows/System32/calc.exe");

        assertEquals("calc.exe", nameOf(result));
        assertEquals(realBase(), result.getParent());
    }

    @Test
    void backslashTraversalIsStrippedOnAnyOs() throws IOException {
        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "..\\..\\Windows\\System32\\calc.exe");

        assertEquals("calc.exe", nameOf(result));
        assertEquals(realBase(), result.getParent());
    }

    @Test
    void windowsAbsolutePathIsReducedToFileName() throws IOException {
        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "C:\\Users\\Bob\\secret.txt");

        assertEquals("secret.txt", nameOf(result));
        assertEquals(realBase(), result.getParent());
    }

    @Test
    void unixAbsolutePathIsReducedToFileName() throws IOException {
        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "/etc/passwd");

        assertEquals("passwd", nameOf(result));
        assertEquals(realBase(), result.getParent());
    }

    @Test
    void nastyNamesNeverEscapeTheDownloadsFolder() throws IOException {
        List<String> nastyNames = List.of(
                "../x",
                "..\\x",
                "....//x",
                "/../x",
                "a/../../b",
                "%2e%2e/x",
                "evil\u0000.txt",
                "con?.txt",
                "..",
                "."
        );

        for (String name : nastyNames) {
            Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), name);
            assertEquals(realBase(), result.getParent(), "Escaped the folder for name: " + name);
        }
    }

    // ---------------------------------------------------------------
    // Cleaning names
    // ---------------------------------------------------------------

    @Test
    void illegalCharactersBecomeUnderscores() throws IOException {
        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "my file?<>.txt");

        assertEquals("my_file___.txt", nameOf(result));
    }

    @Test
    void dotOnlyNamesGetADefaultName() throws IOException {
        for (String name : List.of("..", "...", ".")) {
            Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), name);

            assertTrue(nameOf(result).startsWith("received_file_"), "Unexpected name for: " + name);
            assertEquals(realBase(), result.getParent());
        }
    }

    @Test
    void nullAndBlankNamesGetADefaultName() throws IOException {
        Path fromNull = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), null);
        Path fromBlank = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "   ");

        assertTrue(nameOf(fromNull).startsWith("received_file_"));
        assertTrue(nameOf(fromBlank).startsWith("received_file_"));
    }

    // ---------------------------------------------------------------
    // No overwriting
    // ---------------------------------------------------------------

    @Test
    void existingFileGetsNumberSuffix() throws IOException {
        Files.writeString(tempDir.resolve("report.pdf"), "old content");

        Path first = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "report.pdf");
        assertEquals("report_1.pdf", nameOf(first));

        Files.writeString(first, "second");

        Path second = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "report.pdf");
        assertEquals("report_2.pdf", nameOf(second));
    }

    @Test
    void fileWithoutExtensionGetsNumberSuffix() throws IOException {
        Files.writeString(tempDir.resolve("README"), "old");

        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "README");

        assertEquals("README_1", nameOf(result));
    }

    @Test
    void freeNameIsKeptAsIs() throws IOException {
        Path result = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "photo.jpg");

        assertEquals("photo.jpg", nameOf(result));
    }

    @Test
    void sameNameGivesSamePathEveryTime() throws IOException {
        // Important for resume: the second connection must find the same ".part" file.
        Path a = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "video.mp4");
        Path b = PathSecurityUtils.resolveUniqueDestinationPath(baseDir(), "video.mp4");

        assertEquals(a, b);
        assertNotEquals(null, a);
    }
}