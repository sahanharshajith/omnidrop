package com.omnidrop.discovery;

/**
 * MEMBER 1: One UDP discovery beacon, as sent on port 8888.
 *
 * @param deviceId   unique node ID (UUID)
 * @param deviceName human readable node name
 * @param tcpPort    port of the node's TCP file receiver
 * @param httpPort   port of the node's REST/WebSocket server
 * @param timestamp  sender's clock when the beacon was built (ms)
 * @param event      {@link #EVENT_ALIVE} for heartbeats, {@link #EVENT_LEAVE} on shutdown
 */
public record DiscoveryBeacon(String deviceId, String deviceName, int tcpPort, int httpPort,
                              long timestamp, String event) {

    public static final String MAGIC = "OMNI_DISCOVER";
    public static final int VERSION = 1;
    public static final String EVENT_ALIVE = "ALIVE";
    public static final String EVENT_LEAVE = "LEAVE";

    public boolean isLeave() {
        return EVENT_LEAVE.equals(event);
    }
}
