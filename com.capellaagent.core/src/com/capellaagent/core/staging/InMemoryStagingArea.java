package com.capellaagent.core.staging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Thread-safe in-memory implementation of {@link StagingArea}.
 * <p>
 * TTL is 10 minutes; expired entries are evicted lazily on access AND swept
 * every 60 s by a background daemon thread (sprint I7 — prevents slow memory
 * growth when a session stages but never applies).
 * <p>
 * <b>Capacity:</b> store is capped at {@link #MAX_ENTRIES} to defend against
 * an LLM that spams stage calls. LRU eviction is approximated by timestamp
 * when the cap is hit.
 * <p>
 * <b>diffId entropy:</b> full 128-bit UUID (sprint G1 — the original 8-hex
 * substring was 32-bit and vulnerable to collision/guess attacks).
 */
public final class InMemoryStagingArea implements StagingArea {

    private static final Logger LOG = Logger.getLogger(InMemoryStagingArea.class.getName());

    private static final long DEFAULT_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    /** Hard cap on total staged entries across all sessions (I7). */
    public static final int MAX_ENTRIES = 100;
    /** Sweep interval for the background eviction task. */
    public static final long SWEEP_INTERVAL_SECONDS = 60L;

    private static final InMemoryStagingArea INSTANCE = new InMemoryStagingArea();

    public static InMemoryStagingArea getInstance() {
        return INSTANCE;
    }

    /** Key: "sessionId:diffId" → PendingDiff */
    private final ConcurrentHashMap<String, PendingDiff> store = new ConcurrentHashMap<>();

    private final ScheduledExecutorService sweeper;

    private InMemoryStagingArea() {
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "capella-agent-staging-sweeper");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleAtFixedRate(this::sweepExpired,
            SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Shuts down the sweeper thread (call from bundle {@code Activator.stop}). */
    public void shutdown() {
        sweeper.shutdownNow();
        store.clear();
    }

    private void sweepExpired() {
        try {
            store.entrySet().removeIf(e -> e.getValue().isExpired());
        } catch (RuntimeException ex) {
            LOG.fine("Staging sweep failed: " + ex.getMessage());
        }
    }

    @Override
    public String stage(String sessionId, String projectName, List<ProposedChange> changes) {
        // I7: enforce capacity. Evict the oldest entry if we're at the cap.
        if (store.size() >= MAX_ENTRIES) {
            store.entrySet().stream()
                .min((a, b) -> Long.compare(
                    a.getValue().stagedAtEpochMs(), b.getValue().stagedAtEpochMs()))
                .ifPresent(e -> store.remove(e.getKey()));
        }

        // G1: full 128-bit UUID — no substring truncation.
        String diffId = UUID.randomUUID().toString().toUpperCase();
        PendingDiff diff = new PendingDiff(diffId, sessionId, projectName,
                List.copyOf(changes), System.currentTimeMillis(), DEFAULT_TTL_MS);
        store.put(key(sessionId, diffId), diff);
        return diffId;
    }

    @Override
    public Optional<PendingDiff> get(String sessionId, String diffId) {
        PendingDiff diff = store.get(key(sessionId, diffId));
        if (diff == null) return Optional.empty();
        if (diff.isExpired()) {
            store.remove(key(sessionId, diffId));
            return Optional.empty();
        }
        return Optional.of(diff);
    }

    @Override
    public void discard(String sessionId, String diffId) {
        store.remove(key(sessionId, diffId));
    }

    /** Test-only: current size of the staging store. */
    public int size() {
        return store.size();
    }

    private String key(String sessionId, String diffId) {
        return sessionId + ":" + diffId;
    }
}
