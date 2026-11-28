import { describe, it, expect, beforeEach, vi } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import type { PeerDevice } from "@/types";

let listener: ((ev: unknown) => void) | null = null;
const unsubscribe = vi.fn();

vi.mock("@/lib/websocket", () => ({
  transferWs: {
    connect: vi.fn(),
    onEvent: vi.fn((cb: (ev: unknown) => void) => {
      listener = cb;
      return unsubscribe;
    }),
    isOpen: vi.fn(() => true),
  },
}));

const getDevices = vi.fn<() => Promise<PeerDevice[]>>();
vi.mock("@/services/api", () => ({
  api: { getDevices: () => getDevices() },
}));

const peer = (id: string, ip = "192.168.1.10", lastSeen = 1): PeerDevice => ({
  deviceId: id,
  deviceName: `Laptop-${id}`,
  ipAddress: ip,
  tcpPort: 9090,
  httpPort: 8080,
  lastSeenTimestamp: lastSeen,
  active: true,
});

describe("usePeers", () => {
  beforeEach(() => {
    listener = null;
    unsubscribe.mockClear();
    getDevices.mockReset();
    getDevices.mockResolvedValue([]);
  });

  it("loads the initial list from the REST API", async () => {
    getDevices.mockResolvedValue([peer("a")]);
    const { usePeers } = await import("@/hooks/usePeers");
    const { result } = renderHook(() => usePeers({ pollMs: 0 }));

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.peers.map((p) => p.deviceId)).toEqual(["a"]);
  });

  it("keeps BOTH peers when two DEVICE_DISCOVERED events arrive back to back", async () => {
    const { usePeers } = await import("@/hooks/usePeers");
    const { result } = renderHook(() => usePeers({ pollMs: 0 }));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      listener?.({ type: "DEVICE_DISCOVERED", timestamp: 1, payload: peer("a") });
      listener?.({ type: "DEVICE_DISCOVERED", timestamp: 2, payload: peer("b") });
    });

    expect(result.current.peers.map((p) => p.deviceId).sort()).toEqual(["a", "b"]);
  });

  it("updates a known peer instead of duplicating it", async () => {
    const { usePeers } = await import("@/hooks/usePeers");
    const { result } = renderHook(() => usePeers({ pollMs: 0 }));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      listener?.({ type: "DEVICE_DISCOVERED", timestamp: 1, payload: peer("a", "192.168.1.10") });
      listener?.({ type: "DEVICE_DISCOVERED", timestamp: 2, payload: peer("a", "192.168.1.77") });
    });

    expect(result.current.peers).toHaveLength(1);
    expect(result.current.peers[0].ipAddress).toBe("192.168.1.77");
  });

  it("removes a peer on DEVICE_LOST", async () => {
    getDevices.mockResolvedValue([peer("a"), peer("b")]);
    const { usePeers } = await import("@/hooks/usePeers");
    const { result } = renderHook(() => usePeers({ pollMs: 0 }));
    await waitFor(() => expect(result.current.peers).toHaveLength(2));

    act(() => {
      listener?.({ type: "DEVICE_LOST", timestamp: 3, payload: { deviceId: "a" } });
    });

    expect(result.current.peers.map((p) => p.deviceId)).toEqual(["b"]);
  });

  it("ignores unrelated and malformed events", async () => {
    const { usePeers } = await import("@/hooks/usePeers");
    const { result } = renderHook(() => usePeers({ pollMs: 0 }));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      listener?.({ type: "TRANSFER_PROGRESS", timestamp: 1, payload: {} });
      listener?.({ type: "DEVICE_DISCOVERED", timestamp: 1, payload: {} });
      listener?.({ type: "DEVICE_LOST", timestamp: 1, payload: null });
    });

    expect(result.current.peers).toEqual([]);
  });

  it("unsubscribes on unmount", async () => {
    const { usePeers } = await import("@/hooks/usePeers");
    const { unmount } = renderHook(() => usePeers({ pollMs: 0 }));
    unmount();
    expect(unsubscribe).toHaveBeenCalledTimes(1);
  });
});

describe("mergeFetched", () => {
  it("keeps seenAt when the heartbeat did not change and refreshes it when it did", async () => {
    const { mergeFetched } = await import("@/hooks/usePeers");
    const prev = [{ ...peer("a", "1.1.1.1", 100), seenAt: 5 }, { ...peer("b", "1.1.1.2", 100), seenAt: 5 }];
    const merged = mergeFetched(prev, [peer("a", "1.1.1.1", 100), peer("b", "1.1.1.2", 200), peer("c")], 99);

    expect(merged.find((p) => p.deviceId === "a")!.seenAt).toBe(5);
    expect(merged.find((p) => p.deviceId === "b")!.seenAt).toBe(99);
    expect(merged.find((p) => p.deviceId === "c")!.seenAt).toBe(99);
  });
});
