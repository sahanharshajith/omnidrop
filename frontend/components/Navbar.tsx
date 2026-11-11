"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Radio, Send, Share2, RefreshCw, LayoutDashboard, Wifi } from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface NavbarProps {
  isWsConnected: boolean;
}

export function Navbar({ isWsConnected }: NavbarProps) {
  const pathname = usePathname();

  const navLinks = [
    { href: "/", label: "Dashboard", icon: LayoutDashboard },
    { href: "/discovery", label: "Discovery", icon: Radio },
    { href: "/transfer", label: "Transfer", icon: Send },
    { href: "/distribution", label: "1-to-Many", icon: Share2 },
    { href: "/recovery", label: "Recovery", icon: RefreshCw },
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <div className="flex items-center space-x-3">
          <Link href="/" className="flex items-center space-x-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm">
              <Share2 className="h-5 w-5" />
            </div>
            <div>
              <span className="text-lg font-bold tracking-tight text-slate-900">OmniDrop</span>
              <span className="hidden text-xs text-indigo-600 font-semibold sm:inline ml-1.5 px-1.5 py-0.5 rounded bg-indigo-50 border border-indigo-200">Offline P2P</span>
            </div>
          </Link>
        </div>

        {/* Desktop Navigation */}
        <nav className="hidden md:flex items-center space-x-1">
          {navLinks.map((link) => {
            const Icon = link.icon;
            const isActive = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`flex items-center space-x-2 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-indigo-50 text-indigo-700 font-semibold"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`}
              >
                <Icon className={`h-4 w-4 ${isActive ? "text-indigo-600" : "text-slate-400"}`} />
                <span>{link.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Connection Status Indicator */}
        <div className="flex items-center space-x-2">
          <Badge
            variant={isWsConnected ? "success" : "destructive"}
            className="flex items-center space-x-1.5 py-1 px-2.5"
          >
            <Wifi className="h-3 w-3" />
            <span>{isWsConnected ? "Socket Live" : "Agent Offline"}</span>
          </Badge>
        </div>
      </div>
    </header>
  );
}
