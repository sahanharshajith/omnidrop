"use client";

import Link from "next/link";
import { Laptop, Monitor, Smartphone, Send } from "lucide-react";
import { LivePeer } from "@/types";
import { PeerStatusBadge } from "@/components/PeerStatusBadge";
import { Button } from "@/components/ui/button";
import { formatAge } from "@/lib/peerStatus";

interface DeviceDiscoveryCardProps {
  peer: LivePeer;
  now: number;
  isSelected?: boolean;
  onSelect?: (peer: LivePeer) => void;
}

function DeviceIcon({ name }: { name: string }) {
  const lower = name.toLowerCase();
  const cls = "h-6 w-6 text-indigo-600";
  if (/(phone|mobile|android|iphone|pixel|galaxy)/.test(lower)) return <Smartphone className={cls} />;
  if (/(macbook|laptop|thinkpad|notebook)/.test(lower)) return <Laptop className={cls} />;
  return <Monitor className={cls} />;
}

/**
 * MEMBER 1: Detailed card for one discovered device on the discovery page.
 */
export function DeviceDiscoveryCard({ peer, now, isSelected, onSelect }: DeviceDiscoveryCardProps) {
  const ageMs = now - peer.seenAt;
  const sendHref = `/transfer?peerIp=${encodeURIComponent(peer.ipAddress)}&peerPort=${peer.tcpPort}&peerName=${encodeURIComponent(peer.deviceName)}`;

  return (
    <div
      role="button"
      tabIndex={0}
      aria-pressed={!!isSelected}
      onClick={() => onSelect?.(peer)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect?.(peer);
        }
      }}
      className={`group flex flex-col justify-between rounded-xl border p-4 sm:p-5 transition-all cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
        isSelected
          ? "border-indigo-600 bg-indigo-50/50 shadow-md ring-2 ring-indigo-500/20"
          : "border-slate-200 bg-white hover:border-slate-300 hover:shadow-sm"
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-50 group-hover:bg-indigo-100 transition-colors">
            <DeviceIcon name={peer.deviceName} />
          </div>
          <div className="min-w-0">
            <h4 className="truncate font-semibold text-slate-900">{peer.deviceName}</h4>
            <p className="truncate font-mono text-xs text-slate-500">
              {peer.ipAddress}:{peer.tcpPort}
            </p>
          </div>
        </div>
        <PeerStatusBadge ageMs={ageMs} className="shrink-0" />
      </div>

      <div className="mt-4 flex items-center justify-between gap-2 border-t border-slate-100 pt-3 text-xs text-slate-500">
        <span>Last seen {formatAge(ageMs)}</span>
        <Link href={sendHref} onClick={(e) => e.stopPropagation()}>
          <Button size="sm" variant="ghost" className="h-7 text-xs font-semibold text-indigo-600 hover:text-indigo-700">
            <Send className="mr-1 h-3.5 w-3.5" /> Send file
          </Button>
        </Link>
      </div>
    </div>
  );
}
