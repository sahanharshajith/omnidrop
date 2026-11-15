"use client";


import "./globals.css";
import { Navbar } from "@/components/Navbar";
import { MobileNav } from "@/components/MobileNav";
import { IncomingTransferModal } from "@/components/IncomingTransferModal";
import { useWebSocket } from "@/hooks/useWebSocket";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isConnected } = useWebSocket();

  return (
    <html lang="en">
      <head>
        <title>OmniDrop – Offline P2P File Sharing</title>
        <meta name="description" content="Mobile-responsive offline P2P file sharing with Java TCP, UDP, and WebSockets" />
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
      </head>
      <body className="min-h-screen bg-slate-50 text-slate-900 pb-20 md:pb-8">
        <Navbar isWsConnected={isConnected} />
        <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          {children}
        </main>
        <MobileNav />
        <IncomingTransferModal />
      </body>
    </html>
  );
}