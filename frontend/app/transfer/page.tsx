"use client";

import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { PeerDevice, TransferProgress } from "@/types";
import { api } from "@/services/api";
import { useWebSocket } from "@/hooks/useWebSocket";
import { TransferProgressCard } from "@/components/TransferProgressCard";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { formatBytes } from "@/lib/utils";
import { Send, Upload, File as FileIcon, CheckCircle2, AlertCircle, Info } from "lucide-react";

function TransferContent() {
  const searchParams = useSearchParams();
  const preselectedIp = searchParams.get("peerIp") || "";
  const preselectedPort = searchParams.get("peerPort") || "9090";
  const preselectedName = searchParams.get("peerName") || "";

  const [peers, setPeers] = useState<PeerDevice[]>([]);
  const [selectedPeerIp, setSelectedPeerIp] = useState(preselectedIp);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [recentTransfers, setRecentTransfers] = useState<TransferProgress[]>([]);

  const { lastMessage } = useWebSocket();

  useEffect(() => {
    api.getDevices().then(setPeers).catch(console.warn);
  }, []);

  useEffect(() => {
    if (!lastMessage) return;
    if (
      lastMessage.type === "TRANSFER_STARTED" ||
      lastMessage.type === "TRANSFER_PROGRESS" ||
      lastMessage.type === "TRANSFER_COMPLETED" ||
      lastMessage.type === "TRANSFER_INTERRUPTED"
    ) {
      const progress = lastMessage.payload as TransferProgress;
      setRecentTransfers((prev) => {
        const existingIdx = prev.findIndex((p) => p.transferId === progress.transferId);
        if (existingIdx >= 0) {
          const updated = [...prev];
          updated[existingIdx] = progress;
          return updated;
        }
        return [progress, ...prev];
      });
    }
  }, [lastMessage]);

  const handleSend = async () => {
    if (!selectedFile) {
      setErrorMessage("Please select a file to transfer.");
      return;
    }
    if (!selectedPeerIp) {
      setErrorMessage("Please select a target receiving peer.");
      return;
    }

    setErrorMessage(null);
    setStatusMessage(null);
    setIsSubmitting(true);

    try {
      const targetPeer = peers.find((p) => p.ipAddress === selectedPeerIp);
      const targetPort = targetPeer ? targetPeer.tcpPort : parseInt(preselectedPort, 10);
      const targetName = targetPeer ? targetPeer.deviceName : preselectedName || selectedPeerIp;

      await api.sendFile(selectedFile, selectedPeerIp, targetPort, targetName);
      setStatusMessage(`File '${selectedFile.name}' dispatched to TCP stream!`);
      setSelectedFile(null);
    } catch (e: any) {
      setErrorMessage(e.message || "Failed to start transfer");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Point-to-Point TCP Transfer</h1>
            <Badge variant="default" className="text-xs">Members 2 & 3 (TCP)</Badge>
          </div>
          <p className="text-xs sm:text-sm text-slate-500">
            Streams binary files directly between sender and receiver using Java TCP sockets and 20-byte binary framing.
          </p>
        </div>
      </div>

      {/* Protocol Framing Note */}
      <div className="rounded-xl border border-indigo-100 bg-indigo-50/70 p-4 text-xs text-indigo-900 flex items-start space-x-3">
        <Info className="h-4 w-4 text-indigo-600 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold">Binary Framing:</span> TCP transfers are divided into 64 KB chunks, each prefixed with a 20-byte header containing magic bytes (<span className="font-mono">0x4F4D4E49</span>), opcode, chunk index, payload length, and CRC32 checksum.
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Transfer Setup Form */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Initiate File Transfer</CardTitle>
            <CardDescription className="text-xs">Choose a local file and a discovered peer device</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Target Peer Selector */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Target Receiver</label>
              {peers.length === 0 ? (
                <div className="text-xs text-slate-500 rounded-lg border border-slate-200 p-3 bg-slate-50">
                  No peers discovered yet. You can still specify an IP manually if known.
                  <input
                    type="text"
                    placeholder="Receiver IP (e.g. 192.168.1.50)"
                    value={selectedPeerIp}
                    onChange={(e) => setSelectedPeerIp(e.target.value)}
                    className="mt-2 w-full rounded-md border border-slate-300 px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              ) : (
                <select
                  value={selectedPeerIp}
                  onChange={(e) => setSelectedPeerIp(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="">-- Select Discovered Device --</option>
                  {peers.map((p) => (
                    <option key={p.deviceId} value={p.ipAddress}>
                      {p.deviceName} ({p.ipAddress}:{p.tcpPort})
                    </option>
                  ))}
                </select>
              )}
            </div>

            {/* File Dropzone */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Select File</label>
              <div className="relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 p-6 hover:border-indigo-500 transition-colors bg-slate-50/50">
                <input
                  type="file"
                  onChange={(e) => {
                    if (e.target.files && e.target.files[0]) {
                      setSelectedFile(e.target.files[0]);
                    }
                  }}
                  className="absolute inset-0 opacity-0 cursor-pointer"
                />
                <Upload className="h-8 w-8 text-slate-400 mb-2" />
                <span className="text-xs font-semibold text-slate-700">
                  {selectedFile ? selectedFile.name : "Click or drag file here"}
                </span>
                <span className="text-[11px] text-slate-400 mt-1">
                  {selectedFile ? `${formatBytes(selectedFile.size)}` : "Any file type supported without internet"}
                </span>
              </div>
            </div>

            {errorMessage && (
              <div className="flex items-center space-x-2 rounded-lg bg-rose-50 p-3 text-xs text-rose-700">
                <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
                <span>{errorMessage}</span>
              </div>
            )}

            {statusMessage && (
              <div className="flex items-center space-x-2 rounded-lg bg-emerald-50 p-3 text-xs text-emerald-700">
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" />
                <span>{statusMessage}</span>
              </div>
            )}

            <Button
              onClick={handleSend}
              disabled={isSubmitting || !selectedFile || !selectedPeerIp}
              className="w-full font-semibold"
            >
              <Send className="mr-2 h-4 w-4" />
              {isSubmitting ? "Connecting to Socket..." : "Transmit via TCP"}
            </Button>
          </CardContent>
        </Card>

        {/* Live Progress Feed */}
        <div className="space-y-4">
          <h3 className="text-base font-bold text-slate-900">Transfer Stream Monitor</h3>
          {recentTransfers.length === 0 ? (
            <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center">
              <FileIcon className="mx-auto h-8 w-8 text-slate-400" />
              <p className="mt-2 text-xs text-slate-500">No active or recent transfers in this session.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {recentTransfers.map((p) => (
                <TransferProgressCard key={p.transferId} progress={p} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function TransferPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-xs text-slate-500">Loading Transfer Console...</div>}>
      <TransferContent />
    </Suspense>
  );
}
