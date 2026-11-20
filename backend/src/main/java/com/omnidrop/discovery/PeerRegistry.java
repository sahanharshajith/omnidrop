package com.omnidrop.discovery;

import com.omnidrop.model.PeerDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

//thread safe in memory table of peers found by UDP discovery
public class PeerRegistry {

    //results when a beacon comes in
    public enum Result { NEW, UPDATED, REFRESHED }

    //3 threads use this
    //UDP listener, cleanup scheduler, HTTP requests
    private final Map<String, PeerDevice> peers = new ConcurrentHashMap<>();

    //system clock in code
    private final LongSupplier clock;

    //normal system clock
    public PeerRegistry() {
        this(System::currentTimeMillis);
    }

    //for tests
    public PeerRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    //adding a new peer or update details of an existing peer
    public Result upsert(PeerDevice incoming) {

        //sets the last time seen
        incoming.setLastSeenTimestamp(clock.getAsLong());

        //returns the old value by put()
        PeerDevice old = peers.put(incoming.getDeviceId(), incoming);

        // sets as new
        if (old == null) {
            return Result.NEW;
        }

        //checks whether the IP,device name or the TCP is changed
        boolean changed = !Objects.equals(old.getIpAddress(), incoming.getIpAddress())
                || !Objects.equals(old.getDeviceName(), incoming.getDeviceName())
                || old.getTcpPort() != incoming.getTcpPort();

        //if changed returns as updated if not just a refresh
        return changed ? Result.UPDATED : Result.REFRESHED;
    }

    //to remove one peer (used for LEAVE beacon later)
    public Optional<PeerDevice> remove(String device) {
        return Optional.ofNullable(peers.remove(device));
    }

    public List<PeerDevice> evictStale(long timeoutMs) {
        long now = clock.getAsLong();

        //new arraylist for evicted devices
        List<PeerDevice> evicted = new ArrayList<>();

        peers.values().removeIf(peer -> {
            //stale is how much time has passed since the device is last seen
            boolean stale = now - peer.getLastSeenTimestamp() > timeoutMs;

            //add to the evicted list to send to the UI
            if (stale) {
                evicted.add(peer);
            }
            return stale;
        });
        return evicted;
    }

    //get a copy of the map
    public List<PeerDevice> snapshot() {
        return new ArrayList<>(peers.values());
    }

    //currently existing peers
    public int size() {
        return peers.size();
    }

}
