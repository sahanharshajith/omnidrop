"use client";

import { Badge } from "@/components/ui/badge";
import { getPeerStatus, PeerStatus } from "@/lib/peerStatus";

const LABELS: Record<PeerStatus, string> = {
  online: "Online",
  weak: "Weak signal",
  offline: "Offline",
};

const VARIANTS: Record<PeerStatus, "success" | "warning" | "secondary"> = {
  online: "success",
  weak: "warning",
  offline: "secondary",
};

const DOTS: Record<PeerStatus, string> = {
  online: "bg-emerald-500",
  weak: "bg-amber-500",
  offline: "bg-slate-400",
};

interface PeerStatusBadgeProps {
  /** Milliseconds since the peer's last heartbeat. */
  ageMs: number;
  intervalMs?: number;
  timeoutMs?: number;
  className?: string;
}

/**
 * MEMBER 1: Online / Weak signal / Offline badge driven by heartbeat age.
 */
export function PeerStatusBadge({ ageMs, intervalMs, timeoutMs, className }: PeerStatusBadgeProps) {
  const status = getPeerStatus(ageMs, intervalMs, timeoutMs);
  return (
    <Badge variant={VARIANTS[status]} className={className} data-status={status}>
      <span className={`mr-1.5 inline-block h-1.5 w-1.5 rounded-full ${DOTS[status]}`} aria-hidden="true" />
      {LABELS[status]}
    </Badge>
  );
}
