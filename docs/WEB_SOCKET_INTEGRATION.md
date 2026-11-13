# OmniDrop WebSocket Integration Guide

This guide describes the WebSocket connection between the Java Spring Boot backend and the Next.js frontend. It covers real-time events, the three client commands, the frontend singleton, sender cancellation and resume behavior, and local verification.

## 1. Integration overview

The browser connects to one raw JSON WebSocket endpoint per browser tab:

```text
ws://<backend-host>:8080/ws/transfers
```

The backend broadcasts events to connected sessions. A browser can send commands over the same connection. Commands are fire-and-forget: the current handler does not send a command acknowledgement or result message.

| Message | Direction | Shape |
|---|---|---|
| Event | Backend → browser | `WebSocketEvent`: `type`, `timestamp`, `payload` |
| Command | Browser → backend | JSON object with `command` and any command fields |

The browser does not open TCP or UDP sockets. File transfer and discovery networking are performed by Java services; WebSockets carry UI events and control commands.

## 2. Endpoint and implementation map

| Responsibility | Implementation |
|---|---|
| Register `/ws/transfers` | `backend/src/main/java/com/omnidrop/config/WebSocketConfig.java` |
| Accept sessions, dispatch commands, broadcast events | `backend/src/main/java/com/omnidrop/websocket/TransferWebSocketHandler.java` |
| Event envelope | `backend/src/main/java/com/omnidrop/websocket/WebSocketEvent.java` |
| Publish discovery and transfer events | `backend/src/main/java/com/omnidrop/websocket/WebSocketNotificationService.java` |
| Shared browser connection and command sender | `frontend/lib/websocket.ts` (`transferWs`) |
| Bridge events into React state | `frontend/hooks/useWebSocket.ts` |
| Display progress and send cancel/resume | `frontend/components/TransferProgressCard.tsx` |

In development, the frontend uses `ws://localhost:8080/ws/transfers`. The wrapper also supports `NEXT_PUBLIC_WS_URL`; otherwise it uses the current browser hostname and port 8080. For another device on a LAN, the browser must connect to the backend host's reachable LAN address.

The backend currently registers the endpoint with `setAllowedOrigins("*")`. This is permissive and should be restricted before exposing the service beyond a trusted local development network. The endpoint uses raw WebSocket JSON; the current configuration does not register STOMP or SockJS.

## 3. Event format and event flow

Each event uses this envelope:

```json
{
  "type": "TRANSFER_PROGRESS",
  "timestamp": 1731600000000,
  "payload": {
    "transferId": "example-transfer-id",
    "percentage": 42.0
  }
}
```

`WebSocketNotificationService` creates a `WebSocketEvent` and passes it to `TransferWebSocketHandler.broadcast()`. The handler serializes the event and sends it to each currently open session. It removes sessions when their WebSocket connection closes.

The notification service currently exposes these event types:

| Event | Payload passed by the backend | Typical source |
|---|---|---|
| `DEVICE_DISCOVERED` | `PeerDevice` | UDP discovery |
| `DEVICE_LOST` | Object containing `deviceId` | Peer timeout handling |
| `TRANSFER_STARTED` | `TransferProgress` | Sender or receiver transfer start |
| `TRANSFER_PROGRESS` | `TransferProgress` | Sender or receiver progress update |
| `TRANSFER_COMPLETED` | `TransferProgress` | Transfer completion |
| `TRANSFER_FAILED` | `TransferProgress` | Transfer failure or rejection |
| `TRANSFER_INTERRUPTED` | `TransferProgress` | Cancellation or interrupted transfer |
| `DISTRIBUTION_PROGRESS` | Summary map | One-to-many progress |
| `DISTRIBUTION_COMPLETED` | List of `TransferProgress` | One-to-many completion |

The exact payload fields are defined by the corresponding Java model and can vary by event. See [`WEBSOCKET_PROTOCOL.md`](WEBSOCKET_PROTOCOL.md) for protocol examples.

On the frontend, `transferWs` parses JSON and notifies its subscribers. `useWebSocket()` subscribes on mount, exposes `lastMessage`, and polls the singleton's open state for `isConnected`. Dashboard and transfer pages interpret relevant events and update their React state; `TransferProgressCard` renders the resulting `TransferProgress` values.

## 4. Client commands

Commands are JSON objects with a `command` field:

```json
{
  "command": "CANCEL_TRANSFER",
  "transferId": "example-transfer-id"
}
```

| Command | Required fields | Backend action |
|---|---|---|
| `TRIGGER_DISCOVERY_PING` | None | Calls `UdpDiscoveryService.triggerManualBroadcast()` |
| `CANCEL_TRANSFER` | Non-empty `transferId` | Calls `TcpFileSenderService.cancelTransfer(transferId)` |
| `RESUME_TRANSFER` | Non-empty `transferId` | Calls `TcpFileSenderService.resumeTransfer(transferId)` |

The handler ignores malformed JSON, unknown commands, and cancel/resume requests without a transfer ID. It does not return an acknowledgement. The frontend's `sendCommand()` returns `false` if the socket is not open; it does not queue the command for later delivery.

Example frontend call:

```ts
transferWs.sendCommand("CANCEL_TRANSFER", {
  transferId: "example-transfer-id",
});
```

The discovery UI also has a REST discovery action. The WebSocket discovery command is available to components that choose to use it.

## 5. Sender lifecycle: cancel and resume

The sender tracks active work in an in-memory map keyed by `transferId`. The UI action buttons are shown for sender cards while connecting/transferring (Cancel) and while interrupted (Resume).

### Cancel

1. The card sends `CANCEL_TRANSFER` with its current `transferId`.
2. The sender marks that transfer context as cancelled.
3. The chunk streaming loop checks the flag at a chunk boundary. The transfer can remain blocked while waiting for a socket read/acknowledgement until the configured socket timeout is reached.
4. Explicit cancellation emits `TRANSFER_INTERRUPTED` and retains the transfer context so the sender can look it up for a resume request.

### Resume

1. The card sends `RESUME_TRANSFER` with the same `transferId`.
2. The sender reads the retained context and its current byte count, clears the cancellation flag, and starts a virtual thread.
3. The resumed transfer calls the sender implementation with the same `transferId` and byte offset, so progress events can update the existing card.

**Current limitation:** the retained-context path is specifically preserved for explicit cancellation. The sender's generic exception path removes the context after a socket or other transfer failure, so an unexpected network interruption does not currently leave a context that this WebSocket resume command can use. Resume is in-memory and is not a durable recovery mechanism across backend restarts.

## 6. Frontend singleton and reconnection

`frontend/lib/websocket.ts` exports one `transferWs` instance. Components share its connection by subscribing with `onEvent()` and receive an unsubscribe function. `connect()` is idempotent while a connection is open or connecting.

When a connection closes unexpectedly, the wrapper reconnects with exponential delays of 1, 2, 4, 8 seconds and so on, capped at 30 seconds. Calling `close()` manually disables automatic reconnect. A command attempted while disconnected fails immediately; callers should not treat it as delivered.

Typical React subscription:

```tsx
const { isConnected, lastMessage } = useWebSocket();

useEffect(() => {
  if (!lastMessage) return;

  if (lastMessage.type === "TRANSFER_PROGRESS") {
    // Update the component's transfer state from lastMessage.payload.
  }
}, [lastMessage]);
```

The hook owns the subscription and cleans it up on unmount. Multiple hook consumers share the singleton socket, but each consumer maintains its own React state.

## 7. Local verification

Start the backend:

```powershell
cd backend
mvn spring-boot:run
```

Start the frontend in another terminal:

```powershell
cd frontend
npm run dev
```

Open `http://localhost:3000`, then inspect browser DevTools → Network → WS and select `/ws/transfers`. The frontend logs connection and reconnect messages with the `[transferWs]` prefix.

To send a discovery command from the browser console:

```js
const ws = new WebSocket("ws://localhost:8080/ws/transfers");
ws.onopen = () => {
  ws.send(JSON.stringify({ command: "TRIGGER_DISCOVERY_PING" }));
};
```

For a LAN client, replace `localhost` with the backend machine's reachable IP address and ensure the backend port is reachable from that device.

## 8. Automated tests

| Layer | Test file | Scope |
|---|---|---|
| Backend command dispatch and broadcast | `backend/src/test/java/com/omnidrop/websocket/TransferWebSocketHandlerTest.java` | Commands, missing fields, malformed JSON, unknown commands, session broadcast and cleanup |
| Frontend singleton | `frontend/__tests__/websocket.test.ts` | URL, idempotent connect, event subscription, command serialization, malformed messages, reconnect |
| React hook | `frontend/__tests__/useWebSocket.test.tsx` | Connect, subscription, event state, open state, unsubscribe |
| Progress card | `frontend/__tests__/TransferProgressCard.test.tsx` | Progress rendering and cancel/resume visibility and commands |

Run the suites with:

```powershell
cd backend
mvn test

cd ..\frontend
npm test
```

The tests above do not establish an end-to-end browser-to-backend transfer. Playwright E2E coverage must be run using a Playwright test runner and configured services; it is separate from the Vitest unit/component command.

## 9. Related documentation

- [`WebSocket protocol`](WEBSOCKET_PROTOCOL.md)
- [`Testing strategy`](TESTING.md)
- [`TCP and UDP network protocol`](NETWORK_PROTOCOL.md)
- [`System architecture`](ARCHITECTURE.md)
