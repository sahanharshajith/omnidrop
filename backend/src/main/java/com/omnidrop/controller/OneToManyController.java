package com.omnidrop.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.distribution.OneToManyDistributionManager;
import com.omnidrop.model.PeerDevice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/distribution")
public class OneToManyController {

    private final OneToManyDistributionManager distributionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OneToManyController(OneToManyDistributionManager distributionManager) {
        this.distributionManager = distributionManager;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProgress(@PathVariable("id") String id) {
        var progress = distributionManager.getProgress(id);
        return progress == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(progress);
    }

    @PostMapping("/start")
    public ResponseEntity<?> startDistribution(@RequestParam("file") MultipartFile multipartFile,
                                               @RequestParam("targetsJson") String targetsJson) {
        if (multipartFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided for distribution."));
        }

        try {
            List<PeerDevice> targets = objectMapper.readValue(targetsJson, new TypeReference<List<PeerDevice>>() {});
            if (targets == null || targets.isEmpty() || targets.stream().anyMatch(Objects::isNull)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Target peers list is empty."));
            }

            String originalName = multipartFile.getOriginalFilename();
            String fileName = originalName == null ? "" : originalName.replace('\\', '/');
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..") || fileName.contains(":")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name."));
            }
            File directory = Files.createTempDirectory("omnidrop_dist_").toFile();
            directory.deleteOnExit();
            File tempFile = new File(directory, fileName);
            tempFile.deleteOnExit();
            multipartFile.transferTo(tempFile);

            String distributionId = distributionManager.distributeFileConcurrently(tempFile, targets);

            return ResponseEntity.ok(Map.of(
                    "distributionId", distributionId,
                    "fileName", fileName,
                    "receiversCount", targets.size(),
                    "status", "STARTED"
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
