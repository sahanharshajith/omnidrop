package com.omnidrop.distribution;

import com.omnidrop.model.PeerDevice;
import com.omnidrop.model.TransferProgress;
import com.omnidrop.model.TransferState;
import com.omnidrop.sender.TcpFileSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;

/**
 * MEMBER 4: Runnable/Callable worker executing an isolated TCP transfer to a single peer in a thread pool.
 */
public class ConcurrentTransferWorker implements Callable<TransferProgress> {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentTransferWorker.class);

    private final PeerDevice targetPeer;
    private final File file;
    private final TcpFileSenderService senderService;

    private final LongConsumer acknowledged;

    public ConcurrentTransferWorker(PeerDevice targetPeer, File file, TcpFileSenderService senderService,
                                    LongConsumer acknowledged) {
        this.acknowledged = acknowledged;
        this.targetPeer = targetPeer;
        this.file = file;
        this.senderService = senderService;
    }

    @Override
    public TransferProgress call() {
        log.info("Worker thread '{}' beginning concurrent transfer to {}",
                Thread.currentThread().getName(), targetPeer.getDeviceName());

        try {
            return senderService.sendFile(targetPeer, file, acknowledged);
        } catch (Exception e) {
            log.error("Concurrent worker failed for peer {}: {}", targetPeer.getDeviceName(), e.getMessage());
            TransferProgress failed = new TransferProgress();
            failed.setPeerName(targetPeer.getDeviceName());
            failed.setPeerIp(targetPeer.getIpAddress());
            failed.setState(TransferState.FAILED);
            failed.setErrorMessage(e.getMessage());
            return failed;
        }
    }
}
