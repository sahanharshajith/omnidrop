# Member 1 – Discovery Test Evidence

Put the multi‑device test results here (Task T10 in `docs/MEMBER1_TASK_BRANCHES.md`).

## What to capture

1. **Wireshark capture** – `udp-discovery.pcapng`
   - Capture on the Wi‑Fi adapter, display filter `udp.port == 8888`.
   - Let it run ~20 s with two or three nodes running, then stop one backend (to capture its LEAVE beacon).
2. **Screenshots**
   - `wireshark-beacon.png` – one beacon selected: source IP, destination `x.x.x.255`, JSON payload in the bytes pane.
   - `wireshark-leave.png` – the `"event":"LEAVE"` beacon.
   - `discovery-radar-<laptop>.png` – the `/discovery` page on each laptop showing the others.
3. **Checklist** – fill in below.

## Checklist

| Check | Expected | Result |
| :-- | :-- | :-- |
| Peers appear | within ~3 s on every screen | |
| Stop one backend (Ctrl+C) | disappears from the others at once (LEAVE) | |
| Turn Wi‑Fi off on one laptop | shows "Weak signal", then disappears after ~12 s | |
| Turn Wi‑Fi back on | reappears within ~3 s | |
| "Scan Subnet Now" | extra beacon visible in Wireshark | |
| Junk on port 8888 (optional, e.g. `ncat -u <ip> 8888`) | ignored, nothing crashes | |

Date / network used / laptops: 
