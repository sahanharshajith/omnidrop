package com.omnidrop.controller;

import com.omnidrop.discovery.UdpDiscoveryService;
import com.omnidrop.model.PeerDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MEMBER 1: REST endpoints for discovered devices.
 */
class DeviceDiscoveryControllerTest {

    private UdpDiscoveryService discoveryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        discoveryService = mock(UdpDiscoveryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceDiscoveryController(discoveryService)).build();
    }

    @Test
    void listsDiscoveredDevices() throws Exception {
        when(discoveryService.getActivePeers()).thenReturn(List.of(
                new PeerDevice("bob", "Bob-Laptop", "192.168.1.3", 9090, 8080, 123L)));

        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("bob"))
                .andExpect(jsonPath("$[0].ipAddress").value("192.168.1.3"))
                .andExpect(jsonPath("$[0].tcpPort").value(9090));
    }

    @Test
    void broadcastTriggersBeacon() throws Exception {
        mockMvc.perform(post("/api/v1/devices/broadcast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Broadcast beacon transmitted."));

        verify(discoveryService).triggerManualBroadcast();
    }
}
