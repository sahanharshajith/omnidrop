"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ReceivedFile } from "@/services/api";
import { TransferProgress } from "@/types";
import { useWebSocket } from "@/hooks/useWebSocket";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { formatBytes } from "@/lib/utils";
import { Download, Trash2, RefreshCw, FileText, AlertCircle } from "lucide-react";

/**
 * MEMBER 3: Lists the files this device has received, with download and delete.
 */
export function ReceivedFilesList() {
  const [files, setFiles] = useState<ReceivedFile[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { lastMessage } = useWebSocket();

  const loadFiles = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await api.getReceivedFiles();
      // Newest file first
      setFiles([...data].sort((a, b) => b.lastModified - a.lastModified));
    } catch (e: any) {
      setErrorMessage(e.message || "Could not load files");
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Load once when the page opens
  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  // Reload automatically when a file finishes arriving on this device
  useEffect(() => {
    if (!lastMessage || lastMessage.type !== "TRANSFER_COMPLETED") return;
    const progress = lastMessage.payload as TransferProgress;
    if (progress.role === "RECEIVER") {
      loadFiles();
    }
  }, [lastMessage, loadFiles]);

  const handleDelete = async (fileName: string) => {
    if (!window.confirm(`Delete "${fileName}" from this device?`)) return;
    try {
      await api.deleteReceivedFile(fileName);
      setFiles((prev) => prev.filter((f) => f.fileName !== fileName));
    } catch (e: any) {
      setErrorMessage(e.message || "Could not delete file");
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle className="text-base">Received Files ({files.length})</CardTitle>
          <CardDescription className="text-xs">Files that arrived from other devices</CardDescription>
        </div>
        <Button variant="outline" size="sm" onClick={loadFiles} disabled={isLoading} className="text-xs">
          <RefreshCw className={`mr-2 h-3.5 w-3.5 ${isLoading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </CardHeader>

      <CardContent className="space-y-2">
        {errorMessage && (
          <div className="flex items-center space-x-2 rounded-lg bg-rose-50 p-2.5 text-xs text-rose-700">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
            <span>{errorMessage}</span>
          </div>
        )}

        {!isLoading && files.length === 0 && !errorMessage && (
          <div className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-xs text-slate-500">
            No files received yet.
          </div>
        )}

        {files.map((file) => (
          <div
            key={file.fileName}
            className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3"
          >
            <div className="flex items-center space-x-3 min-w-0">
              <FileText className="h-5 w-5 text-indigo-500 shrink-0" />
              <div className="min-w-0">
                <p className="text-xs font-semibold text-slate-900 truncate">{file.fileName}</p>
                <p className="text-[11px] text-slate-500">
                  {formatBytes(file.fileSize)} · {new Date(file.lastModified).toLocaleString()}
                </p>
              </div>
            </div>

            <div className="flex items-center space-x-1 shrink-0">
              {/* A normal link: the browser downloads the file */}
              <a href={api.getDownloadUrl(file.fileName)} download={file.fileName}>
                <Button variant="outline" size="sm" className="h-8 text-xs">
                  <Download className="mr-1.5 h-3.5 w-3.5" />
                  Download
                </Button>
              </a>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleDelete(file.fileName)}
                className="h-8 w-8 p-0 text-rose-500"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}