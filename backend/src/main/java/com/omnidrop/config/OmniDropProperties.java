package com.omnidrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Type-safe configuration properties for OmniDrop.
 */
@ConfigurationProperties(prefix = "omnidrop")
public class OmniDropProperties {

    private NodeProperties node = new NodeProperties();
    private NetworkProperties network = new NetworkProperties();
    private TransferProperties transfer = new TransferProperties();
    private StorageProperties storage = new StorageProperties();

    public static class NodeProperties {
        private String name = "OmniDrop-Node";
        private String id = UUID.randomUUID().toString();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class NetworkProperties {
        private int tcpPort = 9090;
        private int udpPort = 8888;
        private long broadcastIntervalMs = 3000;
        private long peerTimeoutMs = 12000;

        public int getTcpPort() {
            return tcpPort;
        }

        public void setTcpPort(int tcpPort) {
            this.tcpPort = tcpPort;
        }

        public int getUdpPort() {
            return udpPort;
        }

        public void setUdpPort(int udpPort) {
            this.udpPort = udpPort;
        }

        public long getBroadcastIntervalMs() {
            return broadcastIntervalMs;
        }

        public void setBroadcastIntervalMs(long broadcastIntervalMs) {
            this.broadcastIntervalMs = broadcastIntervalMs;
        }

        public long getPeerTimeoutMs() {
            return peerTimeoutMs;
        }

        public void setPeerTimeoutMs(long peerTimeoutMs) {
            this.peerTimeoutMs = peerTimeoutMs;
        }
    }

    public static class TransferProperties {
        private int chunkSizeBytes = 65536; // 64 KB
        private int socketTimeoutMs = 15000;

        public int getChunkSizeBytes() {
            return chunkSizeBytes;
        }

        public void setChunkSizeBytes(int chunkSizeBytes) {
            this.chunkSizeBytes = chunkSizeBytes;
        }

        public int getSocketTimeoutMs() {
            return socketTimeoutMs;
        }

        public void setSocketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
        }
    }

    public static class StorageProperties {
        private String downloadsDir = "downloads";
        private String checkpointsDir = "checkpoints";

        public String getDownloadsDir() {
            return downloadsDir;
        }

        public void setDownloadsDir(String downloadsDir) {
            this.downloadsDir = downloadsDir;
        }

        public String getCheckpointsDir() {
            return checkpointsDir;
        }

        public void setCheckpointsDir(String checkpointsDir) {
            this.checkpointsDir = checkpointsDir;
        }
    }

    public NodeProperties getNode() {
        return node;
    }

    public void setNode(NodeProperties node) {
        this.node = node;
    }

    public NetworkProperties getNetwork() {
        return network;
    }

    public void setNetwork(NetworkProperties network) {
        this.network = network;
    }

    public TransferProperties getTransfer() {
        return transfer;
    }

    public void setTransfer(TransferProperties transfer) {
        this.transfer = transfer;
    }

    public StorageProperties getStorage() {
        return storage;
    }

    public void setStorage(StorageProperties storage) {
        this.storage = storage;
    }
}
