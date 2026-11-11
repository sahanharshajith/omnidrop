"use client";

import { TransferProgress } from "@/types";
import { formatBytes, formatDuration } from "@/lib/utils";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { FileUp, FileDown, CheckCircle2, AlertCircle, Clock, Zap } from "lucide-react";

interface TransferProgressCardProps {
  progress: TransferProgress;
}

export function TransferProgressCard({ progress }: TransferProgressCardProps) {
  const isSender = progress.role === "SENDER";

  const getStatusBadge = () => {
    switch (progress.state) {
      case "COMPLETED":
        return <Badge variant="success">Completed</Badge>;
      case "INTERRUPTED":
        return <Badge variant="warning">Interrupted</Badge>;
      case "FAILED":
        return <Badge variant="destructive">Failed</Badge>;
      case "TRANSFERRING":
        return <Badge variant="default">Streaming</Badge>;
      default:
        return <Badge variant="secondary">{progress.state}</Badge>;
    }
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
      <div className="flex items-start justify-between">
        <div className="flex items-center space-x-3">
          <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${
            isSender ? "bg-indigo-50 text-indigo-600" : "bg-emerald-50 text-emerald-600"
          }`}>
            {isSender ? <FileUp className="h-6 w-6" /> : <FileDown className="h-6 w-6" />}
          </div>
          <div>
            <h4 className="font-semibold text-slate-900 truncate max-w-xs sm:max-w-md">
              {progress.fileName}
            </h4>
            <p className="text-xs text-slate-500">
              {isSender ? `To: ${progress.peerName || progress.peerIp}` : `From: ${progress.peerIp}`}
            </p>
          </div>
        </div>
        {getStatusBadge()}
      </div>

      <div className="space-y-1.5">
        <div className="flex justify-between text-xs text-slate-600 font-medium">
          <span>{formatBytes(progress.bytesTransferred)} / {formatBytes(progress.totalBytes)}</span>
          <span className="font-semibold text-indigo-600">{progress.percentage.toFixed(1)}%</span>
        </div>
        <Progress value={progress.percentage} />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-2 border-t border-slate-100 text-xs text-slate-500">
        <div className="flex items-center space-x-1.5">
          <Zap className="h-3.5 w-3.5 text-amber-500" />
          <span>{progress.speedFormatted || "Calculating..."}</span>
        </div>
        <div className="flex items-center space-x-1.5">
          <Clock className="h-3.5 w-3.5 text-slate-400" />
          <span>ETA: {formatDuration(progress.estimatedRemainingSeconds)}</span>
        </div>
        <div className="hidden sm:flex items-center space-x-1.5 justify-end">
          <span>Chunk {progress.currentChunk}/{progress.totalChunks}</span>
        </div>
      </div>

      {progress.errorMessage && (
        <div className="flex items-center space-x-2 rounded-lg bg-rose-50 p-2.5 text-xs text-rose-700">
          <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
          <span>{progress.errorMessage}</span>
        </div>
      )}
    </div>
  );
}
