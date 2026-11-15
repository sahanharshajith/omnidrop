"use client";

import { Badge } from "@/components/ui/badge";
import { ReceivedFilesList } from "@/components/ReceivedFilesList";
import { PathSettingCard } from "@/components/PathSettingCard";

export default function ReceivedPage() {
  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center space-x-2">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Received Files</h1>
          <Badge variant="default" className="text-xs">Member 3 (TCP Receiver)</Badge>
        </div>
        <p className="text-xs sm:text-sm text-slate-500">
          Files received through the Java TCP ServerSocket are saved to this device&apos;s downloads folder.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <ReceivedFilesList />
        </div>
        <PathSettingCard />
      </div>
    </div>
  );
}