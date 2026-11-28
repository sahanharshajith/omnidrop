"use client";

import { LivePeer } from "@/types";
import { getPeerStatus, PeerStatus, radarAngles } from "@/lib/peerStatus";

interface RadarViewProps {
  peers: LivePeer[];
  now: number;
  selfName?: string;
  selectedId?: string | null;
  onSelect?: (peer: LivePeer) => void;
}

const SIZE = 300;
const CENTER = SIZE / 2;
const MAX_R = 128;

// Fresher peers sit closer to the centre
const RADIUS: Record<PeerStatus, number> = { online: 0.48, weak: 0.74, offline: 0.94 };
const FILL: Record<PeerStatus, string> = { online: "#10b981", weak: "#f59e0b", offline: "#94a3b8" };

/**
 * MEMBER 1: Radar view of discovered devices. This node is in the centre; peers are spread evenly
 * around it, and a dot moves further out as its heartbeat gets older.
 */
export function RadarView({ peers, now, selfName = "This device", selectedId, onSelect }: RadarViewProps) {
  const angles = radarAngles(peers.map((p) => p.deviceId));
  return (
    <div className="mx-auto w-full max-w-[320px]">
      <svg
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        className="h-auto w-full"
        role="img"
        aria-label={`Discovery radar showing ${peers.length} nearby device${peers.length === 1 ? "" : "s"}`}
      >
        <defs>
          <radialGradient id="radar-bg" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#eef2ff" />
            <stop offset="100%" stopColor="#ffffff" />
          </radialGradient>
          <linearGradient id="radar-sweep" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#6366f1" stopOpacity="0" />
            <stop offset="100%" stopColor="#6366f1" stopOpacity="0.35" />
          </linearGradient>
        </defs>

        <circle cx={CENTER} cy={CENTER} r={MAX_R + 10} fill="url(#radar-bg)" stroke="#e0e7ff" />
        {[0.33, 0.66, 1].map((f) => (
          <circle key={f} cx={CENTER} cy={CENTER} r={MAX_R * f} fill="none" stroke="#c7d2fe" strokeDasharray="3 4" />
        ))}
        <line x1={CENTER - MAX_R} y1={CENTER} x2={CENTER + MAX_R} y2={CENTER} stroke="#e0e7ff" />
        <line x1={CENTER} y1={CENTER - MAX_R} x2={CENTER} y2={CENTER + MAX_R} stroke="#e0e7ff" />

        {/* Rotating sweep (stops for users who prefer reduced motion) */}
        <g
          className="animate-[spin_4s_linear_infinite] motion-reduce:animate-none"
          style={{ transformOrigin: `${CENTER}px ${CENTER}px` }}
          data-testid="radar-sweep"
        >
          <path
            d={`M ${CENTER} ${CENTER} L ${CENTER + MAX_R} ${CENTER} A ${MAX_R} ${MAX_R} 0 0 0 ${CENTER + MAX_R * Math.cos(Math.PI / 4)} ${CENTER - MAX_R * Math.sin(Math.PI / 4)} Z`}
            fill="url(#radar-sweep)"
          />
        </g>

        {/* This node */}
        <circle cx={CENTER} cy={CENTER} r={7} fill="#4f46e5" />
        <circle cx={CENTER} cy={CENTER} r={12} fill="none" stroke="#4f46e5" strokeOpacity="0.3" />
        <text x={CENTER} y={CENTER + 26} textAnchor="middle" fontSize="10" fill="#475569">
          {selfName}
        </text>

        {/* Peers */}
        {peers.map((peer) => {
          const status = getPeerStatus(now - peer.seenAt);
          const angle = angles.get(peer.deviceId) ?? 0;
          const r = MAX_R * RADIUS[status];
          const x = CENTER + r * Math.cos(angle);
          const y = CENTER - r * Math.sin(angle);
          const selected = peer.deviceId === selectedId;
          const label = `${peer.deviceName}, ${peer.ipAddress}, ${status}`;
          return (
            <g
              key={peer.deviceId}
              role="button"
              tabIndex={0}
              aria-label={label}
              aria-pressed={selected}
              data-testid="radar-peer"
              className="cursor-pointer focus:outline-none"
              onClick={() => onSelect?.(peer)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  onSelect?.(peer);
                }
              }}
            >
              <title>{label}</title>
              {/* bigger invisible circle = easier to tap on phones */}
              <circle cx={x} cy={y} r={16} fill="transparent" />
              {selected && <circle cx={x} cy={y} r={11} fill="none" stroke="#4f46e5" strokeWidth={2} />}
              <circle cx={x} cy={y} r={6.5} fill={FILL[status]} stroke="#ffffff" strokeWidth={2} />
              <text x={x} y={y - 12} textAnchor="middle" fontSize="9.5" fontWeight={600} fill="#334155">
                {peer.deviceName.length > 14 ? `${peer.deviceName.slice(0, 13)}…` : peer.deviceName}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
