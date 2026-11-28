"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Info, Radio, RefreshCw, WifiOff, AlertTriangle } from "lucide-react";
import { LivePeer, NodeHealth } from "@/types";
import { api } from "@/services/api";
import { usePeers } from "@/hooks/usePeers";
import { useNow } from "@/hooks/useNow";
import { useWebSocket } from "@/hooks/useWebSocket";
import { RadarView } from "@/components/RadarView";
import { DeviceDiscoveryCard } from "@/components/DeviceDiscoveryCard";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { BEACON_INTERVAL_MS, PEER_TIMEOUT_MS } from "@/lib/peerStatus";

/**
 * MEMBER 1: Device Discovery page — radar + live peer cards fed by UDP discovery.
 */
export default function DiscoveryPage() {
  const { peers, refresh } = usePeers();
  const { isConnected } = useWebSocket();
  const now = useNow(1000);
  const [health, setHealth] = useState<NodeHealth | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    api.getHealth().then(setHealth).catch(() => setHealth(null));
  }, []);

  const selectedPeer = peers.find((p) => p.deviceId === selectedId) ?? null;
  const onSelect = (peer: LivePeer) => setSelectedId(peer.deviceId);
  const discoveryDown = health?.discoveryRunning === false;

  const handleBroadcast = async () => {
    setIsRefreshing(true);
    try {
      await api.triggerDiscovery();
    } catch (e) {
      console.warn("Failed to trigger discovery:", e);
    }
    setTimeout(async () => {
      await refresh();
      setIsRefreshing(false);
    }, 500);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Device Discovery</h1>
            <Badge variant="default" className="text-xs">Member 1 (UDP)</Badge>
          </div>
          <p className="text-xs text-slate-500 sm:text-sm">
            Finds OmniDrop nodes broadcasting on UDP port {health?.udpPort ?? 8888} across the local subnet.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {!isConnected && (
            <Badge variant="warning" className="text-xs">
              <WifiOff className="mr-1 h-3 w-3" /> Reconnecting…
            </Badge>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={handleBroadcast}
            disabled={isRefreshing}
            className="text-xs font-semibold"
          >
            <RefreshCw className={`mr-2 h-3.5 w-3.5 ${isRefreshing ? "animate-spin" : ""}`} />
            Scan Subnet Now
          </Button>
        </div>
      </div>

      {/* Discovery offline warning (e.g. UDP port already in use) */}
      {discoveryDown && (
        <div role="alert" className="flex items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-900">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
          <div>
            <span className="font-semibold">Discovery is offline.</span>{" "}
            {health?.discoveryError ?? "The UDP listener is not running."} Close any other OmniDrop instance and restart the backend.
          </div>
        </div>
      )}

      {/* Network concept note */}
      <div className="flex items-start gap-3 rounded-xl border border-indigo-100 bg-indigo-50/70 p-4 text-xs text-indigo-900">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" />
        <div>
          <span className="font-semibold">How it works:</span> every node sends a small JSON{" "}
          <span className="font-mono">DatagramPacket</span> to each IPv4 broadcast address every{" "}
          {BEACON_INTERVAL_MS / 1000} seconds using Java <span className="font-mono">DatagramSocket</span>. Nodes silent for{" "}
          {PEER_TIMEOUT_MS / 1000} seconds are dropped, and a node that shuts down sends a LEAVE beacon so it disappears at once.
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
        {/* Radar */}
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <RadarView
            peers={peers}
            now={now}
            selfName={health?.nodeName ?? "This device"}
            selectedId={selectedId}
            onSelect={onSelect}
          />
          <div className="mt-3 flex flex-wrap justify-center gap-3 text-[11px] text-slate-500">
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-emerald-500" /> Online</span>
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-amber-500" /> Weak signal</span>
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-slate-400" /> Offline</span>
          </div>
        </div>

        {/* Peer list */}
        <div className="space-y-4">
          {peers.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center sm:p-12">
              <Radio className="mx-auto h-12 w-12 animate-pulse text-slate-400 motion-reduce:animate-none" />
              <h3 className="mt-4 text-base font-semibold text-slate-900">No nearby devices found yet</h3>
              <p className="mx-auto mt-1 max-w-sm text-xs text-slate-500">
                Connect devices to the same Wi-Fi router or mobile hotspot, run OmniDrop on each one, and allow UDP port 8888 through the firewall.
              </p>
              <Button variant="default" size="sm" onClick={handleBroadcast} className="mt-5 text-xs font-semibold">
                Send discovery ping
              </Button>
            </div>
          ) : (
            <>
              <p className="text-xs font-medium text-slate-500">
                {peers.length} device{peers.length === 1 ? "" : "s"} nearby
              </p>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {peers.map((peer) => (
                  <DeviceDiscoveryCard
                    key={peer.deviceId}
                    peer={peer}
                    now={now}
                    isSelected={peer.deviceId === selectedId}
                    onSelect={onSelect}
                  />
                ))}
              </div>
            </>
          )}

          {selectedPeer && (
            <div className="flex flex-col gap-3 rounded-xl border border-indigo-200 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <h4 className="truncate font-semibold text-slate-900">Selected: {selectedPeer.deviceName}</h4>
                <p className="text-xs text-slate-500">
                  Target IP: {selectedPeer.ipAddress} | TCP port: {selectedPeer.tcpPort}
                </p>
              </div>
              <Link
                href={`/transfer?peerIp=${encodeURIComponent(selectedPeer.ipAddress)}&peerPort=${selectedPeer.tcpPort}&peerName=${encodeURIComponent(selectedPeer.deviceName)}`}
              >
                <Button size="sm" className="w-full text-xs font-semibold sm:w-auto">
                  Send File to This Device
                </Button>
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
