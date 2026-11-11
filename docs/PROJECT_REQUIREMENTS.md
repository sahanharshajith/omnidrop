# OmniDrop – Project Requirements Document

## 1. Project Overview & Problem Statement
Transferring files between devices is difficult or impossible when internet connectivity is absent or unreliable. Cloud-based storage and file-transfer platforms (such as Google Drive, Dropbox, or WeTransfer) strictly require external internet connectivity. In offline environments—such as classrooms, field operations, local Wi-Fi hotspots, or emergency mesh networks—devices need a peer-to-peer, local-network-only file distribution mechanism. Furthermore, sharing a large file with multiple devices individually through ad-hoc point-to-point transfers wastes bandwidth and time.

**OmniDrop** is a mobile-responsive offline peer-to-peer file sharing and one-to-many distribution web application designed with a **Java network programming backend** (using raw TCP and UDP sockets) and a modern **Next.js frontend** communicating in real time over **WebSockets**.

---

## 2. Functional Requirements (FR)

### FR1: Offline Zero-Internet Operation
- The application must operate entirely within a Local Area Network (LAN) or mobile hotspot without connecting to any external cloud service or the public internet.
- DNS resolution must not be required; communication uses direct IP addressing or broadcast UDP.

### FR2: Dynamic UDP Device Discovery
- Nodes must announce their presence on the local subnet by broadcasting UDP discovery beacons.
- Nodes must listen for incoming UDP discovery packets on a configurable discovery port (default: `8888`).
- Nodes must periodically clean up stale or disconnected devices when heartbeat beacons cease (configurable timeout: 15 seconds).
- Discovered devices must be pushed to the web client in real time via WebSockets.

### FR3: Reliable TCP Point-to-Point File Transfer
- The sender node must connect directly to the receiver node's TCP server socket (default port: `9090`).
- The protocol must perform a handshake (`HELLO`, `FILE_OFFER`, `ACCEPT`/`REJECT`).
- File data must be divided into structured, framed binary chunks (e.g., 64 KB chunks).
- Transfers must support streaming large files without loading entire files into memory.

### FR4: Concurrent One-to-Many File Distribution
- A sender must be able to distribute a single file to multiple selected receiver nodes simultaneously.
- Transfers must be executed concurrently using managed Java thread pools (`ExecutorService`).
- Each receiver's connection must be isolated; a network failure or slow receiver must not block or terminate the transfer to other receivers.

### FR5: Interrupted Transfer Recovery & Resumption
- The system must track transfer checkpoints by recording the byte offset and last confirmed chunk index.
- If a connection drops, the sender or receiver can issue a `RESUME_REQUEST` with the last received byte offset.
- The sender must seek to the checkpoint using Java NIO (`FileChannel`) or `RandomAccessFile` and resume streaming remaining chunks.
- Upon completion, the receiver must compute and verify the file's cryptographic hash (SHA-256) against the sender's offer hash.

### FR6: Real-Time WebSocket Synchronization
- The Java backend must host a WebSocket endpoint (default: `/ws/transfers`).
- All device discovery events, transfer progress (bytes sent, speed in MB/s, ETA), errors, and recovery states must be broadcast to the frontend in real time.

---

## 3. Non-Functional Requirements (NFR)

### NFR1: Performance & Resource Management
- Memory usage must remain bounded regardless of file size by utilizing chunked streaming buffers rather than in-memory buffering.
- Concurrent distribution must throttle thread allocation to avoid socket exhaustion.

### NFR2: Portability & Cross-Platform Support
- Backend must run on any OS with Java 21+ / 24 LTS (Windows, macOS, Linux, Raspberry Pi).
- Frontend must be accessible through modern web browsers on desktops, tablets, and smartphones.

### NFR3: Security & File Integrity
- Path traversal protection: Senders cannot dictate arbitrary destination paths on receivers; all incoming files are validated and stored in a designated quarantine/downloads directory.
- Collision avoidance: Receiver will append timestamp/indices if a file with the same name exists, preventing accidental overwrite.
- Cryptographic verification: SHA-256 verification guarantees that received files have not been corrupted during transmission.

### NFR4: Usability & Responsiveness
- The web interface must be mobile-first and responsive down to 320px viewport widths.
- Real-time visual progress bars, transfer speeds, peer badges, and toast alerts must provide clear UX feedback.

---

## 4. Scope, Assumptions & Limitations

### 4.1 Scope
- Covers local LAN and Wi-Fi hotspot environments.
- Implements custom binary framing over Java TCP sockets and broadcast packets over UDP sockets.
- Provides a Next.js web application interfacing with the local Java agent.

### 4.2 Architectural Reality & Assumptions
- **Browser Socket Limitation**: Standard web browsers (Chrome, Safari, Firefox) cannot directly open raw TCP `ServerSocket` or UDP `DatagramSocket` connections due to sandbox security policies.
- **Node Topology**: Therefore, each participating computer or device runs a lightweight local OmniDrop Java backend service. The Next.js frontend runs in the browser and controls its local Java backend via HTTP REST and WebSockets.
- If a mobile phone only accesses the web UI hosted on a computer, it can view and trigger transfers on behalf of that computer. To act as an independent TCP receiver, the device must run the Java runtime environment.
