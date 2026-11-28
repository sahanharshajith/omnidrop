# Member 1 – UDP Device Discovery: Completion Plan

> Owner: Member 1 · Branch: `feature/udp-discovery` · Written 19 Nov 2026
> Companion to `docs/PROJECT_CONTEXT.md`.
>
> **Status 28 Nov 2026:** Steps 1–8 and 11 are implemented (see `docs/MEMBER1_TASK_BRANCHES.md` for the branch of each). Steps 9 (multi‑device + Wireshark), 10 (PRs) and 12 (demo prep) remain. The gap table below describes the code as it was on 19 Nov.

---

## 1. What Member 1 is responsible for

The split is stated clearly in the repo (README §4, `docs/TEAM_CONTRIBUTIONS.md`, `docs/PROJECT_PLAN.md`, and `MEMBER 1` comments in the code). Member 1 owns **UDP Device Discovery**:

**Network programming core**
* Bind a `DatagramSocket` to port **8888** with `setBroadcast(true)`.
* Walk all active network interfaces and broadcast beacons to every valid IPv4 subnet broadcast address.
* Run a background daemon thread that receives datagrams **with timeout handling** (`setSoTimeout` / `SocketTimeoutException`).
* Keep an in‑memory **`PeerRegistry`** and expire inactive peers automatically.
* Ignore your own beacons (loopback / self filter).

**Owned classes:** `UdpDiscoveryService`, `UdpBeaconBroadcaster`, `UdpPacketListener`, `PeerRegistry` (plus the closely related `DeviceDiscoveryController`, `PeerDevice`, `NetworkInterfaceUtils`).

**Frontend & integration**
* Device Discovery radar and peer table with status indicators. Named components: **`DeviceDiscoveryCard`**, **`RadarView`**, **`PeerStatusBadge`**.
* Push "peer joined" / "peer left" to the browser over the WebSocket.

**Testing:** unit tests for packet encoding, decoding, loopback filter and heartbeat expiry (+ the `UdpDiscoveryTest` loopback test described in `docs/TESTING.md` §2.3).

**Demo / viva:** walk through the `DatagramSocket` code, show tests passing, show Wireshark/log traces of beacons, explain how discovery feeds the WebSocket and UI.

---

## 2. Current state: what already exists vs. what is missing

### Already working
* `UdpDiscoveryService` binds listener on 8888, starts listener thread, schedules beacons every 3 s and eviction every 5 s, filters own `deviceId`, emits `DEVICE_DISCOVERED` / `DEVICE_LOST`.
* `UdpBeaconBroadcaster` builds the JSON beacon and sends it to every interface broadcast address (fallback `255.255.255.255`).
* `UdpPacketListener` receives, checks `magic`, builds `PeerDevice` from `packet.getAddress()`.
* REST `GET /api/v1/devices`, `POST /api/v1/devices/broadcast`; WS command `TRIGGER_DISCOVERY_PING`.
* `/discovery` page with `PeerCard` grid and "Scan Subnet Now".

### Gaps and issues found in the code

| # | Gap / issue | Where | Why it matters |
| :-- | :-- | :-- | :-- |
| G1 | **No tests at all** for discovery | `backend/src/test` | Required by DoD (>80% coverage) and the viva |
| G2 | **No `PeerRegistry` class**: the map lives inside the service | `UdpDiscoveryService` | Named deliverable; also hard to unit‑test eviction without a clock |
| G3 | **No receive timeout**: `socket.receive()` blocks forever | `UdpPacketListener` | Spec explicitly asks for `SocketTimeoutException` handling; also makes clean shutdown harder |
| G4 | **`stop()` is never called**: no `@PreDestroy`; listener thread is not a daemon | `UdpDiscoveryService` | Port 8888 can stay busy on restart in dev; no clean exit |
| G5 | **No "leave" beacon**: peers only disappear after the timeout | broadcaster / listener | Architecture doc lists "node leave"; nicer demo (peer disappears instantly) |
| G6 | **Beacon validation is weak**: missing `deviceId` → `NullPointerException` caught as a warning; `version` not checked | `UdpPacketListener` | Robustness against junk packets on port 8888 |
| G7 | **Peer changes are silent**: if a known peer changes IP or name, no event is sent | `registerPeer` | UI shows stale IP; transfers go to the wrong address |
| G8 | **`httpPort` hardcoded to 8080** in the beacon | `UdpBeaconBroadcaster` | Wrong if someone runs with another `server.port` |
| G9 | **Silent failure if port 8888 is busy**: logged only, UI does not know | `start()` | Confusing during demo; health endpoint could report it |
| G10 | **Timeout inconsistency**: 10 s / 12 s / 15 s across docs, config 12 s | docs + config | Examiners read the docs; pick one value |
| G11 | **Docs payload mismatch**: `WEBSOCKET_PROTOCOL.md` shows `status`/`lastSeen`; code sends `lastSeenTimestamp`/`active` | docs | Keep docs truthful |
| G12 | **Frontend can drop discovery events**: pages react to `lastMessage`; if two WS events arrive before React re‑renders, only the last one is seen | `app/discovery/page.tsx`, `app/page.tsx` | With several devices joining together, some peers may not appear until refresh |
| G13 | **Named frontend components missing**: `DeviceDiscoveryCard`, `RadarView`, `PeerStatusBadge` | `frontend/components` | Named deliverables |
| G14 | **Docker breaks LAN discovery**: compose uses a bridge network, broadcasts never reach the real LAN | `docker-compose.yml` | Avoid surprises if the team demos from Docker |

---

## 3. Timeline (fits the team plan)

Team plan: multi‑device testing **22–25 Nov**, demo prep **26–28 Nov**. So the backend + frontend work should be done by **21 Nov**.

| Day | Date | Work |
| :-- | :-- | :-- |
| 1 | Thu 19 Nov | Step 0, Step 1 (`PeerRegistry`), Step 2 (listener hardening) |
| 2 | Fri 20 Nov | Steps 3–5 (lifecycle, leave beacon, beacon codec), Step 6 (backend tests) |
| 3 | Sat 21 Nov | Steps 7–8 (frontend components + tests), open PR (Step 10) |
| 4–7 | 22–25 Nov | Step 9 (multi‑device + Wireshark), fix findings, Step 11 (docs) |
| 8–10 | 26–28 Nov | Step 12 (demo script + viva prep) |

---

## 4. Step‑by‑step plan

### Step 0 – Set up (30 min)
1. `git checkout develop && git pull origin develop`
2. `git checkout -b feature/udp-discovery` (or `git checkout feature/udp-discovery && git rebase develop` if it exists).
3. Run `cd backend && mvn clean test` to see the baseline is green.
4. Run the backend once and confirm the log line `UDP Discovery Service started on port 8888`.
5. Agree with the team **now** on the peer timeout value (G10). Recommendation: keep **12 s** (= 4 missed beacons at 3 s), because code, config and UI already say 12 s; only docs change.

### Step 1 – Extract `PeerRegistry` (G2, G7)
Create `backend/src/main/java/com/omnidrop/discovery/PeerRegistry.java`:

```java
public class PeerRegistry {
    public enum Result { NEW, UPDATED, REFRESHED }

    private final Map<String, PeerDevice> peers = new ConcurrentHashMap<>();
    private final LongSupplier clock;            // injectable for tests

    public PeerRegistry() { this(System::currentTimeMillis); }
    public PeerRegistry(LongSupplier clock) { this.clock = clock; }

    /** Insert or refresh a peer. UPDATED = IP, name or port changed. */
    public Result upsert(PeerDevice incoming) {
        incoming.setLastSeenTimestamp(clock.getAsLong());
        PeerDevice old = peers.put(incoming.getDeviceId(), incoming);
        if (old == null) return Result.NEW;
        boolean changed = !Objects.equals(old.getIpAddress(), incoming.getIpAddress())
                || !Objects.equals(old.getDeviceName(), incoming.getDeviceName())
                || old.getTcpPort() != incoming.getTcpPort();
        return changed ? Result.UPDATED : Result.REFRESHED;
    }

    public Optional<PeerDevice> remove(String deviceId) { return Optional.ofNullable(peers.remove(deviceId)); }

    /** Removes and returns peers silent for longer than timeoutMs. */
    public List<PeerDevice> evictStale(long timeoutMs) {
        long now = clock.getAsLong();
        List<PeerDevice> evicted = new ArrayList<>();
        peers.values().removeIf(p -> {
            boolean stale = now - p.getLastSeenTimestamp() > timeoutMs;
            if (stale) evicted.add(p);
            return stale;
        });
        return evicted;
    }

    public List<PeerDevice> snapshot() { return new ArrayList<>(peers.values()); }
    public int size() { return peers.size(); }
}
```

Then in `UdpDiscoveryService`:
* Replace `activePeers` with a `PeerRegistry` (constructor‑injected or a field; for testability add a package‑private constructor that takes one).
* `registerPeer`: after the self‑filter, `switch (registry.upsert(peer))` → `NEW` or `UPDATED`: `notifyPeerDiscovered(peer)` (the frontend already treats `DEVICE_DISCOVERED` as an upsert, so no new event type is needed); `REFRESHED`: nothing.
* `evictStalePeers`: loop over `registry.evictStale(timeout)` and call `notifyPeerLost` for each.
* `getActivePeers` → `registry.snapshot()`.

### Step 2 – Harden `UdpPacketListener` (G3, G6)
1. In `start()`, set a timeout on the listener socket: `listenerSocket.setSoTimeout(1000);`
2. In `run()`:
   ```java
   try {
       socket.receive(packet);
   } catch (SocketTimeoutException e) {
       continue;                     // normal: lets the loop re-check running flag
   }
   ```
3. Validate before building the peer; drop silently (`log.debug`) if:
   * `magic` missing or not `OMNI_DISCOVER`,
   * `version` != 1,
   * `deviceId` missing/blank, `deviceName` missing,
   * `tcpPort` outside 1–65535.
4. Catch `JsonProcessingException` separately and log at `debug` (junk on port 8888 should not spam warnings).
5. Use a fresh `DatagramPacket` per receive (already done) and keep the 4096‑byte buffer; reject packets with `getLength() == buffer.length` (possibly truncated).

### Step 3 – Lifecycle and startup status (G4, G8, G9)
1. Annotate `stop()` with `@PreDestroy` (import `jakarta.annotation.PreDestroy`).
2. Make the listener thread a daemon: `Thread t = new Thread(listener, "OmniDrop-UDP-Listener"); t.setDaemon(true); t.start();`
3. Create the listener socket so restarts don't fail with "Address already in use":
   ```java
   listenerSocket = new DatagramSocket(null);
   listenerSocket.setReuseAddress(true);
   listenerSocket.setBroadcast(true);
   listenerSocket.bind(new InetSocketAddress(udpPort));
   ```
4. Inject the real HTTP port (`@Value("${server.port:8080}") int httpPort`) and pass it to the broadcaster instead of the hardcoded `8080`.
5. Keep the last start error in a field (`String lastError`) and expose `discoveryRunning` + `discoveryError` in `HealthController` output (coordinate with whoever owns the health endpoint; it's a 2‑line change). The discovery page can then show "Discovery offline: UDP port 8888 in use".

### Step 4 – Graceful "leave" beacon (G5)
1. Add an `event` field to the beacon: `"ALIVE"` (default) or `"LEAVE"`. Old nodes that ignore the field still work.
2. In `stop()`, before closing sockets, send one `LEAVE` beacon (reuse the broadcaster with a flag).
3. In the listener: if `event == "LEAVE"` → `discoveryService.unregisterPeer(deviceId)` → `registry.remove(...)` → `notifyPeerLost(deviceId)`.
4. Demo payoff: stop one laptop's backend and the peer disappears from the other screen at once, instead of after 12 s.

### Step 5 – Make the beacon testable: `DiscoveryBeacon` + codec
Move JSON building/parsing out of the two runnables so they can be unit‑tested without sockets:

```java
public record DiscoveryBeacon(String deviceId, String deviceName, int tcpPort, int httpPort,
                              long timestamp, String event) {
    public static final String MAGIC = "OMNI_DISCOVER";
    public static final int VERSION = 1;
}

public final class BeaconCodec {
    public static byte[] encode(DiscoveryBeacon b) { ... }              // JSON, UTF-8
    public static Optional<DiscoveryBeacon> decode(byte[] data, int off, int len) { ... } // all Step 2 validation lives here
}
```

`UdpBeaconBroadcaster` calls `encode`; `UdpPacketListener` calls `decode` and only handles the socket. This keeps the classes small and easy to explain in the viva.

### Step 6 – Backend tests (G1) — target >80% coverage of `com.omnidrop.discovery`
Create under `backend/src/test/java/com/omnidrop/discovery/`:

**`BeaconCodecTest`** (pure unit)
* encode → decode round trip keeps all fields.
* rejects wrong magic, wrong version, missing `deviceId`, blank name, port 0 / 70000, non‑JSON bytes, empty array.
* `event` defaults to `ALIVE` when absent (backward compatible).

**`PeerRegistryTest`** (pure unit, fake clock with `AtomicLong`)
* first upsert → `NEW`; same data again → `REFRESHED`; changed IP → `UPDATED`.
* `evictStale`: peer at t=0, clock → 12 001, timeout 12 000 → evicted; peer refreshed at t=10 000 is kept.
* `remove` returns the peer once, then empty.
* concurrency smoke test: 8 threads × 1000 upserts → no exception, correct size.

**`UdpDiscoveryServiceTest`** (Mockito mock of `WebSocketNotificationService`)
* own `deviceId` is ignored (no registry entry, no notification) — the loopback filter.
* new peer → `notifyPeerDiscovered` exactly once; refresh → no extra call; IP change → called again.
* eviction → `notifyPeerLost(deviceId)`.
* LEAVE → `notifyPeerLost` immediately.

**`UdpLoopbackIntegrationTest`** (real sockets, matches `TESTING.md` §2.3)
* Bind `new DatagramSocket(0, InetAddress.getLoopbackAddress())`, `setSoTimeout(500)`.
* Start a `UdpPacketListener` on it with a mocked `UdpDiscoveryService` (`isRunning()` → true).
* From a second `DatagramSocket`, send an encoded beacon to `127.0.0.1:<boundPort>`.
* `verify(service, timeout(2000)).registerPeer(argThat(p -> p.getIpAddress().equals("127.0.0.1") && ...))`.
* Send garbage → `verify(service, after(500).never()).registerPeer(...)` for that packet.
* Close the socket, confirm the thread exits (join with timeout).

**`DeviceDiscoveryControllerTest`** (`@WebMvcTest` + `@MockBean UdpDiscoveryService`)
* `GET /api/v1/devices` returns the mocked list as JSON.
* `POST /api/v1/devices/broadcast` calls `triggerManualBroadcast()`.

Run: `mvn test -Dtest='*Discovery*,*Beacon*,*PeerRegistry*,*Udp*'`. To check coverage, add the JaCoCo plugin to `pom.xml` (agree with the team first, since it is a shared file) and run `mvn test jacoco:report` → `target/site/jacoco/index.html`.

### Step 7 – Frontend components (G12, G13)

**`frontend/hooks/usePeers.ts`** (fixes dropped events)
* Load `api.getDevices()` on mount.
* Subscribe directly with `transferWs.onEvent(...)` and update state with functional `setPeers(prev => ...)` for **every** event (no reliance on `lastMessage`), handling `DEVICE_DISCOVERED` (upsert) and `DEVICE_LOST` (remove).
* On WebSocket reconnect, re‑fetch the list (peers may have changed while disconnected).
* Return `{ peers, refresh, isLoading }`. Use it in `/discovery`, and offer it to the owners of `/`, `/transfer`, `/distribution` (they currently fetch once).

**`frontend/components/PeerStatusBadge.tsx`**
* Props: `lastSeenTimestamp`, `intervalMs = 3000`, `timeoutMs = 12000`.
* Ticks every second. `< 2 × interval` → green "Online"; up to `timeout` → amber "Weak signal"; beyond → grey "Offline".
* Replaces the current `active ? Online : Idle` badge in `PeerCard` (that "Idle" state never shows, because evicted peers are removed rather than marked inactive).
* Backend note: the `lastSeenTimestamp` in the WS payload is only sent on NEW/UPDATED. For a live "last seen", either (a) let the UI poll `GET /api/v1/devices` every 3 s on the discovery page, or (b) also emit a light `DEVICE_HEARTBEAT` event. Recommend (a): simpler, no protocol change.

**`frontend/components/DeviceDiscoveryCard.tsx`**
* The detailed card for the discovery page: device icon, name, `ip:tcpPort`, `PeerStatusBadge`, "last seen Xs ago", selected state, "Send file" link. Keep `PeerCard` for the compact dashboard list (or make `PeerCard` reuse `PeerStatusBadge`).

**`frontend/components/RadarView.tsx`**
* SVG radar: concentric rings, a rotating sweep (CSS `@keyframes rotate`), this node in the centre, each peer as a dot placed at a stable angle (hash of `deviceId`) and radius by freshness. Click a dot → select the peer. Respect `prefers-reduced-motion`. Must work at 320 px width (NFR4).

**`/discovery` page**
* Header with connection status (WebSocket connected / reconnecting) and discovery status from the health endpoint (Step 3.5).
* `RadarView` on top, grid of `DeviceDiscoveryCard` below.
* Fix the help text to match the agreed timeout.

### Step 8 – Frontend tests
Add to `frontend/__tests__/` (Vitest + Testing Library, same style as existing suites):
* `PeerStatusBadge.test.tsx`: Online / Weak / Offline thresholds using `vi.useFakeTimers()`.
* `usePeers.test.tsx`: mock `transferWs` and `api`; two `DEVICE_DISCOVERED` events fired back‑to‑back → **both** peers present (regression test for G12); `DEVICE_LOST` removes; duplicate discovered → no duplicate.
* `RadarView.test.tsx`: renders one dot per peer; clicking a dot calls `onSelect`.

Run `cd frontend && npm test`.

### Step 9 – Real multi‑device verification + Wireshark (22–25 Nov)
1. Two (ideally three) laptops on the same Wi‑Fi or phone hotspot, internet off.
2. Windows firewall (run as admin on each laptop):
   ```
   netsh advfirewall firewall add rule name="OmniDrop UDP 8888" dir=in action=allow protocol=UDP localport=8888
   netsh advfirewall firewall add rule name="OmniDrop TCP" dir=in action=allow protocol=TCP localport=8080,9090,3000
   ```
   Make sure the Wi‑Fi profile is "Private", not "Public".
3. Give each node a clear name: `mvn spring-boot:run -Dspring-boot.run.arguments="--omnidrop.node.name=Thiloni-Laptop"`.
4. Check and record:
   * peers appear within ~3 s on every screen;
   * stop one backend → peer disappears immediately (LEAVE) on the others;
   * pull the Wi‑Fi on one laptop → peer disappears after ~12 s (timeout path);
   * reconnect → reappears; change of IP is reflected (UPDATED path);
   * "Scan Subnet Now" triggers an immediate beacon (visible in Wireshark).
5. Wireshark capture for the viva: filter `udp.port == 8888`; show the source IP, destination `x.x.x.255`, and the JSON payload in the bytes pane. Save the `.pcapng` and a screenshot into `docs/evidence/` (create it).
6. Known pitfalls to note in the report:
   * Some campus Wi‑Fi and some phone hotspots use **client isolation** and block broadcast. Use a personal hotspot or a home router for the demo.
   * Machines with VirtualBox/VPN adapters broadcast on those too; harmless, but explain it if asked.
   * Docker (G14): the bridge network cannot see LAN broadcasts. For the demo run the backend natively. If Docker is needed on Linux, add `network_mode: host` to the backend service (not supported the same way on Docker Desktop for Windows/macOS). Add this note to the README.

**Optional stretch (only if time):** "Add device by IP" — send a unicast beacon to a typed IP:8888 for networks that block broadcast. Good fallback for the demo.

### Step 10 – Pull request
* Conventional commits, e.g.
  * `refactor(discovery): extract PeerRegistry with injectable clock`
  * `feat(discovery): receive timeout, beacon validation and LEAVE beacon`
  * `test(discovery): codec, registry, service and loopback socket tests`
  * `feat(frontend): RadarView, DeviceDiscoveryCard, PeerStatusBadge and usePeers`
* `mvn clean test` and `npm test` both green, zero compiler warnings.
* Push `feature/udp-discovery`, open a PR to `develop`, ask one teammate to review (DoD needs one approval).
* Shared files touched (`HealthController`, `pom.xml` for JaCoCo, `PeerCard`, `app/page.tsx`): mention them in the PR description and ping their owners.

### Step 11 – Documentation updates (G10, G11)
* `docs/NETWORK_PROTOCOL.md` §1: agreed timeout, `event: ALIVE|LEAVE` field, validation rules (version, required fields, port range), receive timeout.
* `docs/WEBSOCKET_PROTOCOL.md` §3.1–3.2: real `DEVICE_DISCOVERED` payload (`lastSeenTimestamp`, `active`), agreed timeout, note that `DEVICE_DISCOVERED` is also sent on IP/name change.
* `docs/PROJECT_REQUIREMENTS.md` FR2 and `docs/TEAM_CONTRIBUTIONS.md`: same timeout value.
* `docs/TESTING.md` §2.3: list the real test classes.
* `docs/ARCHITECTURE.md` class diagram: real method names, add `PeerRegistry`.

### Step 12 – Demo and viva preparation (26–28 Nov)
**Demo script (3 minutes)**
1. Show `UdpDiscoveryService.start()`: `DatagramSocket`, `setBroadcast(true)`, bind 8888, scheduler.
2. Show `NetworkInterfaceUtils.getBroadcastAddresses()`: why per‑interface broadcast addresses instead of only `255.255.255.255`.
3. Show `UdpPacketListener`: `receive`, `setSoTimeout`, `packet.getAddress()`, validation, self filter.
4. Run `mvn test -Dtest='*Discovery*,*Beacon*,*PeerRegistry*,*Udp*'` live.
5. Two laptops: peers pop up on the radar; stop one → disappears; Wireshark shows the beacons.

**Questions to be ready for**
* Why UDP and not TCP for discovery? (Connectionless, one‑to‑all broadcast, no need to know IPs in advance, losing one beacon is fine.)
* Broadcast vs multicast? (Broadcast reaches the whole subnet, simple, no group join; multicast `224.x` needs `MulticastSocket.joinGroup` and IGMP, more scalable. We chose broadcast for simplicity on small LANs.)
* What if a beacon is lost? (Heartbeat every 3 s, timeout 12 s = 4 lost beacons tolerated.)
* Why take the IP from `getAddress()` instead of putting it in the JSON? (The real source address is reliable; a device with many interfaces would not know which IP the peer can reach.)
* How is `255.255.255.255` different from `192.168.1.255`? (Limited broadcast vs directed subnet broadcast; some OSes send the limited one only out of the default interface.)
* Why `ConcurrentHashMap`? (Listener thread writes, scheduler thread evicts, HTTP threads read.)
* Why `setSoTimeout`? (So `receive()` wakes up regularly and the thread can stop cleanly.)
* How is your own beacon filtered? (Compare `deviceId` with the local node ID.)
* How does a discovered peer reach the browser? (`UdpDiscoveryService` → `WebSocketNotificationService` → `TransferWebSocketHandler.broadcast` → `transferWs` singleton → `usePeers` → `RadarView`.)
* Max UDP payload? (Theoretical 65,507 bytes; our beacon is ~200 bytes, well under one MTU, so no fragmentation.)

---

## 5. Definition of done checklist

- [ ] `PeerRegistry` extracted; service uses it
- [ ] Listener uses `setSoTimeout` and handles `SocketTimeoutException`
- [ ] Beacon validation (magic, version, required fields, port range)
- [ ] `@PreDestroy` stop, daemon listener thread, reuse‑address bind
- [ ] LEAVE beacon sent on shutdown and handled on receive
- [ ] Peer IP/name change re‑emits `DEVICE_DISCOVERED`
- [ ] Real `server.port` in the beacon
- [ ] Discovery status visible in health endpoint/UI
- [ ] Backend tests: codec, registry, service, loopback, controller; >80% coverage of `discovery`
- [ ] `usePeers`, `PeerStatusBadge`, `DeviceDiscoveryCard`, `RadarView` + Vitest tests
- [ ] Multi‑device test passed; Wireshark capture saved in `docs/evidence/`
- [ ] Docs aligned (timeout, payload, protocol, testing, architecture)
- [ ] PR to `develop` reviewed and merged; CI green
- [ ] Demo script rehearsed
