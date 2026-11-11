package com.omnidrop.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.model.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists and loads transfer checkpoints from disk (.chk files).
 */
@Component
public class CheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(CheckpointStore.class);

    private final OmniDropProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Checkpoint> inMemoryCache = new ConcurrentHashMap<>();

    public CheckpointStore(OmniDropProperties properties) {
        this.properties = properties;
        initDir();
        loadAll();
    }

    private void initDir() {
        File dir = new File(properties.getStorage().getCheckpointsDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public synchronized void save(Checkpoint checkpoint) {
        inMemoryCache.put(checkpoint.getTransferId(), checkpoint);
        File file = new File(properties.getStorage().getCheckpointsDir(), checkpoint.getTransferId() + ".chk");
        try {
            objectMapper.writeValue(file, checkpoint);
        } catch (IOException e) {
            log.error("Failed to persist checkpoint for transfer {}: {}", checkpoint.getTransferId(), e.getMessage());
        }
    }

    public Checkpoint get(String transferId) {
        return inMemoryCache.get(transferId);
    }

    public List<Checkpoint> getAll() {
        return new ArrayList<>(inMemoryCache.values());
    }

    public synchronized void delete(String transferId) {
        inMemoryCache.remove(transferId);
        File file = new File(properties.getStorage().getCheckpointsDir(), transferId + ".chk");
        if (file.exists()) {
            file.delete();
        }
    }

    private void loadAll() {
        File dir = new File(properties.getStorage().getCheckpointsDir());
        File[] files = dir.listFiles((d, name) -> name.endsWith(".chk"));
        if (files != null) {
            for (File file : files) {
                try {
                    Checkpoint cp = objectMapper.readValue(file, Checkpoint.class);
                    inMemoryCache.put(cp.getTransferId(), cp);
                } catch (Exception e) {
                    log.warn("Corrupted checkpoint ignored: {}", file.getName());
                }
            }
        }
    }
}
