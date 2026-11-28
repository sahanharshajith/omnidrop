/**
 * MEMBER 1: How fresh a discovered peer is, from the time since its last heartbeat.
 *
 * Beacons arrive every 3 s and the backend drops a peer after 12 s of silence, so:
 *   online  – heard from within 2 beacon intervals (< 6 s)
 *   weak    – missed a few beacons but not timed out yet (< 12 s)
 *   offline – about to be (or already) removed
 */
export type PeerStatus = "online" | "weak" | "offline";

export const BEACON_INTERVAL_MS = 3000;
export const PEER_TIMEOUT_MS = 12000;

export function getPeerStatus(
  ageMs: number,
  intervalMs: number = BEACON_INTERVAL_MS,
  timeoutMs: number = PEER_TIMEOUT_MS
): PeerStatus {
  const age = Math.max(0, ageMs);
  if (age < intervalMs * 2) return "online";
  if (age < timeoutMs) return "weak";
  return "offline";
}

/** "just now", "5s ago", "2m ago" */
export function formatAge(ageMs: number): string {
  const seconds = Math.max(0, Math.floor(ageMs / 1000));
  if (seconds < 2) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  return `${Math.floor(seconds / 60)}m ago`;
}

/**
 * Angle (radians) for each device on the radar. Devices are sorted by deviceId and spread evenly
 * around the circle (first one at the top), so dots never sit on top of each other and a dot
 * only moves when a device joins or leaves.
 */
export function radarAngles(deviceIds: string[]): Map<string, number> {
  const sorted = [...deviceIds].sort();
  const step = sorted.length > 0 ? (2 * Math.PI) / sorted.length : 0;
  const angles = new Map<string, number>();
  sorted.forEach((id, i) => angles.set(id, Math.PI / 2 - i * step));
  return angles;
}
