"use client";

import { useEffect, useState } from "react";
import { Checkpoint } from "@/types";
import { api } from "@/services/api";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { formatBytes } from "@/lib/utils";
import { RefreshCw, ShieldCheck, FileWarning, Play, Info, CheckCircle2 } from "lucide-react";

export default function RecoveryPage() {
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const loadCheckpoints = async () => {
    setIsLoading(true);
    try {
      const data = await api.getCheckpoints();
      setCheckpoints(data);
    } catch (e) {
      console.warn("Failed fetching checkpoints:", e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadCheckpoints();
  }, []);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Transfer Recovery & Resumption</h1>
            <Badge variant="default" className="text-xs">Member 5 (Recovery)</Badge>
          </div>
          <p className="text-xs sm:text-sm text-slate-500">
            Resumes interrupted file transfers from saved byte offsets without retransmitting previously received chunks.
          </p>
        </div>

        <Button variant="outline" size="sm" onClick={loadCheckpoints} className="text-xs font-semibold">
          <RefreshCw className="mr-2 h-3.5 w-3.5" /> Refresh Checkpoints
        </Button>
      </div>

      {/* Concept Note */}
      <div className="rounded-xl border border-indigo-100 bg-indigo-50/70 p-4 text-xs text-indigo-900 flex items-start space-x-3">
        <Info className="h-4 w-4 text-indigo-600 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold">Byte-Level Resumption & Integrity:</span> Checkpoints record byte offsets written to disk. Upon receiving a <span className="font-mono">RESUME_REQ</span> frame, the sender positions its stream using Java NIO <span className="font-mono">FileChannel.position()</span>. When complete, SHA-256 verifies that no bits were corrupted during interrupted streaming.
        </div>
      </div>

      {/* Checkpoints List */}
      <div className="space-y-4">
        <h3 className="text-base font-bold text-slate-900">Interrupted Transfers on This Node</h3>

        {checkpoints.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
            <ShieldCheck className="mx-auto h-12 w-12 text-emerald-500" />
            <h4 className="mt-3 font-semibold text-slate-900">All Transfers Healthy</h4>
            <p className="mt-1 text-xs text-slate-500 max-w-sm mx-auto">
              No partial files or interrupted downloads are currently awaiting recovery.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {checkpoints.map((cp) => {
              const percentage = (cp.lastOffset / cp.fileSize) * 100;
              return (
                <Card key={cp.transferId} className="border-amber-200 bg-white">
                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between">
                      <div className="flex items-center space-x-2.5">
                        <FileWarning className="h-5 w-5 text-amber-500 shrink-0" />
                        <div>
                          <CardTitle className="text-sm font-bold text-slate-900 truncate max-w-[220px]">
                            {cp.fileName}
                          </CardTitle>
                          <p className="text-[11px] font-mono text-slate-400">ID: {cp.transferId.substring(0, 12)}...</p>
                        </div>
                      </div>
                      <Badge variant="warning">Partial ({percentage.toFixed(0)}%)</Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-slate-600 font-medium">
                        <span>{formatBytes(cp.lastOffset)} saved</span>
                        <span>{formatBytes(cp.fileSize)} total</span>
                      </div>
                      <Progress value={percentage} indicatorClassName="bg-amber-500" />
                    </div>

                    <div className="rounded bg-slate-50 p-2.5 text-[11px] font-mono text-slate-600 space-y-1">
                      <p className="truncate">Saved Chunk: {cp.lastChunkIndex}</p>
                      <p className="truncate">Expected SHA-256: {cp.expectedSha256 || "Pending verification"}</p>
                    </div>

                    <div className="pt-2 flex justify-end">
                      <Button size="sm" className="bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold">
                        <Play className="mr-1.5 h-3.5 w-3.5" /> Resume Transfer
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
