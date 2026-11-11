package com.omnidrop.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a peer node discovered over the local network via UDP broadcasts.
 */
public class PeerDevice implements Serializable {

    private String deviceId;
    private String deviceName;
    private String ipAddress;
    private int tcpPort;
    private int httpPort;
    private long lastSeenTimestamp;
    private boolean active = true;

    public PeerDevice() {}

    public PeerDevice(String deviceId, String deviceName, String ipAddress, int tcpPort, int httpPort, long lastSeenTimestamp) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.tcpPort = tcpPort;
        this.httpPort = httpPort;
        this.lastSeenTimestamp = lastSeenTimestamp;
        this.active = true;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerDevice that = (PeerDevice) o;
        return Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId);
    }
}
