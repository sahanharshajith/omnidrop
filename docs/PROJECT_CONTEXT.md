# OmniDrop – Full Project Context

> A single document that explains what OmniDrop is, how it is built, how the pieces talk to each other, how to run it, and where the project currently stands. Written from a full read of the repository on 19 Nov 2026.

---

## 1. What OmniDrop is

OmniDrop is a **Network Programming group project** (5 members). It is an **offline, peer‑to‑peer file sharing app** for devices on the same local network (Wi‑Fi router or mobile hotspot), with **no internet or cloud** involved.

It can:

1. **Find other devices automatically** using UDP broadcast (Member 1).
2. **Send a file to one device** over a custom framed TCP protocol (Members 2 and 3).
3. **Send one file to many devices at the same time** using a Java thread pool (Member 4).
4. **Resume interrupted transfers** and **verify files with SHA‑256** (Member 5).
5. **Show everything live in a web UI** through WebSockets.

The key design idea: browsers cannot open raw TCP/UDP sockets, so **every device runs a small Java (Spring Boot) backend "agent"** that does the real socket programming, and a **Next.js web UI** controls that local agent over HTTP REST and a WebSocket.

---

## 2. Tech stack

| Layer | Technology |
| :-- | :-- |
| Backend | Java 21 (24 LTS also supported), Spring Boot 3.4.2, Maven, Spring WebSocket, Jackson, JUnit 5 + Mockito |
| Frontend | Next.js 15 (App Router), React 19, TypeScript, Tailwind CSS, lucide-react icons, Vitest + Testing Library |
| Protocols | UDP broadcast (port 8888), framed TCP (port 9090), HTTP REST (port 8080), WebSocket (`/ws/transfers` on 8080) |
| Tooling | Docker + docker-compose, Git (feature branches → `develop` → `main`), GitHub Actions CI (referenced in README) |

---

## 3. Ports and protocols at a glance

| Port | Protocol | Who uses it | Purpose |
| :-- | :-- | :-- | :-- |
| 8888 | UDP | `UdpDiscoveryService` | Discovery beacons (broadcast every 3 s) |
| 9090 | TCP | `TcpFileReceiverService` (server), `TcpFileSenderService` (client) | File transfer with the 20‑byte frame protocol |
| 8080 | HTTP + WS | Spring Boot | REST API `/api/v1/...` and WebSocket `/ws/transfers` |
| 3000 | HTTP | Next.js | Web UI |

---

## 4. Architecture

```
            Browser (desktop / tablet / phone)
            Next.js UI  :3000
                 |  REST :8080/api/v1/...    WebSocket :8080/ws/transfers
                 v
   +--------------------- Java Spring Boot agent (one per device) ----------------------+
   |  UDP Discovery (M1)    TCP Sender (M2)      TCP Receiver (M3)                        |
   |  DatagramSocket:8888   Socket client        ServerSocket:9090                        |
   |                                                                                       |
   |  One-to-Many (M4)      Recovery (M5)        WebSocketNotificationService             |
   |  ExecutorService       FileChannel/SHA-256  (pushes events to all browser sessions)  |
   +---------------------------------------------------------------------------------------+
                 |  UDP broadcast 255.255.255.255:8888      TCP direct to peer:9090
                 v
            Other devices running the same agent
```

Layer split:

| Layer | Protocol | Role |
| :-- | :-- | :-- |
| Discovery | UDP | Peer beacons, heartbeat, peer expiry |
| Data transfer | TCP | Chunked binary file streaming with framing and ACKs |
| Real‑time UI | WebSocket | Live peers, progress, speed, errors |
| Control | HTTP REST | Upload file to the agent, start transfer/distribution, list peers, list checkpoints |

---

## 5. Repository layout

```
omnidrop/
├── README.md, CONTRIBUTING.md, LICENSE, docker-compose.yml
├── docs/                      Requirements, architecture, protocols, plan, testing, git workflow
├── scripts/                   build-all / start-all (.sh and .bat)
├── backend/                   Spring Boot agent (Maven)
│   └── src/main/java/com/omnidrop/
│       ├── OmniDropApplication.java   entry point; starts TCP receiver + UDP discovery at boot
│       ├── config/            CorsConfig, WebSocketConfig, OmniDropProperties (typed config)
│       ├── controller/        REST controllers (health, devices, transfer, distribution, recovery, files)
│       ├── discovery/         MEMBER 1 – UdpDiscoveryService, UdpBeaconBroadcaster, UdpPacketListener
│       ├── sender/            MEMBER 2 – TcpFileSenderService
│       ├── receiver/          MEMBER 3 – TcpFileReceiverService, ReceiverConnectionHandler
│       ├── distribution/      MEMBER 4 – OneToManyDistributionManager/Service, ConcurrentTransferWorker
│       ├── recovery/          MEMBER 5 – TransferRecoveryManager, CheckpointStore
│       ├── protocol/          Frame header/encoder/decoder/opcodes/constants (shared M2/M3)
│       ├── model/             PeerDevice, FileMetadata, TransferProgress, TransferState, Checkpoint
│       ├── utils/             ChecksumUtils, NetworkInterfaceUtils, PathSecurityUtils
│       └── websocket/         TransferWebSocketHandler, WebSocketEvent, WebSocketNotificationService
│   └── src/test/java/com/omnidrop/   Tests (protocol, receiver, path security, checksum, websocket, TCP)
└── frontend/                  Next.js app
    ├── app/                   Pages: / (dashboard), /discovery, /transfer, /received, /distribution, /recovery
    ├── components/            PeerCard, TransferProgressCard, IncomingTransferModal, ReceivedFilesList, ...
    ├── hooks/useWebSocket.ts  React hook over the shared socket
    ├── lib/websocket.ts       Singleton WebSocket client with auto‑reconnect
    ├── services/api.ts        REST client for the local agent
    ├── types/index.ts         Shared TS types (PeerDevice, TransferProgress, Checkpoint, ...)
    └── __tests__/             Vitest suites
```

---

## 6. Configuration (`backend/src/main/resources/application.properties`)

| Property | Default | Meaning |
| :-- | :-- | :-- |
| `server.port` | 8080 | REST + WebSocket |
| `omnidrop.node.name` | `OmniDrop-Node` | Display name sent in beacons |
| `omnidrop.node.id` | empty → random UUID at startup | Unique node ID |
| `omnidrop.network.tcp-port` | 9090 | TCP receiver port |
| `omnidrop.network.udp-port` | 8888 | UDP discovery port |
| `omnidrop.network.broadcast-interval-ms` | 3000 | Beacon interval |
| `omnidrop.network.peer-timeout-ms` | 12000 | Peer is evicted after this much silence |
| `omnidrop.transfer.chunk-size-bytes` | 65536 | 64 KB chunks |
| `omnidrop.transfer.socket-timeout-ms` | 15000 | TCP read timeout |
| `omnidrop.storage.downloads-dir` | `downloads` | Where received files go |
| `omnidrop.storage.checkpoints-dir` | `checkpoints` | Where `.chk` checkpoints go |
| multipart limits | 5 GB | Max file the UI can upload to the agent |

Any property can be overridden on the command line, e.g. `mvn spring-boot:run -Dspring-boot.run.arguments="--omnidrop.node.name=Thiloni-Laptop"`.

---

## 7. How it starts up

`OmniDropApplication` has a `CommandLineRunner` that, after Spring starts:

1. Logs node name, node ID, TCP and UDP ports.
2. Lists local IPv4 addresses via `NetworkInterfaceUtils.getLocalIPv4Addresses()`.
3. Starts the TCP receiver `ServerSocket` on 9090 (`TcpFileReceiverService.startServer()`).
4. Starts UDP discovery (`UdpDiscoveryService.start()`): binds the listener on 8888, starts the listener thread, schedules beacons every 3 s and stale‑peer eviction every 5 s.

---

## 8. The protocols in detail

### 8.1 UDP discovery (Member 1)

* Every 3 s each node sends a JSON datagram to every IPv4 broadcast address of every active, non‑loopback interface (fallback `255.255.255.255`), on port 8888:

```json
{ "magic": "OMNI_DISCOVER", "version": 1, "deviceId": "<uuid>", "deviceName": "Alice-ThinkPad",
  "tcpPort": 9090, "httpPort": 8080, "timestamp": 1731254400000, "event": "ALIVE" }
```

* The listener (`setSoTimeout(1000)`) validates every datagram with `BeaconCodec` (magic, version, required fields, ports, event), takes the sender IP from `DatagramPacket.getAddress()`, ignores its own `deviceId`, and stores the peer in `PeerRegistry` (a `ConcurrentHashMap`).
* New peer, or a known peer whose IP/name/port changed → WebSocket `DEVICE_DISCOVERED`. Peer silent for 12 s, or a `"event": "LEAVE"` beacon on shutdown → removed and WebSocket `DEVICE_LOST`.
* REST: `GET /api/v1/devices` (current peers), `POST /api/v1/devices/broadcast` (send a beacon now). WebSocket command `TRIGGER_DISCOVERY_PING` does the same as the POST.

### 8.2 Framed TCP transfer (Members 2 and 3)

Every frame = **20‑byte header + payload**:

| Bytes | Field |
| :-- | :-- |
| 0–3 | Magic `0x4F4D4E49` ("OMNI") |
| 4 | Version (1) |
| 5 | Opcode |
| 6–7 | Reserved (0) |
| 8–11 | Chunk index (big‑endian) |
| 12–15 | Payload length (big‑endian) |
| 16–19 | CRC32 of payload |

Opcodes: `HELLO 0x01`, `FILE_OFFER 0x02`, `ACCEPT 0x03`, `REJECT 0x04`, `FILE_CHUNK 0x05`, `ACK 0x06`, `RESUME_REQ 0x07`, `TRANSFER_COMP 0x08`, `ERROR 0x09`.

Normal flow: connect to peer:9090 → `HELLO`/`ACK` → `FILE_OFFER` (JSON name, size, SHA‑256, transferId) → `ACCEPT` (start offset) → loop `FILE_CHUNK`/`ACK` (64 KB) → `TRANSFER_COMP` → receiver checks SHA‑256 → `ACK` → close. Receiver writes to a `.part` file, sanitises the filename against path traversal, and avoids overwriting existing files.

### 8.3 Recovery (Member 5)

If the connection drops, a checkpoint (offset + last chunk index) is stored. On resume the sender seeks with `FileChannel`/`RandomAccessFile` to the saved offset and continues; the final file is verified with SHA‑256 and `.part` is renamed to the final name.

### 8.4 One‑to‑many (Member 4)

`OneToManyDistributionManager` uses a fixed thread pool (8 threads). Each target gets its own `ConcurrentTransferWorker` running an isolated TCP transfer, so one slow/failed receiver does not affect the others. Aggregate progress is pushed as `DISTRIBUTION_PROGRESS` and `DISTRIBUTION_COMPLETED`.

### 8.5 WebSocket events (backend → browser)

Envelope: `{ "type": "...", "timestamp": <ms>, "payload": {...} }`

| Event | Sent when |
| :-- | :-- |
| `DEVICE_DISCOVERED` | New peer found (payload = `PeerDevice`) |
| `DEVICE_LOST` | Peer timed out (payload = `{deviceId}`) |
| `TRANSFER_STARTED` / `TRANSFER_PROGRESS` / `TRANSFER_COMPLETED` / `TRANSFER_FAILED` / `TRANSFER_INTERRUPTED` | TCP transfer lifecycle |
| `DISTRIBUTION_PROGRESS` / `DISTRIBUTION_COMPLETED` | One‑to‑many lifecycle |

Browser → backend commands: `TRIGGER_DISCOVERY_PING`, `CANCEL_TRANSFER {transferId}`, `RESUME_TRANSFER {transferId}`.

---

## 9. REST API summary

| Method | Path | Owner | Purpose |
| :-- | :-- | :-- | :-- |
| GET | `/api/v1/health` | shared | Node name, ID, ports, IPs |
| GET | `/api/v1/devices` | M1 | Discovered peers |
| POST | `/api/v1/devices/broadcast` | M1 | Send a discovery beacon now |
| POST | `/api/v1/transfer/send` | M2 | Multipart upload + target IP/port → start transfer |
| POST | `/api/v1/distribution/start` | M4 | Multipart upload + `targetsJson` → start one‑to‑many |
| GET | `/api/v1/recovery/checkpoints[/{id}]` | M5 | Interrupted transfers |
| GET/DELETE | `/api/v1/files...` | M3 | Received files, download, delete, storage info |

---

## 10. Frontend pages

| Route | What it shows |
| :-- | :-- |
| `/` | Dashboard: node health, peer list, active transfers |
| `/discovery` | Member 1's page: peer grid (`PeerCard`), "Scan Subnet Now" button, select a peer → "Send File to This Device" |
| `/transfer` | Pick file + peer, send, watch `TransferProgressCard` |
| `/received` | Received files list, storage path card |
| `/distribution` | Multi‑select peers, one‑to‑many progress |
| `/recovery` | Checkpoints and resume |

`lib/websocket.ts` is one shared socket with exponential‑backoff reconnect; `hooks/useWebSocket.ts` exposes `{ isConnected, lastMessage }` to components.

---

## 11. How to run

**Dev mode (per device):**
```bash
cd backend && mvn clean compile && mvn spring-boot:run   # agent on :8080, TCP :9090, UDP :8888
cd frontend && npm install && npm run dev                 # UI on :3000
```

**Tests:**
```bash
cd backend && mvn test
cd frontend && npm test          # or npm run test:coverage
```

**Docker:** `docker compose up --build`. Note: the compose file uses a bridge network. UDP broadcasts inside a bridge network do not reach the real LAN, so for multi‑device discovery run the backend natively (or `network_mode: host` on Linux). This is noted in the README.

**Multi‑device test:** all devices on the same Wi‑Fi/hotspot, allow UDP 8888 and TCP 9090/8080/3000 through the firewall (Windows Defender Firewall will prompt on first run; allow on "Private" networks), start the agent + UI on each, peers should appear within ~3 s.

---

## 12. Team split (from README §4 and `docs/TEAM_CONTRIBUTIONS.md`)

| Member | Area | Java classes | Frontend | Branch |
| :-- | :-- | :-- | :-- | :-- |
| **1** | **UDP Device Discovery** | `UdpDiscoveryService`, `UdpBeaconBroadcaster`, `UdpPacketListener`, `PeerRegistry` | `DeviceDiscoveryCard`, `RadarView`, `PeerStatusBadge` | `feature/udp-discovery` |
| 2 | TCP File Sender | `TcpFileSenderService`, `FrameEncoder`, `SenderTransferSession` | `FileTransferSenderForm`, `TransferProgressCard`, `SpeedMeter` | `feature/tcp-sender` |
| 3 | TCP File Receiver | `TcpFileReceiverService`, `FrameDecoder`, `ReceiverConnectionHandler` | `IncomingTransferModal`, `ReceivedFilesList`, `PathSettingCard` | `feature/tcp-receiver` |
| 4 | One‑to‑Many | `OneToManyDistributionManager`, `ConcurrentTransferWorker`, `DistributionTracker` | `MultiSelectPeerList`, `DistributionGrid`, `OverallProgressMeter` | `feature/one-to-many` |
| 5 | Recovery & Integrity | `TransferRecoveryManager`, `CheckpointStore`, `ChecksumValidator`, `ResumeProtocolHandler` | `InterruptedTransfersList`, `ResumeActionCard`, `IntegrityBadge` | `feature/transfer-recovery` |

Every member must, at the demo: walk through their socket code line by line, show their unit tests passing, show Wireshark/log traces of their packets, and explain how their part connects to the WebSocket layer and UI.

---

## 13. Where the project stands (19 Nov 2026)

Timeline from `docs/PROJECT_PLAN.md`: Phase 2 core sockets (M1–M3) 10–15 Nov; Phase 3 advanced features + WebSocket/UI 15–21 Nov; **multi‑device testing 22–25 Nov**; security audit 24–25 Nov; **demo prep and final report 26–28 Nov**.

Observed state of the code:

* **Member 3 (receiver)** has the most finished work: full handler, path security, received‑files UI, and tests (`FrameDecoderTest`, `ReceiverConnectionHandlerTest`, `PathSecurityUtilsTest`).
* **Member 2 / WebSocket** work has tests too (`TransferWebSocketHandlerTest`, frontend Vitest suites).
* **Member 1 (discovery)** is complete in code (28 Nov): `PeerRegistry`, receive timeout + beacon validation, clean shutdown with a LEAVE beacon, `BeaconCodec`, discovery status in `/api/v1/health`, 48 backend tests (~94% coverage of `com.omnidrop.discovery`), and the `RadarView` / `DeviceDiscoveryCard` / `PeerStatusBadge` UI with 26 frontend tests. Remaining: real multi‑device test + Wireshark evidence, and the PRs. See `docs/MEMBER1_TASK_BRANCHES.md`.
* Members 4 and 5 have base implementations; their dedicated tests from `TESTING.md` are not in the repo yet.

### Doc inconsistencies (fixed on `docs/udp-discovery`)

* Peer timeout is now **12 s** everywhere (it used to say 10 s / 12 s / 15 s in different files).
* `WEBSOCKET_PROTOCOL.md` now shows the real `DEVICE_DISCOVERED` payload (`lastSeenTimestamp`, `active`).
* `ARCHITECTURE.md` class diagram now uses the real method names and includes `PeerRegistry`.
