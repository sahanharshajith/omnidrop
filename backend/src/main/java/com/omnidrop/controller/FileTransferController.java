package com.omnidrop.controller;

import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferProgress;
import com.omnidrop.sender.TcpFileSenderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transfer")
public class FileTransferController {

    private final TcpFileSenderService senderService;

    public FileTransferController(TcpFileSenderService senderService) {
        this.senderService = senderService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> initiateFileTransfer(@RequestParam("file") MultipartFile multipartFile,
                                                  @RequestParam("targetIp") String targetIp,
                                                  @RequestParam("targetPort") int targetPort,
                                                  @RequestParam(value = "targetName", defaultValue = "Remote-Peer") String targetName) {
        if (multipartFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file selected."));
        }

        try {
            // Stage temporary file for streaming via TCP
            File tempFile = File.createTempFile("omnidrop_upload_", "_" + multipartFile.getOriginalFilename());
            multipartFile.transferTo(tempFile);
            tempFile.deleteOnExit();

            PeerDevice targetPeer = new PeerDevice();
            targetPeer.setIpAddress(targetIp);
            targetPeer.setTcpPort(targetPort);
            targetPeer.setDeviceName(targetName);

            // Asynchronously dispatch TCP socket transfer
            new Thread(() -> {
                try {
                    senderService.sendFile(targetPeer, tempFile, 0);
                } catch (Exception ignored) {}
            }, "OmniDrop-Async-Sender").start();

            return ResponseEntity.ok(Map.of(
                    "status", "QUEUED",
                    "fileName", multipartFile.getOriginalFilename(),
                    "fileSize", multipartFile.getSize(),
                    "targetPeer", targetName
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
