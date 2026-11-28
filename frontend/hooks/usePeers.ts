"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { LivePeer, PeerDevice, WebSocketEvent } from "@/types";
import { api } from "@/services/api";
import { transferWs } from "@/lib/websocket";

/**
 * MEMBER 1: Live list of discovered peers.
 *
 * - Loads GET /api/v1/devices on mount.
 * - Applies EVERY DEVICE_DISCOVERED / DEVICE_LOST WebSocket event with a functional
 *   state update, so no event is lost when several arrive before React re-renders
 *   (the old `lastMessage` pattern only ever saw the last one).
 * - Optionally re-fetches every `pollMs` so lastSeenTimestamp stays fresh and the list
 *   heals itself after a WebSocket reconnect.
 * - Stamps each peer with `seenAt` from THIS browser's clock whenever a new heartbeat shows up.
 *   On an offline LAN the phone/laptop clocks are often not in sync, so the UI never compares
 *   the backend's lastSeenTimestamp with Date.now() directly.
 */
export function usePeers(options: { pollMs?: number } = {}) {
  const { pollMs = 3000 } = options;
  const [peers, setPeers] = useState<LivePeer[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const mounted = useRef(true);

  const refresh = useCallback(async () => {
    try {
      const data = await api.getDevices();
      if (mounted.current) setPeers((prev) => mergeFetched(prev, data, Date.now()));
    } catch (e) {
      console.warn("[usePeers] failed to fetch peers:", e);
    } finally {
      if (mounted.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    transferWs.connect();

    const unsubscribe = transferWs.onEvent((ev: WebSocketEvent) => {
      if (ev.type === "DEVICE_DISCOVERED") {
        const peer = ev.payload as PeerDevice;
        if (!peer?.deviceId) return;
        setPeers((prev) => upsertPeer(prev, peer, Date.now()));
      } else if (ev.type === "DEVICE_LOST") {
        const lostId = (ev.payload as { deviceId?: string })?.deviceId;
        if (!lostId) return;
        setPeers((prev) => prev.filter((p) => p.deviceId !== lostId));
      }
    });

    refresh();
    const timer = pollMs > 0 ? setInterval(refresh, pollMs) : null;

    return () => {
      mounted.current = false;
      unsubscribe();
      if (timer) clearInterval(timer);
    };
  }, [pollMs, refresh]);

  return { peers, isLoading, refresh };
}

/** Insert or replace a peer by deviceId (keeping the original order) and mark it seen now. */
export function upsertPeer(list: LivePeer[], peer: PeerDevice, now: number): LivePeer[] {
  const index = list.findIndex((p) => p.deviceId === peer.deviceId);
  if (index === -1) return [...list, { ...peer, seenAt: now }];
  const next = list.slice();
  next[index] = { ...list[index], ...peer, seenAt: now };
  return next;
}

/**
 * Replace the list with a fresh GET /api/v1/devices result. A peer keeps its old `seenAt`
 * unless the backend reports a newer heartbeat (lastSeenTimestamp changed).
 */
export function mergeFetched(prev: LivePeer[], fetched: PeerDevice[], now: number): LivePeer[] {
  return fetched.map((peer) => {
    const old = prev.find((p) => p.deviceId === peer.deviceId);
    const seenAt = old && old.lastSeenTimestamp === peer.lastSeenTimestamp ? old.seenAt : now;
    return { ...peer, seenAt };
  });
}
