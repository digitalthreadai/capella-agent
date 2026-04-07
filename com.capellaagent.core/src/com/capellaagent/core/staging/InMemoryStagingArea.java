package com.capellaagent.core.staging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link StagingArea}.
 * <p>
 * TTL is set to 10 minutes. Expired entries are evicted lazily on access.
 */
public final class InMemoryStagingArea implements StagingArea {

    private static final long DEFAULT_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private static final InMemoryStagingArea INSTANCE = new InMemoryStagingArea();

    public static InMemoryStagingArea getInstance() {
        return INSTANCE;
    }

    /** Key: "sessionId:diffId" → PendingDiff */
    private final ConcurrentHashMap<String, PendingDiff> store = new ConcurrentHashMap<>();

    private InMemoryStagingArea() {}

    @Override
    public String stage(String sessionId, String projectName, List<ProposedChange> changes) {
        String diffId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

    private String key(String sessionId, String diffId) {
        return sessionId + ":" + diffId;
    }
}
