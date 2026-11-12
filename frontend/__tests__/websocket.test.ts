import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

/**
 * Tests for the TransferWs singleton wrapper.
 *
 * We mock `WebSocket` with a controllable fake so we can drive:
 *  - onopen  -> connect state
 *  - onmessage -> listener dispatch
 *  - onclose -> reconnect scheduling
 *
 * Important: the singleton is created at import time (`export const transferWs`).
 * Vitest isolates each test *file*, so we get a fresh singleton per file, but
 * NOT per test. To keep tests independent we `vi.resetModules()` and re-import
 * before each test.
 */

interface MockSocket {
  readyState: number;
  onopen: (() => void) | null;
  onmessage: ((ev: { data: string }) => void) | null;
  onclose: (() => void) | null;
  onerror: ((err: unknown) => void) | null;
  send: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
  url: string;
}

const OPEN = 1;
const CONNECTING = 0;
const CLOSED = 3;

let lastSocket: MockSocket | null = null;

class FakeWebSocket {
  static CONNECTING = CONNECTING;
  static OPEN = OPEN;
  static CLOSING = 2;
  static CLOSED = CLOSED;

  readyState = CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((ev: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: ((err: unknown) => void) | null = null;
  send = vi.fn();
  close = vi.fn();
  url: string;

  constructor(url: string) {
    this.url = url;
    lastSocket = this as unknown as MockSocket;
  }

  /** Test helper: simulate the server accepting the connection. */
  simulateOpen() {
    this.readyState = OPEN;
    this.onopen?.();
  }

  /** Test helper: simulate an incoming message. */
  simulateMessage(data: string) {
    this.onmessage?.({ data });
  }

  /** Test helper: simulate the socket closing. */
  simulateClose() {
    this.readyState = CLOSED;
    this.onclose?.();
  }
}

describe("transferWs singleton", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    lastSocket = null;
    // Provide a minimal window for the module (it checks `typeof window`)
    (globalThis as unknown as { window: unknown }).window = {
      location: { hostname: "test-host" },
    };
    (globalThis as unknown as { WebSocket: unknown }).WebSocket = FakeWebSocket;
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.resetModules();
    vi.restoreAllMocks();
    delete (globalThis as unknown as { WebSocket?: unknown }).WebSocket;
    delete (globalThis as unknown as { window?: unknown }).window;
  });

  it("connect() creates a WebSocket pointed at ws://<host>:8080/ws/transfers", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();

    expect(lastSocket).not.toBeNull();
    expect(lastSocket!.url).toBe("ws://test-host:8080/ws/transfers");
  });

  it("connect() is idempotent — calling twice doesn't open two sockets", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();
    const first = lastSocket;

    transferWs.connect();
    expect(lastSocket).toBe(first);
  });

  it("onEvent receives parsed events from the server", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();

    const received: unknown[] = [];
    transferWs.onEvent((ev) => received.push(ev));

    lastSocket!.simulateOpen();
    lastSocket!.simulateMessage(
      JSON.stringify({ type: "TRANSFER_PROGRESS", timestamp: 123, payload: { percentage: 42 } })
    );

    expect(received).toHaveLength(1);
    expect((received[0] as { type: string }).type).toBe("TRANSFER_PROGRESS");
  });

  it("unsubscribe returned by onEvent stops delivery", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();

    const received: unknown[] = [];
    const off = transferWs.onEvent((ev) => received.push(ev));

    lastSocket!.simulateOpen();
    lastSocket!.simulateMessage(JSON.stringify({ type: "A", timestamp: 0, payload: {} }));
    expect(received).toHaveLength(1);

    off();
    lastSocket!.simulateMessage(JSON.stringify({ type: "B", timestamp: 0, payload: {} }));
    expect(received).toHaveLength(1); // still 1, second was dropped
  });

  it("malformed JSON messages are swallowed (do not throw, do not dispatch)", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();

    const received: unknown[] = [];
    transferWs.onEvent((ev) => received.push(ev));

    lastSocket!.simulateOpen();
    expect(() => lastSocket!.simulateMessage("not json")).not.toThrow();
    expect(received).toHaveLength(0);
  });

  it("sendCommand returns false when socket isn't open", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();
    // still CONNECTING — never called simulateOpen()

    const result = transferWs.sendCommand("TRIGGER_DISCOVERY_PING");
    expect(result).toBe(false);
    expect(lastSocket!.send).not.toHaveBeenCalled();
  });

  it("sendCommand serializes {command, ...extra} when socket is OPEN", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();
    lastSocket!.simulateOpen();

    const ok = transferWs.sendCommand("CANCEL_TRANSFER", { transferId: "abc-123" });
    expect(ok).toBe(true);
    expect(lastSocket!.send).toHaveBeenCalledTimes(1);

    const sent = JSON.parse(lastSocket!.send.mock.calls[0][0] as string);
    expect(sent).toEqual({ command: "CANCEL_TRANSFER", transferId: "abc-123" });
  });

  it("onclose schedules a reconnect with exponential backoff", async () => {
    const { transferWs } = await import("@/lib/websocket");
    transferWs.connect();
    lastSocket!.simulateOpen();
    const firstSocket = lastSocket;

    lastSocket!.simulateClose();

    // Before the timer fires, no new socket exists
    expect(lastSocket).toBe(firstSocket);

    // First reconnect attempt is ~1000ms
    await vi.advanceTimersByTimeAsync(999);
    expect(lastSocket).toBe(firstSocket);

    await vi.advanceTimersByTimeAsync(2); // total 1001ms
    expect(lastSocket).not.toBe(firstSocket); // a NEW socket was created
  });
});