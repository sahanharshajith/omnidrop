import { NodeHealth, PeerDevice, Checkpoint } from "@/types";

const getBaseUrl = () => {
  if (typeof window !== "undefined") {
    // When in browser, connect to host on port 8080 if not overridden
    const host = window.location.hostname;
    return process.env.NEXT_PUBLIC_BACKEND_URL || `http://${host}:8080`;
  }
  return process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";
};

export const api = {
  async getHealth(): Promise<NodeHealth> {
    const res = await fetch(`${getBaseUrl()}/api/v1/health`);
    if (!res.ok) throw new Error("Backend offline");
    return res.json();
  },

  async getDevices(): Promise<PeerDevice[]> {
    const res = await fetch(`${getBaseUrl()}/api/v1/devices`);
    if (!res.ok) throw new Error("Failed to fetch devices");
    return res.json();
  },

  async triggerDiscovery(): Promise<void> {
    await fetch(`${getBaseUrl()}/api/v1/devices/broadcast`, { method: "POST" });
  },

  async getCheckpoints(): Promise<Checkpoint[]> {
    const res = await fetch(`${getBaseUrl()}/api/v1/recovery/checkpoints`);
    if (!res.ok) throw new Error("Failed to fetch recovery checkpoints");
    return res.json();
  },

  async sendFile(file: File, targetIp: string, targetPort: number, targetName: string) {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("targetIp", targetIp);
    formData.append("targetPort", targetPort.toString());
    formData.append("targetName", targetName);

    const res = await fetch(`${getBaseUrl()}/api/v1/transfer/send`, {
      method: "POST",
      body: formData,
    });
    if (!res.ok) throw new Error("Transfer initialization failed");
    return res.json();
  },

  async startDistribution(file: File, targets: PeerDevice[]) {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("targetsJson", JSON.stringify(targets));

    const res = await fetch(`${getBaseUrl()}/api/v1/distribution/start`, {
      method: "POST",
      body: formData,
    });
    if (!res.ok) throw new Error("Distribution initialization failed");
    return res.json();
  },
};
