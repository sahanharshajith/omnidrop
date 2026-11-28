# OmniDrop 🚀
### Mobile-Responsive Offline Peer-to-Peer File Sharing and One-to-Many Distribution Web Application Using Java TCP, UDP, and WebSockets

[![OmniDrop CI](https://github.com/omnidrop/omnidrop/actions/workflows/ci.yml/badge.svg)](https://github.com/omnidrop/omnidrop/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B%20%2F%2024%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-15%2B%20App%20Router-black.svg)](https://nextjs.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-CSS-38bdf8.svg)](https://tailwindcss.com/)

---

## 1. Project Overview & Problem Statement

Transferring files between devices is difficult or impossible when internet connectivity is unavailable. Mainstream cloud-based file-sharing platforms (Google Drive, Dropbox, WeTransfer) strictly rely on active WAN access. Furthermore, transferring files to multiple devices individually through ad-hoc point-to-point transfers is time-consuming and inefficient.

**OmniDrop** provides a resilient, high-performance offline file-sharing system enabling devices on the same local network (Wi-Fi or mobile hotspot) to:
1. **Discover each other automatically** using UDP broadcast sockets.
2. **Transfer files reliably point-to-point** using structured, framed TCP streams.
3. **Distribute files simultaneously to multiple receivers** using concurrent TCP connection pools.
4. **Resume interrupted transfers** using byte-level checkpoints and verify file integrity via SHA-256.
5. **Monitor operations in real time** through a responsive, mobile-first web interface driven by Java WebSockets.

---

## 2. Key Architecture & Network Design

``` 
+-----------------------------------------------------------------------------------------+
|                                    Next.js Web Frontend                                 |
|                       (Desktop, Laptop, Tablet, Mobile Web Browser)                     |
+--------------------------------------------+--------------------------------------------+
                                              | HTTP REST (:8080) & WebSocket (/ws/transfers)
+--------------------------------------------v--------------------------------------------+
|                                  Java Spring Boot Backend                               |
|                                                                                         |
|   +-----------------------+   +-----------------------+   +-------------------------+   |
|   | UDP Discovery Module  |   | TCP File Sender Mod.  |   | TCP File Receiver Mod.  |   |
|   | (DatagramSocket:8888) |   | (Socket Client)       |   | (ServerSocket:9090)     |   |
|   +-----------------------+   +-----------------------+   +-------------------------+   |
|                                                                                         |
|   +-----------------------+   +-----------------------+   +-------------------------+   |
|   | One-to-Many Manager   |   | Transfer Recovery     |   | WebSocket Notifier      |   |
|   | (ExecutorService)     |   | (FileChannel / SHA256)|   | (Real-time events)      |   |
|   +-----------------------+   +-----------------------+   +-------------------------+   |
+-----------------------------------------------------------------------------------------+
```

### Why a Local Java Agent?
Web browsers running on mobile phones and laptops cannot directly open arbitrary TCP `ServerSocket` listeners or broadcast UDP datagrams due to browser sandbox security policies. 

OmniDrop solves this practically and cleanly: **each participating node runs a lightweight Java backend agent**, which handles genuine Java network socket programming. The responsive Next.js web application acts as its user interface, communicating via local WebSockets and REST endpoints.

---

## 3. Mandatory Functional Features & Java Implementation

| # | Mandatory Feature | Java Networking APIs & Concepts | Primary Classes |
| :- | :--- | :--- | :--- |
| **1** | **Offline LAN Operation** | Direct IPv4 subnet addressing, zero internet or cloud dependency | `NetworkInterface`, `InetAddress` |
| **2** | **UDP Device Discovery** | `DatagramSocket`, `DatagramPacket`, UDP broadcast, timeout handling | `UdpDiscoveryService`, `UdpBeaconBroadcaster` |
| **3** | **TCP File Transfer** | `Socket`, `InputStream`, `OutputStream`, 20-byte binary framing | `TcpFileSenderService`, `FrameEncoder` |
| **4** | **One-to-Many Distribution** | `ExecutorService`, concurrent TCP sockets, thread pooling, synchronization | `OneToManyDistributionManager`, `ConcurrentTransferWorker` |
| **5** | **Transfer Recovery & Integrity** | `FileChannel`, `RandomAccessFile`, byte checkpoints, SHA-256 verification | `TransferRecoveryManager`, `CheckpointStore` |
| **6** | **Real-Time Synchronization** | Spring WebSocket handler (`/ws/transfers`), JSON event dispatching | `WebSocketNotificationService`, `TransferWebSocketHandler` |

---

## 4. Five-Member Group Allocation Matrix

| Member | Focus Area | Network Concepts | Assigned Java Classes |
| :--- | :--- | :--- | :--- |
| **Member 1** | **UDP Discovery** | `DatagramSocket`, `DatagramPacket`, Broadcast | `UdpDiscoveryService`, `UdpPacketListener` |
| **Member 2** | **TCP File Sender** | `Socket`, Binary Framing, Chunk streaming | `TcpFileSenderService`, `FrameEncoder` |
| **Member 3** | **TCP File Receiver**| `ServerSocket`, `Socket`, Path validation | `TcpFileReceiverService`, `FrameDecoder` |
| **Member 4** | **One-to-Many** | `ExecutorService`, Concurrent TCP Sockets | `OneToManyDistributionManager` |
| **Member 5** | **Recovery** | `FileChannel`, Checkpoints, SHA-256 | `TransferRecoveryManager`, `CheckpointStore` |

---

## 5. Technology Stack

- **Frontend**: Next.js 15+ (App Router), React 19, TypeScript, Tailwind CSS, Lucide Icons.
- **Backend**: Java 21+ / Java 24 LTS, Spring Boot 3.4.x, Maven, Spring WebSocket, JUnit 5.
- **Protocols**: UDP Broadcast (8888), Framed TCP Streaming (9090), HTTP REST (8080), WebSockets (/ws/transfers).
- **Tooling**: Docker & Docker Compose, Git, GitHub Actions.

---

## 6. Getting Started

### Prerequisites
- Java JDK 21 or 24 LTS installed (`java -version`)
- Apache Maven 3.8+ installed (`mvn -version`)
- Node.js 20+ installed (`node -v`)

### Quick Start (Development Mode)

#### 1. Start the Java Backend Agent
```bash
cd backend
mvn clean compile
mvn spring-boot:run
```
*Backend runs at `http://localhost:8080` (TCP socket listening on port `9090`, UDP discovery on port `8888`).*

#### 2. Start the Responsive Web UI
```bash
cd frontend
npm install
npm run dev
```
*Frontend runs at `http://localhost:3000`.*

#### 3. Run the Test Suites
```bash
# Backend — JUnit 5 + Mockito (includes WebSocket handler tests)
cd backend
mvn test
# Frontend — Vitest + Testing Library (WebSocket singleton, React hook, UI card)
cd frontend
npm test
# Optional: frontend coverage report (generated in frontend/coverage/)
npm run test:coverage
```
*See [docs/TESTING.md](docs/TESTING.md) for the full strategy, per-suite breakdown, and manual multi-device checklist.*

---

## 7. Running with Docker

You can easily run both services in containers:
```bash
docker compose up --build
```

> **Note on device discovery in Docker:** the compose file uses a bridge network, and UDP broadcasts from inside a bridge network do not reach the real LAN. For multi-device demos run the backend natively (`mvn spring-boot:run`). On Linux you can instead set `network_mode: host` on the backend service; Docker Desktop on Windows/macOS does not support this the same way.

---

## 8. Offline Multi-Device Testing

To share files between two physical laptops on an offline local network:
1. Connect both devices to the same local Wi-Fi or mobile hotspot.
2. Ensure firewall ports `8888` (UDP) and `9090`, `8080`, `3000` (TCP) are allowed.
3. Launch OmniDrop on both devices.
4. Device discovery will automatically populate active nodes on the dashboard within 3 seconds.
5. Select a file, choose target peers, and initiate transfer or one-to-many distribution!

---

## 9. Detailed Documentation Links

- 📖 [Project Requirements & Scope](docs/PROJECT_REQUIREMENTS.md)
- 🏛️ [System Architecture & Mermaid Diagrams](docs/ARCHITECTURE.md)
- 📡 [Network Protocol Specification (TCP Framing & UDP)](docs/NETWORK_PROTOCOL.md)
- ⚡ [WebSocket Real-Time Protocol Specification](docs/WEBSOCKET_PROTOCOL.md)
- 🔗 [WebSocket Integration Guide (Backend ↔ Frontend)](docs/WEB_SOCKET_INTEGRATION.md)
- 👥 [Team Contribution & Group Allocation](docs/TEAM_CONTRIBUTIONS.md)
- 🛠️ [Development Setup Guide](docs/DEVELOPMENT_SETUP.md)
- 🧪 [Testing & Verification Guide](docs/TESTING.md)
- 🗺️ [Project Plan & Milestones](docs/PROJECT_PLAN.md)
- 🔀 [Git Contribution & Branching Workflow](docs/GIT_WORKFLOW.md)
