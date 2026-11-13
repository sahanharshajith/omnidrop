"use client";

import { WebSocketEvent } from "@/types";

/**
 * Singleton WebSocket client for the OmniDrop frontend.
 *
 * One socket for the whole app. Many subscribers. One command bus.
 *
 * - subscribe via onEvent(cb) -> returns unsubscribe function
 * - send JSON commands via sendCommand(name, extra?)
 * - auto-reconnects with exponential backoff on close
 * - reconnects immediately if sendCommand is called while disconnected
 *   (defers to the reconnect timer; no throw)
 */
class TransferWs {
  private ws: WebSocket | null = null;
  private listeners = new Set<(ev: WebSocketEvent) => void>();
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private manuallyClosed = false;
  private isConnecting = false;

  /** Open the socket. Idempotent — safe to call multiple times. */
  connect(): void {
    if (typeof window === "undefined") return;
    if (this.isConnecting) return;
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.isConnecting = true;
    this.manuallyClosed = false;

    const host = window.location.hostname || "localhost";
    const url = process.env.NEXT_PUBLIC_WS_URL || `ws://${host}:8080/ws/transfers`;

    try {
      const ws = new WebSocket(url);

      ws.onopen = () => {
        this.isConnecting = false;
        this.reconnectAttempts = 0;
        console.log("[transferWs] connected");
      };

      ws.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data as string) as WebSocketEvent;
          // Snapshot listeners so a listener that unsubscribes mid-broadcast
          // doesn't cause a skipped/doubled callback.
          for (const cb of Array.from(this.listeners)) {
            try {
              cb(parsed);
            } catch (err) {
              console.error("[transferWs] listener threw:", err);
            }
          }
        } catch (e) {
          console.warn("[transferWs] non-JSON message received:", event.data);
        }
      };

      ws.onclose = () => {
        this.isConnecting = false;
        this.ws = null;
        if (this.manuallyClosed) return;

        // Exponential backoff: 1s, 2s, 4s, 8s, capped at 30s
        const delay = Math.min(30000, 1000 * Math.pow(2, this.reconnectAttempts));
        this.reconnectAttempts += 1;
        console.warn(`[transferWs] closed — reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

        this.reconnectTimer = setTimeout(() => this.connect(), delay);
      };

      ws.onerror = (err) => {
        console.warn("[transferWs] error:", err);
      };

      this.ws = ws;
    } catch (e) {
      this.isConnecting = false;
      console.error("[transferWs] failed to open socket:", e);
    }
  }

  /** Subscribe to incoming events. Returns an unsubscribe function. */
  onEvent(cb: (ev: WebSocketEvent) => void): () => void {
    this.listeners.add(cb);
    return () => {
      this.listeners.delete(cb);
    };
  }

  /**
   * Send a JSON command to the backend.
   * No-op if socket isn't currently OPEN (the reconnect logic will restore it).
   */
  sendCommand(command: string, extra?: Record<string, unknown>): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn(`[transferWs] cannot send '${command}' — socket not open`);
      return false;
    }
    try {
      this.ws.send(JSON.stringify({ command, ...extra }));
      return true;
    } catch (e) {
      console.error(`[transferWs] failed to send '${command}':`, e);
      return false;
    }
  }

  /** Returns true if the socket is currently OPEN. */
  isOpen(): boolean {
    return !!this.ws && this.ws.readyState === WebSocket.OPEN;
  }

  /** Close the socket and stop auto-reconnect. Rarely used in practice. */
  close(): void {
    this.manuallyClosed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}

export const transferWs = new TransferWs();