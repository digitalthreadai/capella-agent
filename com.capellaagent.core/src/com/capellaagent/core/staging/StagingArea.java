package com.capellaagent.core.staging;

import java.util.List;
import java.util.Optional;

/**
 * Transient staging area for architecture proposals.
 * <p>
 * The generative architecture tools (Phase 3) use this to store a validated
 * diff between the "propose" step and the "apply" step. The staging area:
 * <ul>
 *   <li>Never mutates the EMF model — proposals are stored as plain data</li>
 *   <li>Keys proposals by {@code (sessionId, diffId)} so concurrent sessions
 *       are isolated</li>
 *   <li>Enforces a TTL so stale proposals expire automatically</li>
 *   <li>Stores the active project name at staging time to prevent cross-project
 *       application</li>
 * </ul>
 */
public interface StagingArea {

    /**
     * Stages a validated list of proposed changes.
     *
     * @param sessionId   the conversation session ID
     * @param projectName the active project name at proposal time
     * @param changes     the validated proposed changes
     * @return the {@code diffId} token the user can reference in the chat
     */
    String stage(String sessionId, String projectName, List<ProposedChange> changes);

    /**
     * Retrieves a staged proposal by its diffId.
     *
     * @param sessionId the conversation session ID (must match staging-time value)
     * @param diffId    the token returned by {@link #stage}
     * @return the pending changes, or empty if not found or expired
     */
    Optional<PendingDiff> get(String sessionId, String diffId);

    /**
     * Explicitly discards a staged proposal before its TTL expires.
     *
     * @param sessionId the conversation session ID
     * @param diffId    the token to discard
     */
    void discard(String sessionId, String diffId);
}
