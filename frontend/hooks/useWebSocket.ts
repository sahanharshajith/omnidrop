"use client";

import { useEffect, useState } from "react";
import { WebSocketEvent } from "@/types";
import { transferWs } from "@/lib/websocket";

/**
 * React hook that subscribes to the shared TransferWs singleton.
 *
 * Keeps the original public API ({ isConnected, lastMessage }) so existing
 * components don't need changes. Multiple components can use this hook
 * simultaneously — they all share ONE socket.
 */
export function useWebSocket() {
  const [isConnected, setIsConnected] = useState<boolean>(() => transferWs.isOpen());
  const [lastMessage, setLastMessage] = useState<WebSocketEvent | null>(null);

  useEffect(() => {
    // Ensure the singleton is connected (idempotent)
    transferWs.connect();

    // Subscribe to events
    const unsubscribe = transferWs.onEvent((ev) => {
      setLastMessage(ev);
    });

    // Poll isOpen briefly to reflect connection state without us needing to
    // add events to the singleton API (keeps the wrapper lean).
    const interval = setInterval(() => {
      setIsConnected(transferWs.isOpen());
    }, 500);

    return () => {
      unsubscribe();
      clearInterval(interval);
    };
  }, []);

  return { isConnected, lastMessage };
}