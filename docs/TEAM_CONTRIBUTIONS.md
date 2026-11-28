# OmniDrop – Five-Member Group Allocation & Responsibility Matrix

Every member of the group is allocated **at least one genuine, mandatory Java network programming concept**. There are no "documentation-only" or "frontend-only" roles.

---

## 1. Group Allocation Matrix

| Member | Assigned Network Concept | Java Sockets & Networking APIs | Java Classes Owned | Frontend Components Owned | Feature Branch |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Member 1** | **UDP Device Discovery** | `DatagramSocket`, `DatagramPacket`, Multicast/Broadcast, `NetworkInterface`, `SocketTimeoutException` | `UdpDiscoveryService`, `UdpBeaconBroadcaster`, `UdpPacketListener`, `PeerRegistry` | `DeviceDiscoveryCard`, `RadarView`, `PeerStatusBadge` | `feature/udp-discovery` |
| **Member 2** | **TCP File Sender** | `Socket`, `OutputStream`, `BufferedOutputStream`, Binary framing, Chunk streaming | `TcpFileSenderService`, `FrameEncoder`, `SenderTransferSession` | `FileTransferSenderForm`, `TransferProgressCard`, `SpeedMeter` | `feature/tcp-sender` |
| **Member 3** | **TCP File Receiver** | `ServerSocket`, `Socket`, `InputStream`, `BufferedInputStream`, Path validation | `TcpFileReceiverService`, `FrameDecoder`, `ReceiverConnectionHandler` | `IncomingTransferModal`, `ReceivedFilesList`, `PathSettingCard` | `feature/tcp-receiver` |
| **Member 4** | **One-to-Many Distribution** | `ExecutorService`, Concurrent TCP Sockets, `CountDownLatch`, `Future`, Thread Pools | `OneToManyDistributionManager`, `ConcurrentTransferWorker`, `DistributionTracker` | `MultiSelectPeerList`, `DistributionGrid`, `OverallProgressMeter` | `feature/one-to-many` |
| **Member 5** | **Transfer Recovery & Integrity**| `FileChannel`, `RandomAccessFile`, Byte positioning, SHA-256 Checksums, Checkpointing | `TransferRecoveryManager`, `CheckpointStore`, `ChecksumValidator`, `ResumeProtocolHandler` | `InterruptedTransfersList`, `ResumeActionCard`, `IntegrityBadge` | `feature/transfer-recovery` |

---

## 2. Detailed Member Responsibilities

### Member 1: UDP Device Discovery
- **Network Programming Core**:
  - Implement dynamic device discovery over local subnets without an internet connection or static DNS.
  - Bind `DatagramSocket` to port `8888` and set `setBroadcast(true)`.
  - Iterate active network interfaces to broadcast beacons across valid IPv4 subnets.
  - Implement a dedicated background daemon listening for incoming datagrams with timeout handling.
  - Maintain an in-memory `PeerRegistry` with automated expiration of inactive nodes (after 12s) and immediate removal on a LEAVE beacon.
- **Frontend & Integration**:
  - Build the reactive Device Discovery radar and table showing discovered peers with signal/status indicators.
  - Connect discovery events with the WebSocket channel to notify the frontend when peers join or leave.
- **Testing**:
  - Unit tests verifying packet encoding, decoding, loopback filter, and heartbeat expiration logic.

### Member 2: TCP File Sender
- **Network Programming Core**:
  - Implement client-side TCP connection establishment using `java.net.Socket`.
  - Implement the OmniDrop binary framing protocol: write fixed 20-byte headers followed by chunk payloads.
  - Stream files from disk in configurable chunk sizes (e.g., 64 KB) to avoid out-of-memory errors on large files.
  - Read incoming `ACK` frames from receiver and calculate real-time throughput (bytes/second) and ETA.
- **Frontend & Integration**:
  - Build the file picker and point-to-point transfer view in the Next.js frontend.
  - Transmit progress updates to the `WebSocketNotificationService`.
- **Testing**:
  - Unit and mock socket tests for framing header generation, chunk slicing, and graceful IO error handling.

### Member 3: TCP File Receiver
- **Network Programming Core**:
  - Implement server-side TCP listener using `java.net.ServerSocket` bound to port `9090`.
  - Accept incoming connections and delegate each to a worker thread for processing.
  - Decode binary frames, validate magic bytes (`0x4F4D4E49`), and unpack file metadata.
  - Securely handle destination paths to prevent path traversal attacks (`../` stripping).
  - Stream received bytes directly to disk; create `.part` files during in-progress downloads.
- **Frontend & Integration**:
  - Implement real-time prompt/toast when an incoming file offer arrives.
  - Display list of downloaded files with direct open/download links.
- **Testing**:
  - Unit tests for header validation, path traversal prevention, and corrupted chunk detection.

### Member 4: One-to-Many Distribution
- **Network Programming Core**:
  - Coordinate concurrent file transmissions from a single sender to $N$ receiver devices simultaneously.
  - Utilize `ExecutorService` (e.g. `Executors.newFixedThreadPool(maxReceivers)`) to manage concurrent TCP sockets.
  - Maintain thread isolation: ensure that an error or timeout on Receiver $B$ does not crash or stall transfer to Receiver $C$.
  - Implement an aggregate distribution tracker aggregating total bytes sent and individual peer completion status.
- **Frontend & Integration**:
  - Develop the One-to-Many distribution screen with multi-select peer checkboxes and live multi-progress bars.
- **Testing**:
  - Multi-threaded unit test simulating concurrent loopback sockets receiving a distributed test payload.

### Member 5: Transfer Recovery & Integrity
- **Network Programming Core**:
  - Implement chunk-level checkpoint tracking, recording bytes written to disk.
  - Use Java NIO `FileChannel` or `RandomAccessFile` to seek directly to the resumption byte offset.
  - Implement `RESUME_REQ` and `ACCEPT` handshake to re-align sender and receiver chunk indices.
  - Compute SHA-256 cryptographic digest of the finished file and compare against the sender's original offer hash.
- **Frontend & Integration**:
  - Create the Interrupted Transfers / Recovery dashboard showing partially received files and one-click "Resume" buttons.
- **Testing**:
  - Test simulated socket disconnection mid-transfer, verify checkpoint persistence, resumption from offset, and final SHA-256 match.

---

## 3. Demonstration & Oral Exam Expectations
During the project demonstration, every member must be prepared to:
1. Walk through their specific Java network programming class lines (e.g., `DatagramSocket`, `ServerSocket`, `FileChannel`).
2. Demonstrate their unit tests passing.
3. Show Wireshark or log traces of their packets/frames traveling across the offline network.
4. Explain how their component interacts with the WebSocket layer and the Next.js UI.
