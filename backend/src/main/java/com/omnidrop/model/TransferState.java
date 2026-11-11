package com.omnidrop.model;

public enum TransferState {
    PENDING,
    CONNECTING,
    TRANSFERRING,
    PAUSED,
    INTERRUPTED,
    COMPLETED,
    FAILED,
    CANCELLED
}
