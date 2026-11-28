"use client";

import { useEffect, useState } from "react";

/** Current time (ms) that re-renders the component every `tickMs`. */
export function useNow(tickMs: number = 1000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), tickMs);
    return () => clearInterval(timer);
  }, [tickMs]);
  return now;
}
