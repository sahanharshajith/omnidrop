"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Radio, Send, Share2, RefreshCw, LayoutDashboard, Download } from "lucide-react";

export function MobileNav() {
  const pathname = usePathname();

  const navLinks = [
    { href: "/", label: "Home", icon: LayoutDashboard },
    { href: "/discovery", label: "Discovery", icon: Radio },
    { href: "/transfer", label: "Send", icon: Send },
    { href: "/received", label: "Files", icon: Download },
    { href: "/distribution", label: "Broadcast", icon: Share2 },
    { href: "/recovery", label: "Resume", icon: RefreshCw },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 border-t border-slate-200 bg-white/95 backdrop-blur md:hidden">
      <div className="flex h-16 items-center justify-around px-2">
        {navLinks.map((link) => {
          const Icon = link.icon;
          const isActive = pathname === link.href;
          return (
            <Link
              key={link.href}
              href={link.href}
              className={`flex flex-col items-center justify-center space-y-1 py-1 px-2 text-xs font-medium transition-colors ${
                isActive ? "text-indigo-600 font-bold" : "text-slate-500 hover:text-slate-900"
              }`}
            >
              <Icon className={`h-5 w-5 ${isActive ? "text-indigo-600" : "text-slate-400"}`} />
              <span>{link.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}