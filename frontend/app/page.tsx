"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PeerDevice, TransferProgress, NodeHealth } from "@/types";
import { api } from "@/services/api";
import { useWebSocket } from "@/hooks/useWebSocket";
import { PeerCard } from "@/components/PeerCard";
import { TransferProgressCard } from "@/components/TransferProgressCard";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Send, Share2, Radio, RefreshCw, HardDrive, ShieldCheck, Zap, ArrowRight } from "lucide-react";

export default function DashboardPage() {
  const [health, setHealth] = useState<NodeHealth | null>(null);
  const [peers, setPeers] = useState<PeerDevice[]>([]);
  const [activeTransfers, setActiveTransfers] = useState<Map<String, TransferProgress>>(new Map());
  const [isLoading, setIsLoading] = useState(true);
  const { isConnected, lastMessage } = useWebSocket();

  const fetchInitialData = async () => {
    try {
      const [h, d] = await Promise.all([api.getHealth(), api.getDevices()]);
      setHealth(h);
      setPeers(d);
    } catch (e) {
      console.warn("Failed fetching dashboard state:", e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, []);

  // Handle incoming real-time events from Java backend
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
    } else if (
      lastMessage.type === "TRANSFER_STARTED" ||
      lastMessage.type === "TRANSFER_PROGRESS" ||
      lastMessage.type === "TRANSFER_COMPLETED" ||
      lastMessage.type === "TRANSFER_INTERRUPTED" ||
      lastMessage.type === "TRANSFER_FAILED"
    ) {
      const progress = lastMessage.payload as TransferProgress;
      setActiveTransfers((prev) => {
        const next = new Map(prev);
        next.set(progress.transferId, progress);
        return next;
      });
    }
  }, [lastMessage]);

  return (
    <div className="space-y-6">
      {/* Node Identity & Network Overview Banner */}
      <div className="rounded-2xl bg-gradient-to-r from-indigo-900 via-indigo-800 to-indigo-950 p-6 text-white shadow-lg">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center space-x-2">
              <span className="text-2xl font-black tracking-tight">{health?.nodeName || "OmniDrop Node"}</span>
              <Badge variant="outline" className="border-indigo-400 text-indigo-200 text-xs">
                {health?.status === "UP" ? "Agent Running" : "Offline Mode"}
              </Badge>
            </div>
            <p className="text-sm text-indigo-200">
              IP: <span className="font-mono text-white font-semibold">{health?.ipAddresses?.[0] || "127.0.0.1"}</span> | TCP Port: {health?.tcpPort || 9090} | UDP Port: {health?.udpPort || 8888}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <Link href="/transfer">
              <Button className="bg-white text-indigo-900 hover:bg-indigo-50 font-semibold shadow">
                <Send className="mr-2 h-4 w-4" /> Send File
              </Button>
            </Link>
            <Link href="/distribution">
              <Button variant="secondary" className="bg-indigo-700/80 text-white hover:bg-indigo-700 border border-indigo-500/30">
                <Share2 className="mr-2 h-4 w-4" /> 1-to-Many
              </Button>
            </Link>
            <Button
              variant="outline"
              size="icon"
              onClick={() => {
                api.triggerDiscovery();
                fetchInitialData();
              }}
              className="border-indigo-400/40 text-white hover:bg-indigo-800"
              title="Broadcast UDP Ping"
            >
              <Radio className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {/* Network Concepts Highlight Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center space-x-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
              <Radio className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">UDP Discovery</p>
              <p className="text-base font-bold text-slate-900">{peers.length} Peers Online</p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center space-x-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
              <Zap className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">TCP Streaming</p>
              <p className="text-base font-bold text-slate-900">64 KB Framed Chunks</p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center space-x-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
              <RefreshCw className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Recovery Checkpoints</p>
              <p className="text-base font-bold text-slate-900">FileChannel Resume</p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center space-x-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-purple-600">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Integrity</p>
              <p className="text-base font-bold text-slate-900">SHA-256 & CRC32</p>
            </div>
          </div>
        </div>
      </div>

      {/* Discovered Nearby Devices Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-bold text-slate-900">Nearby Devices</h3>
            <p className="text-xs text-slate-500">Auto-discovered via UDP broadcast datagrams on port 8888</p>
          </div>
          <Link href="/discovery">
            <Button variant="ghost" size="sm" className="text-xs text-indigo-600 font-semibold">
              View All ({peers.length}) <ArrowRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </Link>
        </div>

        {peers.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center">
            <Radio className="mx-auto h-10 w-10 text-slate-400 animate-pulse" />
            <h4 className="mt-3 font-semibold text-slate-900">Listening for Nearby Devices</h4>
            <p className="mt-1 text-xs text-slate-500 max-w-md mx-auto">
              Make sure other devices on this Wi-Fi network are running OmniDrop. Devices will appear here automatically within 3 seconds.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => api.triggerDiscovery()}
              className="mt-4 text-xs font-semibold"
            >
              Broadcast Discovery Beacon Now
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {peers.slice(0, 3).map((peer) => (
              <PeerCard key={peer.deviceId} peer={peer} />
            ))}
          </div>
        )}
      </div>

      {/* Active & Recent Transfers Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-bold text-slate-900">Active Transfers</h3>
            <p className="text-xs text-slate-500">Live progress streamed via WebSocket events</p>
          </div>
          <Link href="/recovery">
            <Button variant="ghost" size="sm" className="text-xs text-indigo-600 font-semibold">
              Recovery Center <ArrowRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </Link>
        </div>

        {activeTransfers.size === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center">
            <HardDrive className="mx-auto h-10 w-10 text-slate-400" />
            <h4 className="mt-3 font-semibold text-slate-900">No Transfers Active</h4>
            <p className="mt-1 text-xs text-slate-500">Select a file and send it to a discovered peer to begin.</p>
            <Link href="/transfer">
              <Button size="sm" className="mt-4 text-xs font-semibold">
                Send a File
              </Button>
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {Array.from(activeTransfers.values()).map((p) => (
              <TransferProgressCard key={p.transferId} progress={p} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
