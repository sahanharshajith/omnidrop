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

### 2.3 UDP Discovery Tests (Member 1)
All under `backend/src/test/java/com/omnidrop/discovery/` (plus the controller test). 48 tests, ~94% line coverage of `com.omnidrop.discovery`.
- `UdpLoopbackIntegrationTest`: starts the real `UdpDiscoveryService` on an OS-chosen UDP port, sends beacons from a second `DatagramSocket` over `127.0.0.1`, and verifies `DEVICE_DISCOVERED` (with the sender IP taken from the packet), LEAVE → `DEVICE_LOST`, junk datagrams ignored, and clean `stop()`.
- `BeaconCodecTest`: encode/decode round trip and every validation rule (wrong magic, version, missing fields, bad ports, unknown event, non-JSON).
- `PeerRegistryTest`: `NEW` / `REFRESHED` / `UPDATED`, heartbeat expiry with a fake clock, `remove`, concurrent upserts.
- `UdpDiscoveryServiceTest`: self-beacon suppression, notify once per new peer, re-notify on IP change, eviction → `notifyPeerLost`, LEAVE handling.
- `UdpPacketListenerTest`: ALIVE → `registerPeer`, LEAVE → `unregisterPeer`, junk and oversized datagrams ignored.
- `controller/DeviceDiscoveryControllerTest`: `GET /api/v1/devices` and `POST /api/v1/devices/broadcast`.

### 2.4 TCP Point-to-Point Streaming Tests
- `TcpTransferLoopbackTest`: Spawns a background `ServerSocket` on an ephemeral port, establishes a client `Socket`, transmits a synthetic 10 MB payload in 64 KB chunks with framing, checks received byte length, and validates SHA-256 checksum equality.

### 2.5 Transfer Recovery & Resumption Tests
- `TransferRecoveryTest`: Transmits half of a file, artificially terminates the socket connection, verifies that checkpoint file records offset, resumes from the offset using `FileChannel.position()`, and verifies that the reconstructed file matches the original SHA-256.

### 2.6 One-to-Many Concurrent Distribution Tests
- `OneToManyDistributionTest`: Sets up 3 concurrent receiver sockets, sends a file concurrently via `ExecutorService`, and confirms all 3 receive complete, uncorrupted files without thread deadlocks.

### 2.7 WebSocket Command Dispatch Tests (Member 2)
- `TransferWebSocketHandlerTest`: Unit tests for the WebSocket handler's two responsibilities:
  - **Command dispatch** — `TRIGGER_DISCOVERY_PING` invokes `UdpDiscoveryService.triggerManualBroadcast()`; `CANCEL_TRANSFER` / `RESUME_TRANSFER` invoke the corresponding `TcpFileSenderService` methods with the correct `transferId`. Missing-`transferId`, unknown commands, and malformed JSON are swallowed without throwing and without dispatching.
  - **Event broadcast** — a serialized `WebSocketEvent` is sent to every open session, skipped for closed ones, and never delivered after `afterConnectionClosed` removes that session.
- 10 tests, Mockito-based, no Spring context required.

---

## 3. Frontend Responsive & UI Testing

### 3.1 Manual UI Verification
- **Viewport Testing**: Verified in Chrome DevTools Device Mode across Mobile (375px), Tablet (768px), and Desktop (1280px+).
- **WebSocket Reconnection Test**: Terminating and restarting the Java backend verifies that the frontend UI displays a "Reconnecting..." badge and automatically re-establishes state.
- **Form Validation**: File upload size limits, empty peer selection warnings, and invalid IP notifications.

### 3.2 Automated Frontend Tests (Vitest + Testing Library)
Run with `cd frontend && npm test` (config in `frontend/vitest.config.mts`). Eight suites, 52 tests:

- `__tests__/websocket.test.ts` (8) — the `transferWs` singleton: socket URL construction, idempotent `connect()`, `onEvent` delivery and unsubscribe, malformed-JSON safety, `sendCommand` gating on socket state, and reconnect backoff after close.
- `__tests__/useWebSocket.test.tsx` (5) — the React hook bridging the singleton to component state: connect on mount, subscribe, `lastMessage` updates on event, `isConnected` reflects `transferWs.isOpen()`, unsubscribe on unmount.
- `__tests__/TransferProgressCard.test.tsx` (13) — the UI card: file name / percentage / speed rendering, sender/receiver label swap, Cancel visible only while TRANSFERRING or CONNECTING (sender only), Resume visible only when INTERRUPTED (sender only), and both buttons dispatch the correct `sendCommand` payloads when clicked.

- `__tests__/usePeers.test.tsx` (7) — Member 1's peer list hook: initial REST load, **two back-to-back `DEVICE_DISCOVERED` events keep both peers**, upsert instead of duplicate, `DEVICE_LOST` removal, malformed events ignored, unsubscribe, `seenAt` merge.
- `__tests__/peerStatus.test.ts` (8) — Online / Weak signal / Offline thresholds, clock-skew safety, age formatting, radar angles.
- `__tests__/PeerStatusBadge.test.tsx` (3), `__tests__/DeviceDiscoveryCard.test.tsx` (3), `__tests__/RadarView.test.tsx` (5) — discovery UI rendering, accessible labels and selection by click/keyboard.

`npm run test:coverage` produces a V8 coverage report (`coverage/`, git-ignored).
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
