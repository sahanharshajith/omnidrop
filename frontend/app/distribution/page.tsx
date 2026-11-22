"use client";

import { useEffect, useState } from "react";
import { DistributionProgress, PeerDevice } from "@/types";
import { api } from "@/services/api";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { formatBytes } from "@/lib/utils";
import { Share2, Users, Upload, AlertCircle, Info, CheckSquare, Square } from "lucide-react";

export default function DistributionPage() {
  const [peers, setPeers] = useState<PeerDevice[]>([]);
  const [selectedPeerIds, setSelectedPeerIds] = useState<Set<string>>(new Set());
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [distributionId, setDistributionId] = useState<string | null>(null);
  const [distributionSummary, setDistributionSummary] = useState<DistributionProgress | null>(null);
  const [progressError, setProgressError] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    api.getDevices().then(setPeers).catch(console.warn);
  }, []);

  useEffect(() => {
    if (!distributionId) return;
    const id = distributionId;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;

    async function refresh() {
      try {
        const progress = await api.getDistributionProgress(id, controller.signal);
        if (controller.signal.aborted) return;
        setDistributionSummary(progress);
        setProgressError(false);
        if (progress.status !== "IN_PROGRESS") return;
      } catch {
        if (controller.signal.aborted) return;
        setProgressError(true);
      }
      timer = setTimeout(refresh, 500);
    }

    void refresh();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [distributionId]);

  const aggregatePercent = distributionSummary
    ? Math.round(100 * distributionSummary.bytesTransferred /
        (distributionSummary.fileSize * distributionSummary.totalReceivers))
    : 0;

  const toggleSelectPeer = (deviceId: string) => {
    setSelectedPeerIds((prev) => {
      const next = new Set(prev);
      if (next.has(deviceId)) next.delete(deviceId);
      else next.add(deviceId);
      return next;
    });
  };

  const selectAll = () => {
    setSelectedPeerIds(new Set(peers.map((p) => p.deviceId)));
  };

  const deselectAll = () => {
    setSelectedPeerIds(new Set());
  };

  const handleStartDistribution = async () => {
    if (!selectedFile) {
      setErrorMessage("Please select a file to distribute.");
      return;
    }
    if (selectedPeerIds.size === 0) {
      setErrorMessage("Please select at least one receiving peer.");
      return;
    }

    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      const targets = peers.filter((p) => selectedPeerIds.has(p.deviceId));
      const result = await api.startDistribution(selectedFile, targets);
      setDistributionSummary(null);
      setProgressError(false);
      setDistributionId(result.distributionId);
      setSelectedFile(null);
    } catch (e: any) {
      setErrorMessage(e.message || "Failed to start distribution");
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
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">One-to-Many Distribution</h1>
            <Badge variant="default" className="text-xs">Member 4 (Concurrency)</Badge>
          </div>
          <p className="text-xs sm:text-sm text-slate-500">
            Simultaneously distributes a single file to multiple receivers using concurrent Java TCP sockets and thread pooling.
          </p>
        </div>
      </div>

      {/* Network Concept Note */}
      <div className="rounded-xl border border-indigo-100 bg-indigo-50/70 p-4 text-xs text-indigo-900 flex items-start space-x-3">
        <Info className="h-4 w-4 text-indigo-600 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold">Thread Pool Architecture:</span> Transfers are parallelized via Java <span className="font-mono">ExecutorService</span> with isolated worker threads. If one receiver has high latency or disconnects, other concurrent streams continue uninterrupted.
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Setup Column */}
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">1. Select File for Distribution</CardTitle>
            </CardHeader>
            <CardContent>
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
                <Upload className="h-7 w-7 text-slate-400 mb-2" />
                <span className="text-xs font-semibold text-slate-700">
                  {selectedFile ? selectedFile.name : "Choose file to broadcast"}
                </span>
                <span className="text-[11px] text-slate-400 mt-1">
                  {selectedFile ? formatBytes(selectedFile.size) : "Shared with all selected nodes simultaneously"}
                </span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-base">2. Target Receivers ({selectedPeerIds.size})</CardTitle>
                <CardDescription className="text-xs">Select nodes that will receive this file</CardDescription>
              </div>
              <div className="flex space-x-2">
                <Button variant="ghost" size="sm" onClick={selectAll} className="text-xs text-indigo-600">
                  Select All
                </Button>
                <Button variant="ghost" size="sm" onClick={deselectAll} className="text-xs text-slate-500">
                  Clear
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-2">
              {peers.length === 0 ? (
                <div className="rounded-lg border border-slate-200 p-4 text-center text-xs text-slate-500">
                  No peers discovered on local network yet.
                </div>
              ) : (
                peers.map((peer) => {
                  const isChecked = selectedPeerIds.has(peer.deviceId);
                  return (
                    <div
                      key={peer.deviceId}
                      onClick={() => toggleSelectPeer(peer.deviceId)}
                      className={`flex items-center justify-between rounded-lg border p-3 cursor-pointer transition-all ${
                        isChecked ? "border-indigo-500 bg-indigo-50/40" : "border-slate-200 hover:bg-slate-50"
                      }`}
                    >
                      <div className="flex items-center space-x-3">
                        {isChecked ? (
                          <CheckSquare className="h-5 w-5 text-indigo-600 shrink-0" />
                        ) : (
                          <Square className="h-5 w-5 text-slate-300 shrink-0" />
                        )}
                        <div>
                          <p className="text-xs font-semibold text-slate-900">{peer.deviceName}</p>
                          <p className="text-[11px] font-mono text-slate-500">{peer.ipAddress}:{peer.tcpPort}</p>
                        </div>
                      </div>
                      <Badge variant="outline" className="text-[10px]">Ready</Badge>
                    </div>
                  );
                })
              )}

              {errorMessage && (
                <div className="flex items-center space-x-2 rounded-lg bg-rose-50 p-2.5 text-xs text-rose-700 mt-3">
                  <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
                  <span>{errorMessage}</span>
                </div>
              )}

              <Button
                onClick={handleStartDistribution}
                disabled={isSubmitting || !selectedFile || selectedPeerIds.size === 0}
                className="w-full mt-4 font-semibold"
              >
                <Share2 className="mr-2 h-4 w-4" />
                {isSubmitting ? "Launching Threads..." : `Broadcast to ${selectedPeerIds.size} Devices`}
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* Live Distribution Progress Column */}
        <div className="space-y-4">
          <h3 className="text-base font-bold text-slate-900">Concurrent Distribution Status</h3>
          {progressError && <p role="status" className="text-xs text-rose-700">Progress unavailable. Retrying…</p>}
          {!distributionSummary ? (
            <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center">
              <Users className="mx-auto h-8 w-8 text-slate-400" />
              <p className="mt-2 text-xs text-slate-500">{distributionId ? "Loading distribution status…" : "Distribution batches will display live progress here."}</p>
            </div>
          ) : (
            <Card className="border-indigo-200">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base font-bold text-slate-900">
                    Distribution: {distributionSummary.fileName}
                  </CardTitle>
                  <Badge variant={distributionSummary.status === "SUCCESS" ? "success" : "outline"}>
                    {distributionSummary.status}
                  </Badge>
                </div>
                <CardDescription className="text-xs font-mono">
                  Batch ID: {distributionSummary.distributionId}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-3 gap-2 text-center text-xs">
                  <div className="rounded-lg bg-slate-50 p-2">
                    <p className="text-slate-500">Total Targets</p>
                    <p className="text-base font-bold text-slate-900">{distributionSummary.totalReceivers}</p>
                  </div>
                  <div className="rounded-lg bg-emerald-50 p-2 text-emerald-800">
                    <p>Completed</p>
                    <p className="text-base font-bold">{distributionSummary.completedReceivers}</p>
                  </div>
                  <div className="rounded-lg bg-rose-50 p-2 text-rose-800">
                    <p>Failed</p>
                    <p className="text-base font-bold">{distributionSummary.failedReceivers}</p>
                  </div>
                </div>

                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-slate-500">
                    <span>Total bytes delivered</span>
                    <span className="font-semibold text-slate-900">
                      {aggregatePercent}%
                    </span>
                  </div>
                  <Progress aria-label="Total bytes delivered" value={aggregatePercent} />
                </div>
                <ul className="space-y-3">
                  {distributionSummary.receivers.map((receiver, index) => {
                    const percent = Math.round(100 * receiver.bytesTransferred / distributionSummary.fileSize);
                    return (
                      <li key={index} className="space-y-1 border-t border-slate-100 pt-3">
                        <div className="flex justify-between gap-2 text-xs">
                          <span className="font-semibold">{receiver.deviceName}</span>
                          <span>{receiver.state} · {percent}%</span>
                        </div>
                        <Progress aria-label={receiver.deviceName} value={percent} />
                        {receiver.errorMessage && <p className="text-xs text-rose-700">{receiver.errorMessage}</p>}
                      </li>
                    );
                  })}
                </ul>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
