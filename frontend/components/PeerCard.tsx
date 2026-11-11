"use client";

import { PeerDevice } from "@/types";
import { Laptop, Smartphone, Monitor, HardDrive, ArrowRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface PeerCardProps {
  peer: PeerDevice;
  onSelect?: (peer: PeerDevice) => void;
  isSelected?: boolean;
}

export function PeerCard({ peer, onSelect, isSelected }: PeerCardProps) {
  const getDeviceIcon = (name: string) => {
    const lower = name.toLowerCase();
    if (lower.includes("phone") || lower.includes("mobile") || lower.includes("android") || lower.includes("iphone")) {
      return <Smartphone className="h-6 w-6 text-indigo-600" />;
    }
    if (lower.includes("macbook") || lower.includes("laptop") || lower.includes("thinkpad")) {
      return <Laptop className="h-6 w-6 text-indigo-600" />;
    }
    return <Monitor className="h-6 w-6 text-indigo-600" />;
  };

  return (
    <div
      onClick={() => onSelect && onSelect(peer)}
      className={`group relative flex flex-col justify-between rounded-xl border p-5 transition-all cursor-pointer ${
        isSelected
          ? "border-indigo-600 bg-indigo-50/50 shadow-md ring-2 ring-indigo-500/20"
          : "border-slate-200 bg-white hover:border-slate-300 hover:shadow-sm"
      }`}
    >
      <div className="flex items-start justify-between">
        <div className="flex items-center space-x-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-50 group-hover:bg-indigo-100 transition-colors">
            {getDeviceIcon(peer.deviceName)}
          </div>
          <div>
            <h4 className="font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors">
              {peer.deviceName}
            </h4>
            <p className="text-xs font-mono text-slate-500">{peer.ipAddress}:{peer.tcpPort}</p>
          </div>
        </div>
        <Badge variant={peer.active ? "success" : "secondary"}>
          {peer.active ? "Online" : "Idle"}
        </Badge>
      </div>

      <div className="mt-4 flex items-center justify-between pt-3 border-t border-slate-100 text-xs text-slate-500">
        <div className="flex items-center space-x-1">
          <HardDrive className="h-3.5 w-3.5 text-slate-400" />
          <span>TCP :{peer.tcpPort}</span>
        </div>
        {onSelect && (
          <Button variant="ghost" size="sm" className="h-7 text-xs font-semibold text-indigo-600 hover:text-indigo-700">
            Select <ArrowRight className="h-3.5 w-3.5 ml-1" />
          </Button>
        )}
      </div>
    </div>
  );
}
