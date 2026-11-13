import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";

/**
 * Tests for the useWebSocket hook.
 *
 * We mock the shared singleton (`@/lib/websocket`) rather than dealing with
 * real socket simulation — the hook's job is purely to bridge the singleton
 * to React state, and that's what we test here.
 */

// Captured listener so tests can drive events
let capturedListener: ((ev: unknown) => void) | null = null;
let capturedUnsubscribe = vi.fn();

const mockTransferWs = {
  connect: vi.fn(),
  onEvent: vi.fn((cb: (ev: unknown) => void) => {
    capturedListener = cb;
    return capturedUnsubscribe;
  }),
  isOpen: vi.fn(() => true),
  sendCommand: vi.fn(),
  close: vi.fn(),
};

vi.mock("@/lib/websocket", () => ({
  transferWs: mockTransferWs,
}));

describe("useWebSocket", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedListener = null;
    capturedUnsubscribe = vi.fn();
    mockTransferWs.isOpen.mockReturnValue(true);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("calls transferWs.connect() on mount", async () => {
    const { useWebSocket } = await import("@/hooks/useWebSocket");
    renderHook(() => useWebSocket());

    expect(mockTransferWs.connect).toHaveBeenCalledTimes(1);
  });

  it("subscribes via transferWs.onEvent on mount", async () => {
    const { useWebSocket } = await import("@/hooks/useWebSocket");
    renderHook(() => useWebSocket());

    expect(mockTransferWs.onEvent).toHaveBeenCalledTimes(1);
    expect(capturedListener).not.toBeNull();
  });

  it("updates lastMessage when an event arrives", async () => {
    const { useWebSocket } = await import("@/hooks/useWebSocket");
    const { result } = renderHook(() => useWebSocket());

    expect(result.current.lastMessage).toBeNull();

    const event = { type: "TRANSFER_STARTED", timestamp: 123, payload: { transferId: "t1" } };
    act(() => {
      capturedListener?.(event);
    });

    expect(result.current.lastMessage).toEqual(event);
  });

  it("exposes isConnected based on transferWs.isOpen()", async () => {
    const { useWebSocket } = await import("@/hooks/useWebSocket");
    mockTransferWs.isOpen.mockReturnValue(true);
    const { result } = renderHook(() => useWebSocket());

    expect(result.current.isConnected).toBe(true);
  });

  it("unsubscribes on unmount", async () => {
    const { useWebSocket } = await import("@/hooks/useWebSocket");
    const { unmount } = renderHook(() => useWebSocket());

    unmount();

    expect(capturedUnsubscribe).toHaveBeenCalledTimes(1);
  });
});