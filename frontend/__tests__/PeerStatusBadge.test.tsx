import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PeerStatusBadge } from "@/components/PeerStatusBadge";

describe("PeerStatusBadge", () => {
  it("shows Online for a fresh heartbeat", () => {
    render(<PeerStatusBadge ageMs={1000} />);
    expect(screen.getByText("Online")).toBeInTheDocument();
  });

  it("shows Weak signal after a few missed beacons", () => {
    render(<PeerStatusBadge ageMs={8000} />);
    expect(screen.getByText("Weak signal")).toBeInTheDocument();
  });

  it("shows Offline after the timeout", () => {
    render(<PeerStatusBadge ageMs={13000} />);
    const badge = screen.getByText("Offline");
    expect(badge.closest("[data-status]")).toHaveAttribute("data-status", "offline");
  });
});
