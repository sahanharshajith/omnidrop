package com.omnidrop.controller;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.utils.NetworkInterfaceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final OmniDropProperties properties;

    public HealthController(OmniDropProperties properties) {
        this.properties = properties;
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
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }
}
