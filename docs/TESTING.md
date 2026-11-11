# OmniDrop – Comprehensive Testing Strategy

This document outlines the testing strategy, test cases, and verification procedures for the OmniDrop network programming project.

---

## 1. Test Levels & Strategy

```
+--------------------------------------------------------------+
|               System / Offline Multi-Device Tests            |
|       (Real LAN, Firewalls, Disconnections, Wireshark)      |
+------------------------------+-------------------------------+
                               |
+------------------------------v-------------------------------+
|              Network Integration & Socket Loopback           |
|    (TCP Socket Pairs, UDP Datagram Broadcast, WS Events)     |
+------------------------------+-------------------------------+
                               |
+------------------------------v-------------------------------+
|                Unit Tests & Protocol Verification            |
|    (Binary Framing, SHA-256 Hashes, Path Traversal Defense)  |
+--------------------------------------------------------------+
```

---

## 2. Java Backend Testing Suite

### 2.1 Protocol Framing & Binary Serialization Tests
- `FrameSerializationTest`: Encodes a 20-byte OmniDrop header, computes CRC32, decodes, and verifies that magic bytes, chunk indices, opcodes, and lengths match down to the exact bit.
- `FrameMagicValidationTest`: Verifies that invalid magic byte sequences immediately throw `ProtocolException` and close the socket.

### 2.2 Security & Path Traversal Defense
- `PathSecurityTest`: Feeds filenames like `../../Windows/System32/calc.exe` or `../../../../etc/passwd` to `TcpFileReceiverService` and verifies that paths are sanitized strictly into the designated download directory without escaping.

### 2.3 UDP Discovery Loopback Tests
- `UdpDiscoveryTest`: Binds a test `DatagramSocket` to loopback, sends a JSON discovery datagram, and verifies proper unmarshalling, local-IP detection, and peer registry registration.

### 2.4 TCP Point-to-Point Streaming Tests
- `TcpTransferLoopbackTest`: Spawns a background `ServerSocket` on an ephemeral port, establishes a client `Socket`, transmits a synthetic 10 MB payload in 64 KB chunks with framing, checks received byte length, and validates SHA-256 checksum equality.

### 2.5 Transfer Recovery & Resumption Tests
- `TransferRecoveryTest`: Transmits half of a file, artificially terminates the socket connection, verifies that checkpoint file records offset, resumes from the offset using `FileChannel.position()`, and verifies that the reconstructed file matches the original SHA-256.

### 2.6 One-to-Many Concurrent Distribution Tests
- `OneToManyDistributionTest`: Sets up 3 concurrent receiver sockets, sends a file concurrently via `ExecutorService`, and confirms all 3 receive complete, uncorrupted files without thread deadlocks.

---

## 3. Frontend Responsive & UI Testing
- **Viewport Testing**: Verified in Chrome DevTools Device Mode across Mobile (375px), Tablet (768px), and Desktop (1280px+).
- **WebSocket Reconnection Test**: Terminating and restarting the Java backend verifies that the frontend UI displays a "Reconnecting..." badge and automatically re-establishes state.
- **Form Validation**: File upload size limits, empty peer selection warnings, and invalid IP notifications.

---

## 4. Offline Multi-Device Field Testing Checklist
1. [ ] Disconnect both laptop devices from the internet (turn off cellular data/WAN).
2. [ ] Join the same Wi-Fi router or hotspot.
3. [ ] Launch OmniDrop on Laptop A (IP: 192.168.x.x).
4. [ ] Launch OmniDrop on Laptop B (IP: 192.168.x.y).
5. [ ] Verify that Device Discovery finds each other automatically within 5 seconds.
6. [ ] Send a 50 MB video file from A to B. Verify progress bars update smoothly in real time.
7. [ ] Disconnect Wi-Fi at 50% progress. Verify "Interrupted" state on both screens.
8. [ ] Reconnect Wi-Fi. Click "Resume Transfer". Verify transmission finishes and SHA-256 matches.
