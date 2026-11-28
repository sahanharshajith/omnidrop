export interface PeerDevice {
  deviceId: string;
  deviceName: string;
  ipAddress: string;
  tcpPort: number;
  httpPort: number;
  lastSeenTimestamp: number;
  active: boolean;
}

export type TransferState =
  | 'PENDING'
  | 'CONNECTING'
  | 'TRANSFERRING'
  | 'PAUSED'
  | 'INTERRUPTED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface TransferProgress {
  transferId: string;
  role: 'SENDER' | 'RECEIVER';
  peerName?: string;
  peerIp?: string;
  fileName: string;
  bytesTransferred: number;
  totalBytes: number;
  percentage: number;
  currentChunk: number;
  totalChunks: number;
  speedBytesPerSec: number;
  speedFormatted?: string;
  estimatedRemainingSeconds: number;
  state: TransferState;
  errorMessage?: string;
}

export interface Checkpoint {
  transferId: string;
  fileName: string;
  fileSize: number;
  lastOffset: number;
  lastChunkIndex: number;
  expectedSha256: string;
  partFilePath: string;
  lastUpdatedTimestamp: number;
}

export interface NodeHealth {
  status: string;
  nodeName: string;
  nodeId: string;
  tcpPort: number;
  udpPort: number;
  ipAddresses: string[];
  timestamp: number;
}

export interface WebSocketEvent<T = unknown> {
  type: string;
  timestamp: number;
  payload: T;
}

export interface DistributionProgress {
  distributionId: string;
  fileName: string;
  fileSize: number;
  totalReceivers: number;
  completedReceivers: number;
  failedReceivers: number;
  bytesTransferred: number;
  status: 'IN_PROGRESS' | 'SUCCESS' | 'FAILED' | 'PARTIAL_SUCCESS';
  receivers: {
    deviceId: string;
    deviceName: string;
    bytesTransferred: number;
    state: TransferState;
    errorMessage?: string;
  }[];
}
