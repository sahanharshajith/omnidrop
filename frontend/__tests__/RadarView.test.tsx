import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RadarView } from "@/components/RadarView";
import type { LivePeer } from "@/types";

const NOW = 100_000;
const livePeer = (id: string, ageMs: number): LivePeer => ({
  deviceId: id,
  deviceName: `Device-${id}`,
  ipAddress: `192.168.1.${id.length + 10}`,
  tcpPort: 9090,
  httpPort: 8080,
  lastSeenTimestamp: 1,
  active: true,
  seenAt: NOW - ageMs,
});

describe("RadarView", () => {
  it("draws one dot per peer", () => {
    render(<RadarView peers={[livePeer("a", 0), livePeer("bb", 8000), livePeer("ccc", 20000)]} now={NOW} />);
    expect(screen.getAllByTestId("radar-peer")).toHaveLength(3);
  });

  it("labels each dot with name, IP and status for screen readers", () => {
    render(<RadarView peers={[livePeer("a", 8000)]} now={NOW} />);
    expect(screen.getByRole("button", { name: /Device-a, 192\.168\.1\.11, weak/ })).toBeInTheDocument();
  });

  it("calls onSelect when a dot is clicked or activated with the keyboard", () => {
    const onSelect = vi.fn();
    render(<RadarView peers={[livePeer("a", 0)]} now={NOW} onSelect={onSelect} />);
    const dot = screen.getByTestId("radar-peer");

    fireEvent.click(dot);
    fireEvent.keyDown(dot, { key: "Enter" });

    expect(onSelect).toHaveBeenCalledTimes(2);
    expect(onSelect.mock.calls[0][0].deviceId).toBe("a");
  });

  it("marks the selected peer", () => {
    render(<RadarView peers={[livePeer("a", 0), livePeer("b", 0)]} now={NOW} selectedId="b" />);
    const pressed = screen.getAllByTestId("radar-peer").filter((g) => g.getAttribute("aria-pressed") === "true");
    expect(pressed).toHaveLength(1);
  });

  it("describes the device count", () => {
    render(<RadarView peers={[]} now={NOW} />);
    expect(screen.getByRole("img", { name: /0 nearby devices/ })).toBeInTheDocument();
  });
});
