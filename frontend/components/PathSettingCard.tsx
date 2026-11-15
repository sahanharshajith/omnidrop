"use client";

import { useCallback, useEffect, useState } from "react";
import { api, StorageInfo } from "@/services/api";
import { TransferProgress } from "@/types";
import { useWebSocket } from "@/hooks/useWebSocket";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { formatBytes } from "@/lib/utils";
import { FolderOpen, HardDrive, Info, AlertCircle } from "lucide-react";

/**
 * MEMBER 3: Shows where received files are saved and how much disk space is left.
 * Read-only on purpose (see the note at the bottom of the card).
 */
export function PathSettingCard() {
  const [info, setInfo] = useState<StorageInfo | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { lastMessage } = useWebSocket();

  const loadInfo = useCallback(async () => {
    try {
      setInfo(await api.getStorageInfo());
      setErrorMessage(null);
    } catch (e: any) {
      setErrorMessage(e.message || "Could not load storage info");
    }
  }, []);

  useEffect(() => {
    loadInfo();
  }, [loadInfo]);

  // Update the numbers when a new file arrives
  useEffect(() => {
    if (!lastMessage || lastMessage.type !== "TRANSFER_COMPLETED") return;
    const progress = lastMessage.payload as TransferProgress;
    if (progress.role === "RECEIVER") {
      loadInfo();
    }
  }, [lastMessage, loadInfo]);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Download Location</CardTitle>
        <CardDescription className="text-xs">Where incoming files are saved on this device</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {errorMessage && (
          <div className="flex items-center space-x-2 rounded-lg bg-rose-50 p-2.5 text-xs text-rose-700">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
            <span>{errorMessage}</span>
          </div>
        )}

        {info && (
          <>
            <div className="flex items-start space-x-2.5 rounded-lg bg-slate-50 p-3">
              <FolderOpen className="h-5 w-5 text-indigo-500 shrink-0 mt-0.5" />
              <p className="text-xs font-mono text-slate-700 break-all">{info.downloadsDir}</p>
            </div>

            <div className="grid grid-cols-3 gap-2 text-center text-xs">
              <div className="rounded-lg bg-slate-50 p-2">
                <p className="text-slate-500">Files</p>
                <p className="text-base font-bold text-slate-900">{info.fileCount}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-2">
                <p className="text-slate-500">Used</p>
                <p className="text-base font-bold text-slate-900">{formatBytes(info.totalBytes)}</p>
              </div>
              <div className="rounded-lg bg-emerald-50 p-2 text-emerald-800">
                <p className="flex items-center justify-center">
                  <HardDrive className="mr-1 h-3 w-3" />
                  Free
                </p>
                <p className="text-base font-bold">{formatBytes(info.usableSpaceBytes)}</p>
              </div>
            </div>
          </>
        )}

        <div className="flex items-start space-x-2 rounded-lg border border-indigo-100 bg-indigo-50/70 p-3 text-[11px] text-indigo-900">
          <Info className="h-3.5 w-3.5 text-indigo-600 shrink-0 mt-0.5" />
          <span>
            For safety, the folder cannot be changed from the browser (any device on the network could
            otherwise choose where files are written). To change it, set{" "}
            <span className="font-mono">omnidrop.storage.downloads-dir</span> in{" "}
            <span className="font-mono">application.properties</span> and restart the backend.
            Senders can never choose the folder or use paths like <span className="font-mono">../</span>.
          </span>
        </div>
      </CardContent>
    </Card>
  );
}