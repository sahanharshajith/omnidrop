import { describe, it, expect } from "vitest";
import { formatAge, getPeerStatus, radarAngles } from "@/lib/peerStatus";

describe("getPeerStatus", () => {
  it("is online within two beacon intervals", () => {
    expect(getPeerStatus(0)).toBe("online");
    expect(getPeerStatus(5999)).toBe("online");
  });

  it("is weak after missed beacons but before the timeout", () => {
    expect(getPeerStatus(6000)).toBe("weak");
    expect(getPeerStatus(11999)).toBe("weak");
  });

  it("is offline at the timeout", () => {
    expect(getPeerStatus(12000)).toBe("offline");
  });

  it("treats negative ages (clock skew) as fresh", () => {
    expect(getPeerStatus(-5000)).toBe("online");
  });

  it("respects custom interval and timeout", () => {
    expect(getPeerStatus(2500, 1000, 4000)).toBe("weak");
    expect(getPeerStatus(4000, 1000, 4000)).toBe("offline");
  });
});

describe("formatAge", () => {
  it("formats seconds and minutes", () => {
    expect(formatAge(500)).toBe("just now");
    expect(formatAge(5000)).toBe("5s ago");
    expect(formatAge(125000)).toBe("2m ago");
  });
});

describe("radarAngles", () => {
  it("spreads devices evenly and is independent of input order", () => {
    const a = radarAngles(["c", "a", "b"]);
    const b = radarAngles(["a", "b", "c"]);
    expect(a).toEqual(b);
    expect(a.get("a")).toBeCloseTo(Math.PI / 2);
    expect(a.get("b")! - a.get("c")!).toBeCloseTo((2 * Math.PI) / 3);
  });

  it("handles an empty list", () => {
    expect(radarAngles([]).size).toBe(0);
  });
});
