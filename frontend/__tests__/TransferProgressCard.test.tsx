import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TransferProgressCard } from "@/components/TransferProgressCard";
import type { TransferProgress } from "@/types";

/**
 * Tests for the TransferProgressCard component.
 *
 * Covers:
 *  - Data rendering (filename, percentage, speed)
 *  - Cancel button visibility & dispatch (only when TRANSFERRING/CONNECTING for SENDER)
 *  - Resume button visibility & dispatch (only when INTERRUPTED for SENDER)
 *  - Receiver role sees neither action button
 */

// vi.hoisted runs BEFORE vi.mock factories, so mockSendCommand is defined
// when the vi.mock factory below captures it. Needed because vi.mock calls
// are hoisted to the top of the module.
const { mockSendCommand } = vi.hoisted(() => ({
  mockSendCommand: vi.fn(),
}));

vi.mock("@/lib/websocket", () => ({
  transferWs: {
    sendCommand: mockSendCommand,
    onEvent: vi.fn(() => () => {}),
    connect: vi.fn(),
    isOpen: vi.fn(() => true),
    close: vi.fn(),
  },
}));

// ---- Fixture helpers ----

function makeProgress(overrides: Partial<TransferProgress> = {}): TransferProgress {
  return {
    transferId: "t-1",
    role: "SENDER",
    peerName: "TestPeer",
    peerIp: "192.168.1.5",
    fileName: "sample.bin",
    bytesTransferred: 500 * 1024,
    totalBytes: 1024 * 1024,
    percentage: 48.8,
    currentChunk: 5,
    totalChunks: 10,
    speedBytesPerSec: 2 * 1024 * 1024,
    speedFormatted: "2.00 MB/s",
    estimatedRemainingSeconds: 3,
    state: "TRANSFERRING",
    ...overrides,
  };
}

describe("TransferProgressCard", () => {
  beforeEach(() => {
    mockSendCommand.mockReset();
  });

  // ---- Rendering ----

  it("renders the file name, percentage, and speed", () => {
    render(<TransferProgressCard progress={makeProgress()} />);

    expect(screen.getByText("sample.bin")).toBeInTheDocument();
    expect(screen.getByText("48.8%")).toBeInTheDocument();
    expect(screen.getByText("2.00 MB/s")).toBeInTheDocument();
  });

  it("shows 'To: <peerName>' for SENDER role", () => {
    render(<TransferProgressCard progress={makeProgress({ role: "SENDER" })} />);
    expect(screen.getByText(/To: TestPeer/)).toBeInTheDocument();
  });

  it("shows 'From: <peerIp>' for RECEIVER role", () => {
    render(
      <TransferProgressCard
        progress={makeProgress({ role: "RECEIVER", peerName: undefined, peerIp: "10.0.0.9" })}
      />
    );
    expect(screen.getByText(/From: 10\.0\.0\.9/)).toBeInTheDocument();
  });

  // ---- Cancel button ----

  it("shows Cancel while TRANSFERRING (SENDER)", () => {
    render(<TransferProgressCard progress={makeProgress({ state: "TRANSFERRING" })} />);
    expect(screen.getByRole("button", { name: /cancel transfer/i })).toBeInTheDocument();
  });

  it("shows Cancel while CONNECTING (SENDER)", () => {
    render(<TransferProgressCard progress={makeProgress({ state: "CONNECTING" })} />);
    expect(screen.getByRole("button", { name: /cancel transfer/i })).toBeInTheDocument();
  });

  it("hides Cancel when COMPLETED", () => {
    render(<TransferProgressCard progress={makeProgress({ state: "COMPLETED" })} />);
    expect(screen.queryByRole("button", { name: /cancel transfer/i })).not.toBeInTheDocument();
  });

  it("clicking Cancel sends CANCEL_TRANSFER with the transferId", async () => {
    const user = userEvent.setup();
    render(<TransferProgressCard progress={makeProgress({ transferId: "tx-42" })} />);

    await user.click(screen.getByRole("button", { name: /cancel transfer/i }));

    expect(mockSendCommand).toHaveBeenCalledTimes(1);
    expect(mockSendCommand).toHaveBeenCalledWith("CANCEL_TRANSFER", { transferId: "tx-42" });
  });

  // ---- Resume button ----

  it("shows Resume when INTERRUPTED (SENDER)", () => {
    render(<TransferProgressCard progress={makeProgress({ state: "INTERRUPTED" })} />);
    expect(screen.getByRole("button", { name: /resume transfer/i })).toBeInTheDocument();
  });

  it("hides Resume when TRANSFERRING", () => {
    render(<TransferProgressCard progress={makeProgress({ state: "TRANSFERRING" })} />);
    expect(screen.queryByRole("button", { name: /resume transfer/i })).not.toBeInTheDocument();
  });

  it("clicking Resume sends RESUME_TRANSFER with the transferId", async () => {
    const user = userEvent.setup();
    render(
      <TransferProgressCard progress={makeProgress({ state: "INTERRUPTED", transferId: "tx-99" })} />
    );

    await user.click(screen.getByRole("button", { name: /resume transfer/i }));

    expect(mockSendCommand).toHaveBeenCalledTimes(1);
    expect(mockSendCommand).toHaveBeenCalledWith("RESUME_TRANSFER", { transferId: "tx-99" });
  });

  // ---- Receiver role: no actions ----

  it("RECEIVER never sees Cancel even when TRANSFERRING", () => {
    render(
      <TransferProgressCard progress={makeProgress({ role: "RECEIVER", state: "TRANSFERRING" })} />
    );
    expect(screen.queryByRole("button", { name: /cancel transfer/i })).not.toBeInTheDocument();
  });

  it("RECEIVER never sees Resume even when INTERRUPTED", () => {
    render(
      <TransferProgressCard progress={makeProgress({ role: "RECEIVER", state: "INTERRUPTED" })} />
    );
    expect(screen.queryByRole("button", { name: /resume transfer/i })).not.toBeInTheDocument();
  });

  // ---- Error message ----

  it("renders the errorMessage when present", () => {
    render(
      <TransferProgressCard
        progress={makeProgress({ state: "FAILED", errorMessage: "Connection reset" })}
      />
    );
    expect(screen.getByText("Connection reset")).toBeInTheDocument();
  });
});