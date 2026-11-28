# OmniDrop – System Architecture Specification

## 1. High-Level Architecture Overview

OmniDrop is architected as a distributed local-network system where each node pairs a **Java Network Programming Agent** with a modern **Next.js Web Frontend**. 

Standard browser sandboxes prohibit direct access to raw TCP/UDP sockets. To preserve authentic Java network programming while delivering an intuitive browser UI across desktop, tablet, and mobile devices, each participating node runs the Java backend agent locally.

```mermaid
graph TB
    subgraph BrowserNodeA["Device A (Host/Sender)"]
        UI_A["Next.js Responsive UI<br/>(Desktop / Mobile Browser)"]
        WS_Client_A["WebSocket Client / REST"]
        UI_A <--> WS_Client_A
    end

    subgraph BackendNodeA["Java Backend Agent A"]
        SpringA["Spring Boot Core<br/>REST & WebSocket Service"]
        UDP_A["UDP Discovery Module<br/>(DatagramSocket: 8888)"]
        TCP_Send_A["TCP Sender Module<br/>(Socket Client)"]
        Dist_A["One-to-Many Manager<br/>(ExecutorService)"]
        Recov_A["Transfer Recovery<br/>(FileChannel / Checkpoints)"]
        
        WS_Client_A <-->|"WS /ws/transfers<br/>HTTP REST :8080"| SpringA
        SpringA --> UDP_A
        SpringA --> Dist_A
        Dist_A --> TCP_Send_A
        Dist_A --> Recov_A
    end

    subgraph LocalAreaNetwork["Local Network / Wi-Fi Hotspot (Offline, No Internet)"]
        UDP_Broadcast(("UDP Broadcast<br/>255.255.255.255:8888"))
        TCP_Stream_1[["TCP File Stream<br/>Port: 9090 (Direct)"]]
        TCP_Stream_2[["TCP File Stream<br/>Port: 9090 (Direct)"]]
    end

    subgraph BackendNodeB["Java Backend Agent B (Receiver 1)"]
        TCP_Recv_B["TCP Receiver Module<br/>(ServerSocket: 9090)"]
        UDP_B["UDP Discovery Module<br/>(DatagramSocket: 8888)"]
        SpringB["Spring Boot Core & WS"]
        
        TCP_Recv_B --> SpringB
        UDP_B --> SpringB
    end

    subgraph BrowserNodeB["Device B UI"]
        UI_B["Next.js Responsive UI"]
        UI_B <-->|"WS /ws/transfers"| SpringB
    end

    subgraph BackendNodeC["Java Backend Agent C (Receiver 2)"]
        TCP_Recv_C["TCP Receiver Module<br/>(ServerSocket: 9090)"]
        UDP_C["UDP Discovery Module<br/>(DatagramSocket: 8888)"]
        SpringC["Spring Boot Core & WS"]
    end

    UDP_A <--> UDP_Broadcast
    UDP_Broadcast <--> UDP_B
    UDP_Broadcast <--> UDP_C

    TCP_Send_A -->|Transfer 1| TCP_Stream_1 --> TCP_Recv_B
    TCP_Send_A -->|Transfer 2| TCP_Stream_2 --> TCP_Recv_C
```

---

## 2. Multi-Protocol Tiering

OmniDrop cleanly separates concerns across four distinct protocol layers:

| Layer | Protocol | Role in OmniDrop | Implementation |
| :--- | :--- | :--- | :--- |
| **Discovery** | **UDP Sockets** | Dynamic peer beaconing, heartbeat, node leave | `DatagramSocket`, `DatagramPacket` |
| **Data Transfer** | **TCP Sockets** | Reliable chunked binary file streaming, framing, ACKs | `Socket`, `ServerSocket`, `FileChannel` |
| **Local Real-Time** | **WebSockets** | Instant transfer progress, speeds, active peer table | Spring WebSocket handler (`/ws/transfers`) |
| **Control / Config** | **HTTP REST** | File upload to agent, transfer initiation, cancel commands | Spring Boot REST Controllers (`/api/v1/...`) |

---

## 3. Core Backend Modular Architecture

The Java backend (`com.omnidrop`) is structured into clean, decoupled modules designed for group collaboration:

```mermaid
classDiagram
    class UdpDiscoveryService {
        -DatagramSocket listenerSocket
        -DatagramSocket broadcastSocket
        -PeerRegistry registry
        +start()
        +stop()
        +registerPeer(PeerDevice)
        +unregisterPeer(deviceId)
        +triggerManualBroadcast()
        +getActivePeers() List~PeerDevice~
    }

    class PeerRegistry {
        -Map~String, PeerDevice~ peers
        +upsert(PeerDevice) Result
        +evictStale(timeoutMs) List~PeerDevice~
        +remove(deviceId)
        +snapshot() List~PeerDevice~
    }

    class TcpFileSenderService {
        +sendOffer(peer, fileMetadata) boolean
        +streamFileChunks(transferId, file, startOffset)
    }

    class TcpFileReceiverService {
        -ServerSocket serverSocket
        +startListening(port)
        +handleIncomingConnection(Socket clientSocket)
        +validatePath(filename) Path
    }

    class OneToManyDistributionManager {
        -ExecutorService threadPool
        +distributeFile(file, List~PeerDevice~ targets)
        +abortTransfer(transferId)
    }

    class TransferRecoveryManager {
        -Map~String, Checkpoint~ checkpoints
        +saveCheckpoint(transferId, offset, chunkIndex)
        +requestResume(transferId, peer)
        +verifyChecksum(filePath, expectedSha256) boolean
    }

    class WebSocketNotificationService {
        -SimpMessagingTemplate or WebSocketHandler
        +publishPeerDiscovered(PeerDevice)
        +publishTransferProgress(ProgressUpdate)
        +publishTransferCompleted(TransferResult)
    }

    OneToManyDistributionManager --> TcpFileSenderService : coordinates concurrent
    TcpFileSenderService ..> TransferRecoveryManager : checks offset & hash
    TcpFileReceiverService ..> TransferRecoveryManager : stores checkpoints & verifies
    TcpFileSenderService ..> WebSocketNotificationService : emits progress
    TcpFileReceiverService ..> WebSocketNotificationService : emits progress
    UdpDiscoveryService ..> WebSocketNotificationService : emits peer status
    UdpDiscoveryService --> PeerRegistry : stores peers
```

---

## 4. Frontend Architecture (Next.js App Router)

The frontend is a single-page reactive application built with Next.js App Router, TypeScript, and Tailwind CSS.

- **`app/page.tsx`**: Unified responsive dashboard displaying system health, discovery table, active transfers, and one-to-many launcher.
- **`app/discovery/page.tsx`**: Dedicated real-time radar and peer device manager.
- **`app/transfer/page.tsx`**: Single-file upload and point-to-point transfer console.
- **`app/distribution/page.tsx`**: One-to-many broadcast console with individual target progress monitors.
- **`app/recovery/page.tsx`**: Checkpoint inspector and manual/automatic resume center.
- **`hooks/useWebSocket.ts`**: Reconnecting WebSocket listener that dispatches real-time events into local React states.
- **`services/api.ts`**: Clean REST client interfacing with the local Java backend.
