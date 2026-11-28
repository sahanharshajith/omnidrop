package com.omnidrop.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MEMBER 1: Beacon encoding/decoding and every validation rule.
 */
class BeaconCodecTest {

    private static Optional<DiscoveryBeacon> decode(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return BeaconCodec.decode(bytes, 0, bytes.length);
    }

    @Test
    void roundTripKeepsEveryField() {
        DiscoveryBeacon original = new DiscoveryBeacon("id-1", "Thiloni-Laptop", 9090, 8081, 1234L, DiscoveryBeacon.EVENT_ALIVE);
        byte[] bytes = BeaconCodec.encode(original);

        assertThat(BeaconCodec.decode(bytes, 0, bytes.length)).contains(original);
    }

    @Test
    void encodedJsonContainsMagicAndVersion() {
        String json = new String(BeaconCodec.encode(
                new DiscoveryBeacon("id", "name", 9090, 8080, 1L, DiscoveryBeacon.EVENT_LEAVE)), StandardCharsets.UTF_8);

        assertThat(json).contains("\"magic\":\"OMNI_DISCOVER\"", "\"version\":1", "\"event\":\"LEAVE\"");
    }

    @Test
    void decodesOnlyTheGivenSliceOfTheBuffer() {
        byte[] beacon = BeaconCodec.encode(new DiscoveryBeacon("id", "name", 9090, 8080, 1L, DiscoveryBeacon.EVENT_ALIVE));
        byte[] buffer = new byte[beacon.length + 20];
        System.arraycopy(beacon, 0, buffer, 10, beacon.length);

        assertThat(BeaconCodec.decode(buffer, 10, beacon.length)).isPresent();
    }

    @Test
    void missingEventMeansAliveForOlderNodes() {
        Optional<DiscoveryBeacon> beacon = decode(
                "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"Old\",\"tcpPort\":9090}");

        assertThat(beacon).isPresent();
        assertThat(beacon.get().isLeave()).isFalse();
        assertThat(beacon.get().httpPort()).isEqualTo(8080);
    }

    @Test
    void leaveEventIsRecognised() {
        assertThat(decode("{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090,\"event\":\"LEAVE\"}"))
                .hasValueSatisfying(b -> assertThat(b.isLeave()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "this is not json",
            "[1,2,3]",
            "\"just a string\"",
            "{\"magic\":\"SOMETHING_ELSE\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090}",
            "{\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":2,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090}",
            "{\"magic\":\"OMNI_DISCOVER\",\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceName\":\"n\",\"tcpPort\":9090}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"  \",\"deviceName\":\"n\",\"tcpPort\":9090}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"tcpPort\":9090}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\"}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":0}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":70000}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090,\"httpPort\":-5}",
            "{\"magic\":\"OMNI_DISCOVER\",\"version\":1,\"deviceId\":\"x\",\"deviceName\":\"n\",\"tcpPort\":9090,\"event\":\"DANCE\"}"
    })
    void rejectsInvalidBeacons(String json) {
        assertThat(decode(json)).isEmpty();
    }

    @Test
    void rejectsEmptyOrOutOfRangeInput() {
        assertThat(BeaconCodec.decode(new byte[0], 0, 0)).isEmpty();
        assertThat(BeaconCodec.decode(null, 0, 5)).isEmpty();
        assertThat(BeaconCodec.decode(new byte[4], 2, 10)).isEmpty();
    }
}
