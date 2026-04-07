package com.capellaagent.core.session.persistence;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.capellaagent.core.session.ConversationSession;

/**
 * Storage abstraction for {@link ConversationSession} persistence.
 * <p>
 * Implementations are responsible for serializing sessions to a durable
 * medium (JSON files on disk by default) along with their {@link SessionState}
 * recovery flag and a timestamp. Kept intentionally small (3 methods) so that
 * alternative backends — in-memory for tests, encrypted, or remote — can be
 * dropped in without churning callers.
 */
public interface ConversationStore {

    /**
     * Persists the given session under the supplied project name with the
     * specified recovery state.
     *
     * @param session     the session to save; must not be null
     * @param state       the recovery state to record alongside it
     * @param projectName the Capella project name acting as a namespace
     * @throws IOException if the underlying storage cannot be written
     */
    void save(ConversationSession session, SessionState state, String projectName) throws IOException;

    /**
     * Loads the most recently saved session for the given project, if any.
     *
     * @param projectName the Capella project name acting as a namespace
     * @return an {@link Optional} containing the loaded session, its state,
     *         and its save timestamp; empty if nothing has been saved for
     *         this project (or the latest file is at an incompatible schema)
     * @throws IOException if the underlying storage cannot be read
     */
    Optional<LoadedSession> loadLatestForProject(String projectName) throws IOException;

    /**
     * Lists every persisted session ID for the given project, in unspecified
     * order.
     *
     * @param projectName the Capella project name acting as a namespace
     * @return a list of session IDs; empty if the project has no sessions
     * @throws IOException if the underlying storage cannot be read
     */
    List<String> listSessionIds(String projectName) throws IOException;

    /**
     * The result of a successful {@link #loadLatestForProject(String)} call.
     *
     * @param session the rebuilt conversation session
     * @param state   the recovery state recorded when the session was saved
     * @param savedAt the wall-clock timestamp the session was saved
     */
    record LoadedSession(ConversationSession session, SessionState state, Instant savedAt) {}
}
