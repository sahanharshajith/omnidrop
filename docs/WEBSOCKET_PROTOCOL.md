# OmniDrop – WebSocket Protocol Specification

This document details the real-time event protocol between the local Java backend and the Next.js frontend web interface.

---

## 1. Connection Details
- **Endpoint**: `ws://<host>:8080/ws/transfers`
- **Sub-protocol**: Raw WebSocket JSON messaging (or STOMP over SockJS fallback).
- **Heartbeat**: Ping/Pong every 10 seconds to maintain alive state.
- **Reconnection Policy**: Frontend will automatically attempt reconnection with exponential backoff (1s, 2s, 5s, max 10s).

---

## 2. Event Format

All messages sent over the WebSocket channel use a standard envelope format:

```json
{
  "type": "EVENT_TYPE_STRING",
  "timestamp": 1731254400000,
  "payload": { ... }
}
```

---

## 3. Server-to-Client Events (Backend -> Frontend)

### 3.1 `DEVICE_DISCOVERED`
Emitted by the UDP Discovery Service when a new peer is found, **and again when a known peer's IP address, name or TCP port changes** (clients should treat it as an upsert by `deviceId`). The payload is the backend `PeerDevice`.
```json
{
  "type": "DEVICE_DISCOVERED",
  "timestamp": 1731254401000,
  "payload": {
    "deviceId": "b18a24-9f20-41b2-a4",
    "deviceName": "Bob-MacBook-Pro",
    "ipAddress": "192.168.1.105",
    "tcpPort": 9090,
    "httpPort": 8080,
    "lastSeenTimestamp": 1731254401000,
    "active": true
  }
}
```

### 3.2 `DEVICE_LOST`
Emitted when a peer has not broadcast a heartbeat within 12 seconds, or immediately when it sends a `LEAVE` beacon on shutdown.
```json
{
  "type": "DEVICE_LOST",
  "timestamp": 1731254416000,
  "payload": {
    "deviceId": "b18a24-9f20-41b2-a4"
  }
}
```

### 3.3 `TRANSFER_STARTED`
Emitted when a TCP file transfer session is accepted and begins streaming.
```json
{
  "type": "TRANSFER_STARTED",
  "timestamp": 1731254420000,
  "payload": {
    "transferId": "xfer-77312",
    "role": "SENDER",
    "peerName": "Bob-MacBook-Pro",
    "peerIp": "192.168.1.105",
    "fileName": "lecture-recording.mp4",
    "fileSize": 52428800,
    "totalChunks": 800,
    "resumed": false
  }
}
```

### 3.4 `TRANSFER_PROGRESS`
Pushed periodically (e.g. every 200ms or 10 chunks) to render smooth UI progress.
```json
{
  "type": "TRANSFER_PROGRESS",
  "timestamp": 1731254422000,
  "payload": {
    "transferId": "xfer-77312",
    "bytesTransferred": 26214400,
    "totalBytes": 52428800,
    "percentage": 50.0,
    "currentChunk": 400,
    "totalChunks": 800,
    "speedBytesPerSec": 10485760,
    "speedFormatted": "10.0 MB/s",
    "estimatedRemainingSeconds": 2.5
  }
}
```

### 3.5 `TRANSFER_COMPLETED`
Emitted upon successful EOF and SHA-256 integrity validation.
```json
{
  "type": "TRANSFER_COMPLETED",
  "timestamp": 1731254425000,
  "payload": {
    "transferId": "xfer-77312",
    "fileName": "lecture-recording.mp4",
    "totalBytes": 52428800,
    "durationMs": 4820,
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "verified": true,
    "savedPath": "/downloads/lecture-recording.mp4"
  }
}
```

### 3.6 `TRANSFER_INTERRUPTED`
Emitted if a socket abruptly closes or encounters an IO exception.
```json
{
  "type": "TRANSFER_INTERRUPTED",
  "timestamp": 1731254423000,
  "payload": {
    "transferId": "xfer-77312",
    "lastSavedOffset": 26214400,
    "lastChunkIndex": 400,
    "errorMessage": "Socket connection reset by peer",
    "canResume": true
  }
}
```

### 3.7 `DISTRIBUTION_PROGRESS`
Emitted during one-to-many distribution tracking aggregate and per-node progress.
```json
{
  "type": "DISTRIBUTION_PROGRESS",
  "timestamp": 1731254424000,
  "payload": {
    "distributionId": "dist-9941",
    "fileName": "assignment_materials.zip",
    "totalReceivers": 3,
    "completedReceivers": 1,
    "activeReceivers": 2,
    "failedReceivers": 0,
    "receiverStatuses": [
      { "deviceId": "dev-1", "name": "Bob", "percentage": 100.0, "status": "COMPLETED" },
      { "deviceId": "dev-2", "name": "Charlie", "percentage": 68.5, "status": "TRANSFERRING" },
      { "deviceId": "dev-3", "name": "Diana", "percentage": 72.0, "status": "TRANSFERRING" }
    ]
  }
}
```

---

## 4. Client-to-Server Commands (Frontend -> Backend)

While main operations (like initiating a transfer) can occur via REST, clients can also send interactive control commands over the WebSocket:

| Command | Description |
| :--- | :--- |
| `TRIGGER_DISCOVERY_PING` | Request immediate UDP discovery broadcast cycle |
| `CANCEL_TRANSFER` | Instruct backend to abort an active TCP socket transfer |
| `RESUME_TRANSFER` | Request resumption of an interrupted transfer by ID |
