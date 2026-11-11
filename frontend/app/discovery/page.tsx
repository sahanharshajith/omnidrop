"use client";

import { useEffect, useState } from "react";
import { PeerDevice } from "@/types";
import { api } from "@/services/api";
import { useWebSocket } from "@/hooks/useWebSocket";
import { PeerCard } from "@/components/PeerCard";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Radio, RefreshCw, Smartphone, Laptop, Monitor, AlertCircle, Info } from "lucide-react";
import Link from "next/link";

export default function DiscoveryPage() {
  const [peers, setPeers] = useState<PeerDevice[]>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [selectedPeer, setSelectedPeer] = useState<PeerDevice | null>(null);
  const { lastMessage } = useWebSocket();

  const fetchPeers = async () => {
    setIsRefreshing(true);
    try {
      const data = await api.getDevices();
      setPeers(data);
    } catch (e) {
      console.warn("Failed fetching peers:", e);
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchPeers();
  }, []);

  useEffect(() => {
    if (!lastMessage) return;
    if (lastMessage.type === "DEVICE_DISCOVERED") {
      const newPeer = lastMessage.payload as PeerDevice;
      setPeers((prev) => {
        const filtered = prev.filter((p) => p.deviceId !== newPeer.deviceId);
        return [...filtered, newPeer];
      });
    } else if (lastMessage.type === "DEVICE_LOST") {
      const lostId = (lastMessage.payload as { deviceId: string }).deviceId;
      setPeers((prev) => prev.filter((p) => p.deviceId !== lostId));
    }
  }, [lastMessage]);

  const handleBroadcast = async () => {
    setIsRefreshing(true);
    await api.triggerDiscovery();
    setTimeout(() => {
      fetchPeers();
    }, 500);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Device Discovery</h1>
            <Badge variant="default" className="text-xs">Member 1 (UDP)</Badge>
          </div>
          <p className="text-xs sm:text-sm text-slate-500">
            Dynamically detects active nodes broadcasting on UDP port 8888 across the local subnet.
          </p>
        </div>

        <div className="flex items-center space-x-2">
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

      {/* Network Concept Note */}
      <div className="rounded-xl border border-indigo-100 bg-indigo-50/70 p-4 text-xs text-indigo-900 flex items-start space-x-3">
        <Info className="h-4 w-4 text-indigo-600 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold">How it works:</span> OmniDrop sends DatagramPackets to IPv4 broadcast addresses (<span className="font-mono">255.255.255.255</span>) every 3 seconds using Java <span className="font-mono">DatagramSocket</span>. Devices that stop sending heartbeats for 12 seconds are automatically dropped.
        </div>
      </div>

      {/* Peer Grid */}
      {peers.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
          <Radio className="mx-auto h-12 w-12 text-slate-400 animate-pulse" />
          <h3 className="mt-4 text-base font-semibold text-slate-900">No Nearby Devices Found Yet</h3>
          <p className="mt-1 text-xs text-slate-500 max-w-sm mx-auto">
            Connect devices to the same local Wi-Fi router or mobile hotspot and run OmniDrop on each machine.
          </p>
          <Button
            variant="default"
            size="sm"
            onClick={handleBroadcast}
            className="mt-5 text-xs font-semibold"
          >
            Transmit Discovery Ping
          </Button>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {peers.map((peer) => (
              <PeerCard
                key={peer.deviceId}
                peer={peer}
                isSelected={selectedPeer?.deviceId === peer.deviceId}
                onSelect={(p) => setSelectedPeer(p)}
              />
            ))}
          </div>

          {selectedPeer && (
            <div className="rounded-xl border border-indigo-200 bg-white p-5 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="font-semibold text-slate-900">Selected Device: {selectedPeer.deviceName}</h4>
                  <p className="text-xs text-slate-500">Target IP: {selectedPeer.ipAddress} | Port: {selectedPeer.tcpPort}</p>
                </div>
                <div className="flex space-x-2">
                  <Link href={`/transfer?peerIp=${selectedPeer.ipAddress}&peerPort=${selectedPeer.tcpPort}&peerName=${encodeURIComponent(selectedPeer.deviceName)}`}>
                    <Button size="sm" className="text-xs font-semibold">
                      Send File to This Device
                    </Button>
                  </Link>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
