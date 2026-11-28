import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DeviceDiscoveryCard } from "@/components/DeviceDiscoveryCard";
import type { LivePeer } from "@/types";

const NOW = 50_000;
const peer: LivePeer = {
  deviceId: "bob",
  deviceName: "Bob-MacBook",
  ipAddress: "192.168.1.105",
  tcpPort: 9090,
  httpPort: 8080,
  lastSeenTimestamp: 1,
  active: true,
  seenAt: NOW - 4000,
};

describe("DeviceDiscoveryCard", () => {
  it("shows name, address, status and last seen", () => {
    render(<DeviceDiscoveryCard peer={peer} now={NOW} />);
    expect(screen.getByText("Bob-MacBook")).toBeInTheDocument();
    expect(screen.getByText("192.168.1.105:9090")).toBeInTheDocument();
    expect(screen.getByText("Online")).toBeInTheDocument();
    expect(screen.getByText("Last seen 4s ago")).toBeInTheDocument();
  });

  it("links Send file to the transfer page for this peer", () => {
    render(<DeviceDiscoveryCard peer={peer} now={NOW} />);
    const link = screen.getByRole("link");
    expect(link.getAttribute("href")).toContain("/transfer?peerIp=192.168.1.105&peerPort=9090");
  });

  it("selects the peer on click", () => {
    const onSelect = vi.fn();
    render(<DeviceDiscoveryCard peer={peer} now={NOW} onSelect={onSelect} />);
    fireEvent.click(screen.getByText("Bob-MacBook"));
    expect(onSelect).toHaveBeenCalledWith(peer);
  });
});
