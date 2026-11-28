package com.omnidrop.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MEMBER 1: Converts {@link DiscoveryBeacon}s to and from the JSON bytes sent over UDP.
 * All validation of incoming beacons lives here, so it can be unit tested without sockets.
 */
public final class BeaconCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BeaconCodec() {}

    /** Beacon → UTF-8 JSON bytes. */
    public static byte[] encode(DiscoveryBeacon beacon) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("magic", DiscoveryBeacon.MAGIC);
        json.put("version", DiscoveryBeacon.VERSION);
        json.put("deviceId", beacon.deviceId());
        json.put("deviceName", beacon.deviceName());
        json.put("tcpPort", beacon.tcpPort());
        json.put("httpPort", beacon.httpPort());
        json.put("timestamp", beacon.timestamp());
        json.put("event", beacon.event());
        try {
            return MAPPER.writeValueAsString(json).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            // Only plain strings and numbers go in, so this cannot really happen
            throw new IllegalStateException("Could not encode discovery beacon", e);
        }
    }

    /**
     * UDP bytes → beacon. Returns empty when the bytes are not a valid OmniDrop beacon:
     * not JSON, wrong magic, unsupported version, missing deviceId/deviceName,
     * invalid ports, or an unknown event.
     */
    public static Optional<DiscoveryBeacon> decode(byte[] data, int offset, int length) {
        if (data == null || length <= 0 || offset < 0 || offset + length > data.length) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(data, offset, length);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }

        // Rule 1: must be our protocol
        if (!DiscoveryBeacon.MAGIC.equals(root.path("magic").asText())) {
            return Optional.empty();
        }
        // Rule 2: must be a version we understand
        if (root.path("version").asInt(-1) != DiscoveryBeacon.VERSION) {
            return Optional.empty();
        }
        // Rule 3: required fields (path() never returns null, so no NullPointerException)
        String deviceId = root.path("deviceId").asText("").trim();
        String deviceName = root.path("deviceName").asText("").trim();
        if (deviceId.isEmpty() || deviceName.isEmpty()) {
            return Optional.empty();
        }
        // Rule 4: real port numbers
        int tcpPort = root.path("tcpPort").asInt(-1);
        int httpPort = root.path("httpPort").asInt(8080);
        if (!isValidPort(tcpPort) || !isValidPort(httpPort)) {
            return Optional.empty();
        }
        // Rule 5: known event (missing = ALIVE, so older nodes still work)
        String event = root.path("event").asText(DiscoveryBeacon.EVENT_ALIVE);
        if (!DiscoveryBeacon.EVENT_ALIVE.equals(event) && !DiscoveryBeacon.EVENT_LEAVE.equals(event)) {
            return Optional.empty();
        }

        long timestamp = root.path("timestamp").asLong(0L);
        return Optional.of(new DiscoveryBeacon(deviceId, deviceName, tcpPort, httpPort, timestamp, event));
    }

    static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }
}
