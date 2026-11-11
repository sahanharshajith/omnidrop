package com.omnidrop.controller;

import com.omnidrop.discovery.UdpDiscoveryService;
import com.omnidrop.model.PeerDevice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceDiscoveryController {

    private final UdpDiscoveryService discoveryService;

    public DeviceDiscoveryController(UdpDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public ResponseEntity<List<PeerDevice>> getDiscoveredDevices() {
        return ResponseEntity.ok(discoveryService.getActivePeers());
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, String>> triggerManualBroadcast() {
        discoveryService.triggerManualBroadcast();
        return ResponseEntity.ok(Map.of("message", "Broadcast beacon transmitted."));
    }
}
