package com.omnidrop.controller;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.utils.PathSecurityUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller allowing the Next.js web interface (on laptops, tablets, or phones)
 * to view, download, and manage files received by this node's Java TCP receiver.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileStorageController {

    private final OmniDropProperties properties;

    public FileStorageController(OmniDropProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listReceivedFiles() {
        File downloadDir = new File(properties.getStorage().getDownloadsDir());
        List<Map<String, Object>> filesList = new ArrayList<>();

        if (downloadDir.exists() && downloadDir.isDirectory()) {
            File[] files = downloadDir.listFiles(f -> !f.getName().endsWith(".part"));
            if (files != null) {
                for (File f : files) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("fileName", f.getName());
                    item.put("fileSize", f.length());
                    item.put("lastModified", f.lastModified());
                    filesList.add(item);
                }
            }
        }
        return ResponseEntity.ok(filesList);
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) throws IOException {
        File downloadDir = new File(properties.getStorage().getDownloadsDir());
        Path safePath = PathSecurityUtils.resolveSafeDestinationPath(downloadDir, fileName);
        File file = safePath.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileName) throws IOException {
        File downloadDir = new File(properties.getStorage().getDownloadsDir());
        Path safePath = PathSecurityUtils.resolveSafeDestinationPath(downloadDir, fileName);
        File file = safePath.toFile();

        boolean deleted = false;
        if (file.exists()) {
            deleted = file.delete();
        }
        return ResponseEntity.ok(Map.of("deleted", deleted, "fileName", fileName));
    }
}
