package com.omnidrop.controller;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.discovery.UdpDiscoveryService;
import com.omnidrop.utils.NetworkInterfaceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final OmniDropProperties properties;
    private final UdpDiscoveryService discoveryService;

    public HealthController(OmniDropProperties properties, UdpDiscoveryService discoveryService) {
        this.properties = properties;
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("nodeName", properties.getNode().getName());
        status.put("nodeId", properties.getNode().getId());
        status.put("tcpPort", properties.getNetwork().getTcpPort());
        status.put("udpPort", properties.getNetwork().getUdpPort());
        status.put("ipAddresses", NetworkInterfaceUtils.getLocalIPv4Addresses().stream().map(InetAddress::getHostAddress).toList());
        // MEMBER 1: lets the UI show "Discovery offline" when UDP port 8888 could not be opened
        status.put("discoveryRunning", discoveryService.isRunning());
        status.put("discoveryError", discoveryService.getLastError());
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }
}
