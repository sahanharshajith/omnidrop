package com.omnidrop.discovery;

import com.omnidrop.model.PeerDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MEMBER 1: PeerRegistry insert/refresh/update rules and heartbeat expiry, using a fake clock.
 */
class PeerRegistryTest {

    private AtomicLong now;
    private PeerRegistry registry;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000L);
        registry = new PeerRegistry(now::get);
    }

    private static PeerDevice peer(String id, String ip) {
        return new PeerDevice(id, "Laptop-" + id, ip, 9090, 8080, 0L);
    }

    @Test
    void firstBeaconIsNew() {
        assertThat(registry.upsert(peer("a", "192.168.1.10"))).isEqualTo(PeerRegistry.Result.NEW);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void sameBeaconAgainIsRefreshedAndUpdatesLastSeen() {
        registry.upsert(peer("a", "192.168.1.10"));
        now.set(4_000L);

        assertThat(registry.upsert(peer("a", "192.168.1.10"))).isEqualTo(PeerRegistry.Result.REFRESHED);
        assertThat(registry.snapshot().get(0).getLastSeenTimestamp()).isEqualTo(4_000L);
    }

    @Test
    void changedIpIsUpdated() {
        registry.upsert(peer("a", "192.168.1.10"));
        assertThat(registry.upsert(peer("a", "192.168.1.22"))).isEqualTo(PeerRegistry.Result.UPDATED);
        assertThat(registry.snapshot().get(0).getIpAddress()).isEqualTo("192.168.1.22");
    }

    @Test
    void changedNameOrPortIsUpdated() {
        registry.upsert(peer("a", "192.168.1.10"));
        assertThat(registry.upsert(new PeerDevice("a", "Renamed", "192.168.1.10", 9090, 8080, 0L)))
                .isEqualTo(PeerRegistry.Result.UPDATED);
        assertThat(registry.upsert(new PeerDevice("a", "Renamed", "192.168.1.10", 9191, 8080, 0L)))
                .isEqualTo(PeerRegistry.Result.UPDATED);
    }

    @Test
    void evictStaleRemovesOnlySilentPeers() {
        registry.upsert(peer("old", "192.168.1.10"));   // seen at t=1000
        now.set(10_000L);
        registry.upsert(peer("fresh", "192.168.1.11")); // seen at t=10000

        now.set(13_001L); // "old" silent for 12001 ms, "fresh" for 3001 ms
        List<PeerDevice> evicted = registry.evictStale(12_000L);

        assertThat(evicted).extracting(PeerDevice::getDeviceId).containsExactly("old");
        assertThat(registry.snapshot()).extracting(PeerDevice::getDeviceId).containsExactly("fresh");
    }

    @Test
    void peerExactlyAtTimeoutIsKept() {
        registry.upsert(peer("a", "192.168.1.10"));
        now.set(1_000L + 12_000L);
        assertThat(registry.evictStale(12_000L)).isEmpty();
    }

    @Test
    void removeReturnsPeerOnce() {
        registry.upsert(peer("a", "192.168.1.10"));
        assertThat(registry.remove("a")).isPresent();
        assertThat(registry.remove("a")).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void snapshotIsACopy() {
        registry.upsert(peer("a", "192.168.1.10"));
        registry.snapshot().clear();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void concurrentUpsertsAreSafe() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int thread = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    registry.upsert(peer("peer-" + (i % 50), "10.0.0." + thread));
                    if (i % 100 == 0) registry.evictStale(60_000L);
                }
            }));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        assertThat(registry.size()).isEqualTo(50);
    }
}
