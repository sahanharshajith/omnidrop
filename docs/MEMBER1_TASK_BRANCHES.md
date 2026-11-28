# Member 1 – Remaining Tasks and Branch Plan

> UDP Device Discovery · Updated 28 Nov 2026 (all code tasks done) · Builds on `docs/MEMBER1_PLAN.md`

---

## 1. Where things stand

| Task | Branch | State |
| :-- | :-- | :-- |
| Step 1 `PeerRegistry` | `feature/udp-discovery` (`9ab9009`) | ✅ Pushed |
| T1 Listener timeout + validation | `feature/udp-listener-validation` | ✅ Committed locally |
| T2 Lifecycle + health status | `feature/udp-lifecycle` | ✅ Committed locally |
| T3 LEAVE beacon | `feature/udp-leave-beacon` | ✅ Committed locally |
| T4 `BeaconCodec` | `feature/udp-beacon-codec` | ✅ Committed locally |
| T5 Backend tests (48 new, 96 total, ~94% discovery coverage) | `feature/udp-discovery-tests` | ✅ Committed locally |
| T6 `usePeers` hook | `feature/discovery-peers-hook` | ✅ Committed locally |
| T7 Radar + cards + badge | `feature/discovery-ui` | ✅ Committed locally |
| T8 Frontend tests (26 new, 52 total) | `feature/discovery-ui-tests` | ✅ Committed locally |
| T9 Docs | `docs/udp-discovery` | ✅ Committed locally (add Wireshark evidence after T10) |
| T10 Multi‑device + Wireshark | – | ⏳ Needs real laptops |
| T11 PRs | – | ⏳ To do together |
| T12 Demo rehearsal | – | ⏳ |

The branches are **stacked**: each one was cut from the one before it, in the order above. Merge the PRs in that order (each into `feature/udp-discovery`), or, if time is short, open a single PR from `docs/udp-discovery` (the last branch, which contains everything) into `feature/udp-discovery` and then `feature/udp-discovery` → `develop`.

---

## 2. Branch strategy

`feature/udp-discovery` stays as **Member 1's integration branch** (the one the team docs name). Every important task gets its **own short‑lived branch cut from `feature/udp-discovery`**, and is merged back into it with a PR. At the end, one PR takes `feature/udp-discovery` into `develop`.

```
develop
  └── feature/udp-discovery            (Member 1 integration branch)
        ├── feature/udp-listener-validation   T1
        ├── feature/udp-lifecycle             T2
        ├── feature/udp-leave-beacon          T3
        ├── feature/udp-beacon-codec          T4
        ├── feature/udp-discovery-tests       T5
        ├── feature/discovery-peers-hook      T6
        ├── feature/discovery-ui              T7
        ├── feature/discovery-ui-tests        T8
        └── docs/udp-discovery                T9
```

> Branch names use `feature/udp-...` (with a hyphen), not `feature/udp-discovery/...`. Git cannot have a branch called `feature/udp-discovery` and also branches "inside" it like `feature/udp-discovery/x`.

### Commands for every task (same each time)

```bash
# 1. start from the latest integration branch
git checkout feature/udp-discovery
git pull

# 2. create the task branch
git checkout -b <branch-name>

# 3. code, then check
cd backend && mvn clean test        # backend tasks
cd frontend && npm test             # frontend tasks

# 4. commit and push
git add .
git commit -m "<message from the table>"
git push -u origin <branch-name>
```

5. On GitHub open a PR **base: `feature/udp-discovery`** ← compare: `<branch-name>`. Fill in `.github/PULL_REQUEST_TEMPLATE.md`. Merge when CI is green.
6. Go back to step 1 for the next task (so each new branch includes the previous ones).

---

## 3. Task list

Priority: **Must** = needed for the marks/demo · **Should** = clearly improves it · **Nice** = only if time.

| # | Task | Branch | Priority | Est. | Commit message |
| :-- | :-- | :-- | :-- | :-- | :-- |
| T1 | Listener receive timeout + beacon validation | `feature/udp-listener-validation` | Must | 30 min | `feat(discovery): receive timeout and beacon validation in UDP listener` |
| T2 | Lifecycle: `@PreDestroy`, daemon thread, reuse‑address bind, real `server.port`, discovery status in health | `feature/udp-lifecycle` | Must | 1 h | `feat(discovery): clean shutdown, reuse-address bind and discovery status` |
| T3 | LEAVE beacon on shutdown | `feature/udp-leave-beacon` | Should | 1 h | `feat(discovery): send and handle LEAVE beacon for instant peer removal` |
| T4 | `DiscoveryBeacon` + `BeaconCodec` refactor | `feature/udp-beacon-codec` | Should | 1 h | `refactor(discovery): move beacon JSON into DiscoveryBeacon and BeaconCodec` |
| T5 | Backend tests (registry, listener/codec, service, loopback, controller) | `feature/udp-discovery-tests` | **Must** | 2–3 h | `test(discovery): registry, codec, service and loopback socket tests` |
| T6 | `usePeers` hook (fixes dropped discovery events) | `feature/discovery-peers-hook` | Should | 45 min | `fix(frontend): usePeers hook so no discovery events are lost` |
| T7 | `PeerStatusBadge`, `DeviceDiscoveryCard`, `RadarView` + discovery page | `feature/discovery-ui` | **Must** | 2–3 h | `feat(frontend): RadarView, DeviceDiscoveryCard and PeerStatusBadge` |
| T8 | Frontend tests for T6–T7 | `feature/discovery-ui-tests` | Should | 1 h | `test(frontend): discovery hook and component tests` |
| T9 | Docs: timeout value, payload, protocol, testing, add context + plan docs, Docker note, Wireshark evidence | `docs/udp-discovery` | Must | 1–1.5 h | `docs(discovery): align protocol docs and add test evidence` |
| T10 | Multi‑device test + Wireshark capture | (no code; evidence goes into T9) | **Must** | 1 h | – |
| T11 | Final PR `feature/udp-discovery` → `develop` | (existing branch) | **Must** | 30 min | PR title: `feat(discovery): complete UDP device discovery module (Member 1)` |
| T12 | Demo + viva rehearsal | (no branch) | **Must** | 1 h | – |

**If time is short**, do this minimum path in order: **T1 → T2 → T5 → T7 → T10 → T9 → T11 → T12**. T3, T4, T6 and T8 make it better but the module works and is demo‑able without them. (If T4 is skipped, T5 tests `UdpPacketListener.parseBeacon` directly instead of `BeaconCodec`.)

---

## 4. Task details

### T1 · `feature/udp-listener-validation` (Plan Step 2)
- `UdpDiscoveryService.start()`: `listenerSocket.setSoTimeout(1000);`
- `UdpPacketListener`: catch `SocketTimeoutException` → `continue`; new `parseBeacon(DatagramPacket)` that drops non‑JSON, wrong `magic`, wrong `version`, missing `deviceId`/`deviceName`, ports outside 1–65535, and oversized packets; use `root.path(...)` not `root.get(...)`.
- Done when: `mvn clean test` green (48 tests), backend starts and logs `UDP Discovery Service started`.

### T2 · `feature/udp-lifecycle` (Plan Step 3)
- `@PreDestroy` on `stop()`.
- Listener thread `setDaemon(true)`.
- Bind with `new DatagramSocket(null)` → `setReuseAddress(true)` → `bind(new InetSocketAddress(udpPort))`.
- Inject `@Value("${server.port:8080}")` and send it as `httpPort` instead of hardcoded `8080` (pass it into `UdpBeaconBroadcaster`).
- Keep `lastError` when start fails; expose `discoveryRunning` and `discoveryError` in `HealthController` (shared file: tell the team in the PR).
- Done when: restart the backend twice quickly with no "Address already in use"; `/api/v1/health` shows `discoveryRunning: true`.

### T3 · `feature/udp-leave-beacon` (Plan Step 4)
- Beacon gets `"event": "ALIVE" | "LEAVE"` (missing = ALIVE, so old nodes still work).
- `stop()` sends one LEAVE beacon before closing sockets.
- Listener: LEAVE → `discoveryService.unregisterPeer(deviceId)` → `registry.remove(...)` → `notifyPeerLost`.
- Done when: stopping laptop A removes it from laptop B's screen at once (not after 12 s).

### T4 · `feature/udp-beacon-codec` (Plan Step 5)
- `record DiscoveryBeacon(deviceId, deviceName, tcpPort, httpPort, timestamp, event)` with `MAGIC` and `VERSION` constants.
- `BeaconCodec.encode(beacon) → byte[]` and `BeaconCodec.decode(bytes, off, len) → Optional<DiscoveryBeacon>` (all T1 validation moves here).
- Broadcaster uses `encode`, listener uses `decode`.

### T5 · `feature/udp-discovery-tests` (Plan Step 6)
Under `backend/src/test/java/com/omnidrop/discovery/`:
- `PeerRegistryTest`: NEW / REFRESHED / UPDATED; `evictStale` with a fake clock (`AtomicLong`); `remove`; concurrency smoke test.
- `BeaconCodecTest` (or `UdpPacketListenerParseTest` if T4 skipped): valid round trip; rejects wrong magic, wrong version, missing id/name, bad ports, non‑JSON.
- `UdpDiscoveryServiceTest` (Mockito mock of `WebSocketNotificationService`): own beacon ignored; NEW → notify once; REFRESHED → no notify; UPDATED → notify; eviction → `notifyPeerLost`; LEAVE → `notifyPeerLost`.
- `UdpLoopbackIntegrationTest`: real `DatagramSocket` on `127.0.0.1:0`, send a beacon, `verify(service, timeout(2000)).registerPeer(...)`; junk packet → never registered.
- `DeviceDiscoveryControllerTest`: `MockMvcBuilders.standaloneSetup(...)`; `GET /api/v1/devices`, `POST /api/v1/devices/broadcast`.
- Done when: all old + new tests green; aim > 80% coverage of `com.omnidrop.discovery`.

### T6 · `feature/discovery-peers-hook` (Plan Step 7, part 1)
- `frontend/hooks/usePeers.ts`: fetch `api.getDevices()` on mount; subscribe with `transferWs.onEvent` and update with `setPeers(prev => ...)` on **every** `DEVICE_DISCOVERED` (upsert) / `DEVICE_LOST` (remove); poll `getDevices()` every 3 s on the discovery page for fresh `lastSeenTimestamp`.
- Use it in `app/discovery/page.tsx` (and offer it to `app/page.tsx` owner).

### T7 · `feature/discovery-ui` (Plan Step 7, part 2)
- `PeerStatusBadge`: Online (< 6 s) / Weak signal (< 12 s) / Offline, ticking every second.
- `DeviceDiscoveryCard`: icon, name, `ip:port`, badge, "last seen Xs ago", select + "Send file".
- `RadarView`: SVG rings, rotating sweep, this node in the centre, peers as dots (angle from `deviceId` hash), click to select; respects `prefers-reduced-motion`; works at 320 px.
- Discovery page: WebSocket status + discovery status (from T2), radar on top, cards below, help text says 12 s.

### T8 · `feature/discovery-ui-tests` (Plan Step 8)
- `PeerStatusBadge.test.tsx` (fake timers), `usePeers.test.tsx` (two events back‑to‑back → both peers present), `RadarView.test.tsx` (one dot per peer, click → `onSelect`).

### T9 · `docs/udp-discovery` (Plan Step 11)
- Agree with the team on **12 s** peer timeout, then update `NETWORK_PROTOCOL.md`, `WEBSOCKET_PROTOCOL.md`, `PROJECT_REQUIREMENTS.md`, `TEAM_CONTRIBUTIONS.md`.
- Real `DEVICE_DISCOVERED` payload (`lastSeenTimestamp`, `active`); `event` field; validation rules; receive timeout.
- `TESTING.md` §2.3: real test class names. `ARCHITECTURE.md`: real method names + `PeerRegistry`.
- Add `docs/PROJECT_CONTEXT.md` and `docs/MEMBER1_PLAN.md`.
- README note: Docker bridge network can't see LAN broadcasts; run the backend natively for multi‑device demos (or `network_mode: host` on Linux).
- `docs/evidence/`: Wireshark `.pcapng` + screenshots from T10.

### T10 · Multi‑device test (Plan Step 9)
- 2–3 laptops, same hotspot, internet off; firewall rules for UDP 8888 and TCP 8080/9090/3000; Wi‑Fi profile = Private.
- Check: peers appear in ~3 s; stop backend → disappears (instantly with T3, else ~12 s); Wi‑Fi off → disappears after ~12 s; reconnect → back; "Scan Subnet Now" sends a beacon.
- Wireshark filter `udp.port == 8888`; screenshot source IP, `x.x.x.255` destination, JSON payload.

### T11 · Final PR
- After T1–T10 are merged into `feature/udp-discovery`: `git checkout feature/udp-discovery && git pull`, then on GitHub open PR **base `develop` ← `feature/udp-discovery`**.
- PR description: what changed, test count, screenshots, shared files touched (`HealthController`, `PeerCard`, `app/page.tsx`), ask one teammate to review.

### T12 · Demo + viva (Plan Step 12)
- 3‑minute script and question list are in `MEMBER1_PLAN.md` §4 Step 12. Rehearse once on the real laptops.
