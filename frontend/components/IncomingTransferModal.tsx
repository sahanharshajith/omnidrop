"use client";

import { useEffect, useState } from "react";
import { TransferProgress } from "@/types";
import { useWebSocket } from "@/hooks/useWebSocket";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Button } from "@/components/ui/button";
import { formatBytes } from "@/lib/utils";
import { Download, X, CheckCircle2, AlertCircle } from "lucide-react";

// Only these WebSocket events are about transfers
const TRANSFER_EVENTS = [
  "TRANSFER_STARTED",
  "TRANSFER_PROGRESS",
  "TRANSFER_COMPLETED",
  "TRANSFER_FAILED",
  "TRANSFER_INTERRUPTED",
];

/**
 * MEMBER 3: Shows a small panel when a file is arriving from another device.
 * Put it once in the layout so it appears on every page.
 */
export function IncomingTransferModal() {
  const [incoming, setIncoming] = useState<TransferProgress[]>([]);
  const { lastMessage } = useWebSocket();

  const dismiss = (transferId: string) => {
    setIncoming((prev) => prev.filter((t) => t.transferId !== transferId));
  };

  useEffect(() => {
    if (!lastMessage) return;
    if (!TRANSFER_EVENTS.includes(lastMessage.type)) return;

    const progress = lastMessage.payload as TransferProgress;

    // Ignore transfers where this device is the sender
    if (progress.role !== "RECEIVER") return;

    // Add a new card, or update the card with the same transferId
    setIncoming((prev) => {
      const index = prev.findIndex((t) => t.transferId === progress.transferId);
      if (index >= 0) {
        const updated = [...prev];
        updated[index] = progress;
        return updated;
      }
      return [progress, ...prev];
    });

    // Hide finished transfers after 8 seconds
    if (lastMessage.type === "TRANSFER_COMPLETED") {
      setTimeout(() => dismiss(progress.transferId), 8000);
    }
  }, [lastMessage]);

  if (incoming.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 left-4 sm:left-auto sm:w-96 z-50 space-y-3">
      {incoming.map((t) => {
        const isDone = t.state === "COMPLETED";
        const isProblem = t.state === "FAILED" || t.state === "INTERRUPTED";

        return (
          <Card key={t.transferId} className="border-indigo-200 shadow-lg">
            <CardContent className="p-4 space-y-3">
              {/* Title row */}
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center space-x-2 min-w-0">
                  {isDone ? (
                    <CheckCircle2 className="h-5 w-5 text-emerald-500 shrink-0" />
                  ) : isProblem ? (
                    <AlertCircle className="h-5 w-5 text-rose-500 shrink-0" />
                  ) : (
                    <Download className="h-5 w-5 text-indigo-600 shrink-0" />
                  )}
                  <div className="min-w-0">
                    <p className="text-xs font-semibold text-slate-900 truncate">
                      {isDone ? "File received" : isProblem ? "Transfer problem" : "Incoming file"}
                    </p>
                    <p className="text-[11px] text-slate-500 truncate">
                      From {t.peerIp || "unknown device"}
                    </p>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => dismiss(t.transferId)}
                  className="h-6 w-6 p-0 text-slate-400"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>

              {/* File name and size */}
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-medium text-slate-800 truncate">{t.fileName}</p>
                <Badge variant={isDone ? "success" : isProblem ? "warning" : "default"} className="text-[10px]">
                  {t.state}
                </Badge>
              </div>

              {/* Progress bar */}
              <div className="space-y-1">
                <Progress value={t.percentage} />
                <div className="flex justify-between text-[11px] text-slate-500">
                  <span>
                    {formatBytes(t.bytesTransferred)} / {formatBytes(t.totalBytes)}
                  </span>
                  <span>{t.speedFormatted ?? ""}</span>
                </div>
              </div>

              {/* Error text, only if something went wrong */}
              {isProblem && t.errorMessage && (
                <p className="text-[11px] text-rose-700 bg-rose-50 rounded p-2">{t.errorMessage}</p>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}