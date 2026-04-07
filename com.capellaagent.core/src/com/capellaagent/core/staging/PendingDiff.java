package com.capellaagent.core.staging;

import java.util.List;

/**
 * A staged architecture proposal with its metadata.
 */
public record PendingDiff(
        String diffId,
        String sessionId,
        String projectName,
        List<ProposedChange> changes,
        long stagedAtEpochMs,
        long ttlMs
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > stagedAtEpochMs + ttlMs;
    }
}
