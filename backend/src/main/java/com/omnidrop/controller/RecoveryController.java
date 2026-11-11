package com.omnidrop.controller;

import com.omnidrop.model.Checkpoint;
import com.omnidrop.recovery.TransferRecoveryManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recovery")
public class RecoveryController {

    private final TransferRecoveryManager recoveryManager;

    public RecoveryController(TransferRecoveryManager recoveryManager) {
        this.recoveryManager = recoveryManager;
    }

    @GetMapping("/checkpoints")
    public ResponseEntity<List<Checkpoint>> getInterruptedTransfers() {
        return ResponseEntity.ok(recoveryManager.getInterruptedTransfers());
    }

    @GetMapping("/checkpoints/{transferId}")
    public ResponseEntity<Checkpoint> getCheckpointById(@PathVariable String transferId) {
        Checkpoint cp = recoveryManager.getCheckpoint(transferId);
        if (cp == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(cp);
    }
}
